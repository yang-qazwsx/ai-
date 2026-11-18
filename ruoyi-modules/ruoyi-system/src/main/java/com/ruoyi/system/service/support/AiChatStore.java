package com.ruoyi.system.service.support;

import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.system.domain.AiHotQuestionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * AI 会话的 Redis 存储：热门问题池 + 多轮会话上下文。
 *
 * 从 AiServiceImpl 抽出的原因：
 * 1. 这里全是 Redis 旁路逻辑（缓存、排行、TTL、降级），和主对话编排混在一起时，
 *    读主流程要在 1700 行里来回跳；
 * 2. 热门池有明确的隐私约束（只收通用问题、只收知识库标准答案），集中放置便于审查；
 * 3. Redis 故障时必须“降级但不报错”，把 try/catch 收在这里，调用方保持线性。
 *
 * @author ruoyi
 */
@Component
public class AiChatStore
{
    private static final Logger log = LoggerFactory.getLogger(AiChatStore.class);

    /** 热门问题答案：field = 归一化问题，value = 标准答案 */
    private static final String HOT_QUESTION_DETAIL_KEY = "hot:question:detail";
    /** 热门问题排行：member = 归一化问题，score = 热度 */
    private static final String HOT_QUESTION_RANK_KEY = "hot:question:rank";
    /** 热门问题展示文案：field = 归一化问题，value = 首次提问原文 */
    private static final String HOT_QUESTION_DISPLAY_KEY = "hot:question:display";

    /** 会话历史 key 前缀 */
    private static final String CHAT_HISTORY_PREFIX = "chat:history:";
    /** 每轮会话最多保留的问答轮数（防 Prompt 无限增长） */
    private static final int CHAT_HISTORY_MAX_TURNS = 6;
    /** 会话历史 TTL（小时） */
    private static final long CHAT_HISTORY_TTL_HOURS = 24;

    /**
     * 个性化问题标记：命中则不允许进入全局热门问题池。
     * hot:question:* 是全局 key、不分用户，若把“给孩子挑书，预算120”这类问答写进去，
     * A 用户的家庭情况与预算会被展示给所有人。
     */
    private static final String[] PERSONAL_MARKERS = {
            "我", "俺", "咱", "孩子", "儿子", "女儿", "家里", "家人", "预算", "岁",
            "生日", "推荐", "喜欢", "想要", "打算", "最近"
    };

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 热门问题缓存过期秒数 */
    @Value("${ai.chat.hot-cache-expire-seconds:7200}")
    private long hotCacheExpireSeconds;

