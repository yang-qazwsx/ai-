package com.ruoyi.system.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.system.config.AiAsyncConfig;
import com.ruoyi.system.domain.AiUserMemory;
import com.ruoyi.system.domain.AiUserProfile;
import com.ruoyi.system.mapper.AiUserMemoryMapper;
import com.ruoyi.system.service.IAiMemoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户长期记忆与画像实现
 *
 * 写入：每轮对话后异步让大模型抽取结构化记忆，落 ai_user_memory 并写入向量库
 *       （metadata: type=memory / userId / memoryId），同时刷新 ai_user_profile。
 * 读取：提问时取「最近 N 条」+「向量语义召回」合并去重，拼成上下文注入 Prompt。
 *
 * 注意：向量库与知识库共用索引，userId 未进索引 schema，召回只能放大 topK 后内存过滤。
 *
 * @author ruoyi
 */
@Service
public class AiMemoryServiceImpl implements IAiMemoryService
{
    private static final Logger log = LoggerFactory.getLogger(AiMemoryServiceImpl.class);

    /** 抽取提示词：只保留「关于用户本人」的信息，强制 JSON 输出 */
    private static final String EXTRACT_PROMPT = """
            你是用户画像分析助手。从下面一轮客服对话里提取【关于用户本人】且值得长期记住的信息。
            只允许三类：
            - preference：用户自己的偏好，如喜欢的品类/作者/价格倾向
            - fact：用户本人的固定事实，如"家里有小孩""是教师"
            - concern：用户自己长期关注的问题
            严格禁止提取：
            - 书籍正文、故事情节、书中人物、书中数字（这些是图书内容，不是用户信息）
            - 客户服务话术、客服推荐的书单、一次性的具体提问本身
            - 任何你推断或编造的内容
            如果没有值得记住的用户信息，输出空数组 []。
            严格只输出 JSON 数组，不要解释，不要 markdown 代码块：
            [{"type":"preference","content":"一句话描述","keywords":"逗号分隔关键词"}]
            type 只能是 preference、fact、concern 三者之一。
            用户问题：%s
            客服回答：%s
            """;

    private static final int RECENT_MEMORY_LIMIT = 5;
    private static final int SEMANTIC_MEMORY_TOPK = 3;

    @Autowired
    private AiUserMemoryMapper memoryMapper;
    @Autowired
    private ChatClient chatClient;
    @Autowired
    @Qualifier("memoryVectorStore")
    private VectorStore vectorStore;

    @Async(AiAsyncConfig.AI_MEMORY_EXECUTOR)
    @Override
    public void extractAndSaveAsync(Long userId, String question, String answer)
    {
        if (userId == null || !StringUtils.hasText(question))
        {
            return;
        }
        try
        {
            // 注意区分两种"空"：
            //   null  = 模型输出不是合法 JSON（重试有意义）
            //   []    = 模型判断这句没有值得记的偏好（合法结果，重试只会白白多调一次模型）
            // 但含"我/孩子/预算"等个人信息的问句返回 [] 时多半是漏抽，允许再试一次。
            boolean mayContainProfile = containsPersonalMarker(question);
            JSONArray array = null;
            for (int attempt = 1; attempt <= 2; attempt++)
            {
                String raw = chatClient.prompt()
                        .user(String.format(EXTRACT_PROMPT, question, answer == null ? "" : answer))
                        .call()
                        .content();
                array = parseJsonArray(raw);
                if (array != null && !array.isEmpty())
                {
                    break;
                }
                boolean retryable = (array == null) || mayContainProfile;
                if (!retryable || attempt == 2)
                {
                    if (array == null)
                    {
                        log.info("记忆抽取输出无法解析，已放弃：" + (raw == null ? "null"
                                : raw.substring(0, Math.min(120, raw.length()))));
                    }
                    break;
                }
            }
            if (array == null || array.isEmpty())
            {
                return;
            }
            // 已有关键词集合，用于简单去重，避免同一偏好被反复写入
            Set<String> existing = new LinkedHashSet<>();
            for (AiUserMemory m : memoryMapper.selectMemoriesByUserId(userId, 30))
            {
                if (StringUtils.hasText(m.getContent()))
                {
                    existing.add(m.getContent().trim());
                }
            }
            for (int i = 0; i < array.size(); i++)
            {
                JSONObject item = array.getJSONObject(i);
                if (item == null)
                {
                    continue;
                }
                String content = item.getString("content");
                if (!StringUtils.hasText(content) || existing.contains(content.trim()))
                {
                    continue;
                }
                AiUserMemory memory = new AiUserMemory();
                memory.setUserId(userId);
                // 只接受三个合法类型，模型偶尔会输出 "user|fact" 之类的非法值
                String type = item.getString("type");
                if (!"preference".equals(type) && !"fact".equals(type) && !"concern".equals(type))
                {
                    type = "fact";
                }
                memory.setMemoryType(type);
                memory.setContent(content.trim());
                memory.setKeywords(item.getString("keywords"));
                // INSERT IGNORE：返回 0 表示并发下已有相同记忆，跳过向量写入避免重复文档
                int inserted = memoryMapper.insertMemory(memory);
                existing.add(content.trim());
                if (inserted <= 0)
                {
                    continue;
                }
                // 写入向量库，供后续语义召回
                try
                {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("type", "memory");
                    meta.put("userId", String.valueOf(userId));
                    meta.put("memoryId", String.valueOf(memory.getId()));
                    vectorStore.add(Collections.singletonList(
                            new Document("memory-" + memory.getId(), memory.getContent(), meta)));
                }
                catch (Exception e)
                {
                    log.info("记忆向量化失败，memoryId=" + memory.getId() + "，" + e.getMessage());
                }
            }
            refreshProfile(userId);
        }
        catch (Exception e)
        {
            log.info("记忆抽取失败：userId=" + userId + "，" + e);
        }
    }