    /**
     * 命中热门缓存时返回标准答案，并累加热度；未命中或 Redis 异常返回 null。
     * 调用方拿到 null 就继续走正常链路，不需要关心缓存故障。
     */
    public String findHotAnswer(String question)
    {
        if (!StringUtils.hasText(question))
        {
            return null;
        }
        try
        {
            String key = AiTextUtils.normalizeQuestion(question);
            Object cached = redisTemplate.opsForHash().get(HOT_QUESTION_DETAIL_KEY, key);
            if (cached == null)
            {
                return null;
            }
            try
            {
                redisTemplate.opsForZSet().incrementScore(HOT_QUESTION_RANK_KEY, key, 1);
            }
            catch (Exception e)
            {
                // 热度只是展示排序，失败不影响答案返回
                log.warn("热门问题热度累加失败：{}", e.getMessage());
            }
            return cached.toString();
        }
        catch (Exception e)
        {
            log.warn("读取热门缓存失败，跳过缓存继续处理：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 写入热门问题缓存：归一化 key 存答案，display Hash 存首次提问原文供列表展示，
     * rank ZSet 累加热度。三个 key 统一滑动 TTL，避免展示文案比答案活得更久。
     *
     * 只应传入「知识库直达」的标准答案：大模型回答注入了用户画像，写入全局池会泄露隐私。
     */
    public void cacheHotAnswer(String question, String answer)
    {
        if (!isCacheableForHotPool(question) || !StringUtils.hasText(answer))
        {
            return;
        }
        try
        {
            String key = AiTextUtils.normalizeQuestion(question);
            redisTemplate.opsForHash().put(HOT_QUESTION_DETAIL_KEY, key, answer);
            redisTemplate.opsForHash().putIfAbsent(HOT_QUESTION_DISPLAY_KEY, key, question);
            redisTemplate.opsForZSet().incrementScore(HOT_QUESTION_RANK_KEY, key, 1);
            for (String redisKey : new String[]{HOT_QUESTION_DETAIL_KEY, HOT_QUESTION_DISPLAY_KEY, HOT_QUESTION_RANK_KEY})
            {
                // 滑动 TTL：每次有真实提问就续期；三个 key 同时刷新，
                // 避免出现“有排名没答案”的半过期状态。
                redisTemplate.expire(redisKey, hotCacheExpireSeconds, TimeUnit.SECONDS);
            }
        }
        catch (Exception e)
        {
            log.warn("写入热门缓存失败，忽略：{}", e.getMessage());
        }
    }

    /**
     * 全局热门问题池的准入判断：只收「通用型」问答。
     * 全局池不分用户，个性化问题（含“我/孩子/预算/推荐”等）写入后会把隐私展示给所有人。
     */
    private boolean isCacheableForHotPool(String question)
    {
        if (!StringUtils.hasText(question))
        {
            return false;
        }
        String q = question.trim();
        if (q.length() < 4 || q.length() > 60)
        {
            return false;
        }
        for (String marker : PERSONAL_MARKERS)
        {
            if (q.contains(marker))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * 热门问题 TopN：从热度 ZSet 取排名，再关联答案与展示文案。
     * Redis 故障时返回空列表，避免首页/侧栏整体报错。
     */
    public List<AiHotQuestionDTO> getHotQuestions(int limit)
    {
        List<AiHotQuestionDTO> result = new ArrayList<>();
        if (limit <= 0)
        {
            limit = 10;
        }
        try
        {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(HOT_QUESTION_RANK_KEY, 0, limit - 1);
            if (tuples == null || tuples.isEmpty())
            {
                return result;
            }
            for (ZSetOperations.TypedTuple<String> tuple : tuples)
            {
                String key = tuple.getValue();
                if (!StringUtils.hasText(key))
                {
                    continue;
                }
                Object answerObj = redisTemplate.opsForHash().get(HOT_QUESTION_DETAIL_KEY, key);
                if (answerObj == null)
                {
                    // 有排名没答案：跳过，不能给前端空答案
                    continue;
                }
                Object displayObj = redisTemplate.opsForHash().get(HOT_QUESTION_DISPLAY_KEY, key);
                AiHotQuestionDTO dto = new AiHotQuestionDTO();
                dto.setQuestion(displayObj == null ? key : displayObj.toString());
                dto.setAnswer(answerObj.toString());
                Double score = tuple.getScore();
                dto.setHitCount(score == null ? 0L : score.longValue());
                result.add(dto);
            }
        }
        catch (Exception e)
        {
            log.warn("读取热门问题失败，返回空列表：{}", e.getMessage());
        }
        return result;
    }

    /** 读取会话历史（最新在前），拼成时间正序文本；contextId 为空或 Redis 异常返回空串 */
    public String loadHistory(String contextId)
    {
        if (!StringUtils.hasText(contextId))
        {
            return "";
        }
        try
        {
            List<String> raw = redisTemplate.opsForList()
                    .range(CHAT_HISTORY_PREFIX + contextId, 0, CHAT_HISTORY_MAX_TURNS - 1);
            if (CollectionUtils.isEmpty(raw))
            {
                return "";
            }
            // Redis 中最新在前，展示时反转成时间正序，模型读起来更自然
            StringBuilder sb = new StringBuilder();
            for (int i = raw.size() - 1; i >= 0; i--)
            {
                sb.append(raw.get(i)).append("\n");
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            log.warn("读取会话历史失败：contextId={}，{}", contextId, e.getMessage());
            return "";
        }
    }

    /** 追加一轮问答到会话历史，超出上限裁剪并设置 TTL */
    public void appendHistory(String contextId, String question, String answer)
    {
        if (!StringUtils.hasText(contextId))
        {
            return;
        }
        try
        {
            String q = StringUtils.hasText(question) ? question : "";
            String a = StringUtils.hasText(answer) ? answer : "";
            // 单轮截断，避免长回答撑爆 Prompt
            if (q.length() > 200)
            {
                q = q.substring(0, 200);
            }
            if (a.length() > 400)
            {
                a = a.substring(0, 400);
            }
            String key = CHAT_HISTORY_PREFIX + contextId;
            redisTemplate.opsForList().leftPush(key, "用户：" + q + " 客服：" + a);
            redisTemplate.opsForList().trim(key, 0, CHAT_HISTORY_MAX_TURNS - 1);
            redisTemplate.expire(key, CHAT_HISTORY_TTL_HOURS, TimeUnit.HOURS);
        }
        catch (Exception e)
        {
            log.warn("保存会话历史失败：contextId={}，{}", contextId, e.getMessage());
        }
    }

    /** 清空指定会话的多轮历史；contextId 为空返回 false */
    public boolean clearHistory(String contextId)
    {
        if (!StringUtils.hasText(contextId))
        {
            return false;
        }
        try
        {
            return Boolean.TRUE.equals(redisTemplate.delete(CHAT_HISTORY_PREFIX + contextId));
        }
        catch (Exception e)
        {
            log.warn("清空会话历史失败：contextId={}，{}", contextId, e.getMessage());
            return false;
        }
    }
}