    @Override
    public String buildUserContext(Long userId, String question)
    {
        if (userId == null)
        {
            return "";
        }
        try
        {
            AiUserProfile profile = memoryMapper.selectProfileByUserId(userId);
            // 1. 最近记忆
            List<String> lines = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (AiUserMemory m : memoryMapper.selectMemoriesByUserId(userId, RECENT_MEMORY_LIMIT))
            {
                if (StringUtils.hasText(m.getContent()) && seen.add(m.getContent().trim()))
                {
                    lines.add("[" + m.getMemoryType() + "] " + m.getContent().trim());
                }
            }
            // 2. 语义召回（把命中的记忆计入 hit_count）
            List<Long> hitIds = new ArrayList<>();
            if (StringUtils.hasText(question))
            {
                SearchRequest request = SearchRequest.builder()
                        .query(question)
                        // 独立 aa_memory_index 里全是用户记忆，且 userId 已进 schema，
                        // 直接用 filterExpression 把用户过滤下推到 Redis：
                        // 不再拉 200 条别人的记忆回来再丢，topK 回归真实需要量。
                        .topK(SEMANTIC_MEMORY_TOPK)
                        .similarityThreshold(0.3)
                        .filterExpression("userId == '" + userId + "'")
                        .build();
                List<Document> docs = vectorStore.similaritySearch(request);
                if (docs != null)
                {
                    for (Document doc : docs)
                    {
                        // 双保险：独立索引后理论上不会串用户，仍校验一次防止迁移期脏数据
                        if (!String.valueOf(userId).equals(String.valueOf(doc.getMetadata().get("userId"))))
                        {
                            continue;
                        }
                        String text = doc.getText();
                        if (!StringUtils.hasText(text) || !seen.add(text.trim()))
                        {
                            continue;
                        }
                        lines.add("[recall] " + text.trim());
                        Object mid = doc.getMetadata().get("memoryId");
                        if (mid != null)
                        {
                            try
                            {
                                hitIds.add(Long.valueOf(String.valueOf(mid)));
                            }
                            catch (NumberFormatException ignored)
                            {
                            }
                        }
                        if (lines.size() >= RECENT_MEMORY_LIMIT + SEMANTIC_MEMORY_TOPK)
                        {
                            break;
                        }
                    }
                }
            }
            if (!hitIds.isEmpty())
            {
                memoryMapper.incrementHit(hitIds);
            }
            boolean hasSummary = profile != null && StringUtils.hasText(profile.getProfileSummary());
            boolean hasTags = profile != null && StringUtils.hasText(profile.getTags());
            // 注意：tags 也要算"有画像"。早期只判断 summary，导致"有标签但摘要为空"的用户
            // 完全不注入（画像表里有数据，问答却看不出任何个性化，极难排查）。
            if (lines.isEmpty() && !hasSummary && !hasTags)
            {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (profile != null && StringUtils.hasText(profile.getProfileSummary()))
            {
                sb.append("【画像摘要】").append(profile.getProfileSummary()).append("\n");
            }
            if (profile != null && StringUtils.hasText(profile.getTags()))
            {
                sb.append("【画像标签】").append(profile.getTags()).append("\n");
            }
            if (!lines.isEmpty())
            {
                sb.append("【相关记忆】\n");
                for (String line : lines)
                {
                    sb.append("- ").append(line).append("\n");
                }
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            log.info("构建用户上下文失败：userId=" + userId + "，" + e);
            return "";
        }
    }

    @Override
    public void touchProfile(Long userId)
    {
        if (userId == null)
        {
            return;
        }
        try
        {
            // 原子 upsert：一条 SQL 完成"存在则 +1、不存在则插入"，
            // 替代原"先查再改再写"的读改写，避免并发提问时计数丢失。
            memoryMapper.incrementProfileQuestion(userId);
        }
        catch (Exception e)
        {
            log.error("更新画像计数失败：userId={}", userId, e);
        }
    }

    /**
     * 读取画像摘要，供推荐等场景回退预算/偏好。无画像返回 null。
     */
    @Override
    public String getProfileSummary(Long userId)
    {
        if (userId == null)
        {
            return null;
        }
        try
        {
            AiUserProfile profile = memoryMapper.selectProfileByUserId(userId);
            return profile == null ? null : profile.getProfileSummary();
        }
        catch (Exception e)
        {
            log.warn("读取画像摘要失败：userId={}，{}", userId, e.getMessage());
            return null;
        }
    }

    /** 根据记忆刷新画像摘要与标签 */
    private void refreshProfile(Long userId)
    {
        List<AiUserMemory> memories = memoryMapper.selectMemoriesByUserId(userId, 8);
        if (memories.isEmpty())
        {
            return;
        }
        StringBuilder summary = new StringBuilder();
        Set<String> tags = new LinkedHashSet<>();
        for (AiUserMemory m : memories)
        {
            if (!StringUtils.hasText(m.getContent()))
            {
                continue;
            }
            if (summary.length() > 0)
            {
                summary.append("；");
            }
            summary.append(m.getContent().trim());
            if (StringUtils.hasText(m.getKeywords()))
            {
                for (String kw : m.getKeywords().split("[,，]"))
                {
                    String t = kw.trim();
                    if (StringUtils.hasText(t) && tags.size() < 8)
                    {
                        tags.add(t);
                    }
                }
            }
        }
        String summaryText = summary.length() > 900 ? summary.substring(0, 900) : summary.toString();
        String tagText = String.join(",", tags);

        AiUserProfile profile = memoryMapper.selectProfileByUserId(userId);
        if (profile == null)
        {
            profile = new AiUserProfile();
            profile.setUserId(userId);
            profile.setTags(tagText);
            profile.setProfileSummary(summaryText);
            profile.setTotalQuestions(0);
            profile.setTotalSessions(1);
            profile.setLastActiveTime(new Date());
            memoryMapper.insertProfile(profile);
        }
        else
        {
            profile.setTags(tagText);
            profile.setProfileSummary(summaryText);
            memoryMapper.updateProfile(profile);
        }
    }

    /** 解析大模型返回的 JSON 数组，容忍 ```json 包裹与前后噪声 */
    /**
     * 含个人信息的问句：这类问题若返回空数组，多半是模型漏抽，值得再抽一次。
     * 普通 FAQ（"订单多久发货"）返回空数组是正常结果，不该重试。
     */
    private boolean containsPersonalMarker(String question)
    {
        if (!StringUtils.hasText(question))
        {
            return false;
        }
        String[] markers = {"我", "孩子", "儿子", "女儿", "预算", "喜欢", "平时", "偏好", "岁", "家里"};
        for (String marker : markers)
        {
            if (question.contains(marker))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public Map<String, Object> getProfileSnapshot(Long userId)
    {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (userId == null)
        {
            return snapshot;
        }
        AiUserProfile profile = memoryMapper.selectProfileByUserId(userId);
        List<AiUserMemory> memories = memoryMapper.selectMemoriesByUserId(userId, 20);

        List<Map<String, Object>> memoryList = new ArrayList<>();
        for (AiUserMemory memory : memories)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", memory.getMemoryType());
            item.put("content", memory.getContent());
            item.put("keywords", memory.getKeywords());
            item.put("hitCount", memory.getHitCount());
            item.put("createTime", memory.getCreateTime());
            memoryList.add(item);
        }
        snapshot.put("userId", userId);
        snapshot.put("profile", profile);
        snapshot.put("memoryCount", memoryList.size());
        snapshot.put("memories", memoryList);
        // 真实会拼进 Prompt 的文本（question 传 null：跳过语义召回，只看画像 + 最近记忆）
        snapshot.put("injectContext", buildUserContext(userId, null));
        return snapshot;
    }

    private JSONArray parseJsonArray(String raw)
    {
        if (!StringUtils.hasText(raw))
        {
            return null;
        }
        String s = raw.trim();
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start < 0 || end <= start)
        {
            return null;
        }
        try
        {
            return JSON.parseArray(s.substring(start, end + 1));
        }
        catch (Exception e)
        {
            log.info("记忆 JSON 解析失败：" + s.substring(0, Math.min(200, s.length())));
            return null;
        }
    }
}
