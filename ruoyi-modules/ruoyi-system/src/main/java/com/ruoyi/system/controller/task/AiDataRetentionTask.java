package com.ruoyi.system.controller.task;

import com.ruoyi.system.mapper.AiChatRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * AI 数据保留策略：定期清理过期对话记录。
 *
 * 背景：ai_chat_record 会随问答无限增长，既占空间，也让“对话记录”查询越来越慢；
 * 同时聊天内容含用户隐私，长期保留本身也是风险。这里按可配置天数分批删除。
 *
 * 设计要点：
 * 1. 默认关闭（ai.chat.cleanup-enabled=false），避免上线后误删历史数据；
 * 2. 分批删除（每批 limit 条），避免大事务长时间锁表；
 * 3. Redis 锁保证多实例只有一个在执行；Redis 不可用时退化为单机执行（清理是幂等的）。
 *
 * @author ruoyi
 */
@Component
public class AiDataRetentionTask
{
    private static final Logger log = LoggerFactory.getLogger(AiDataRetentionTask.class);

    private static final String CLEANUP_LOCK = "lock:ai:chat-record:cleanup";

    @Autowired
    private AiChatRecordMapper chatRecordMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 是否启用过期清理 */
    @Value("${ai.chat.cleanup-enabled:false}")
    private boolean cleanupEnabled;

    /** 对话记录保留天数 */
    @Value("${ai.chat.record-retention-days:180}")
    private int retentionDays;

    /** 单批删除条数 */
    @Value("${ai.chat.cleanup-batch-size:2000}")
    private int batchSize;

    /** 单次任务最多清理批数，避免一次跑太久 */
    @Value("${ai.chat.cleanup-max-batches:20}")
    private int maxBatches;

    /** 每天凌晨 3:20 执行，避开业务高峰 */
    @Scheduled(cron = "${ai.chat.cleanup-cron:0 20 3 * * ?}")
    public void cleanupExpiredRecords()
    {
        if (!cleanupEnabled)
        {
            return;
        }
        if (retentionDays <= 0)
        {
            return;
        }
        String lockValue = String.valueOf(System.currentTimeMillis());
        boolean locked = false;
        try
        {
            locked = Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(CLEANUP_LOCK, lockValue, 30, TimeUnit.MINUTES));
            if (!locked)
            {
                return;
            }
        }
        catch (Exception e)
        {
            // Redis 故障不应阻止清理：删除操作幂等，多实例并发最多重复删同一批
            log.warn("获取对话记录清理锁失败，退化为单机执行：{}", e.getMessage());
        }

        Date before = retentionDate();
        int total = 0;
        try
        {
            for (int i = 0; i < maxBatches; i++)
            {
                int deleted = chatRecordMapper.deleteBefore(before, batchSize);
                total += deleted;
                if (deleted < batchSize)
                {
                    break;
                }
            }
            if (total > 0)
            {
                log.info("已清理过期对话记录 {} 条（早于 {}，保留 {} 天）",
                        total, before, retentionDays);
            }
        }
        catch (Exception e)
        {
            log.error("清理过期对话记录失败：已删除 {} 条，{}", total, e.getMessage(), e);
        }
        finally
        {
            if (locked)
            {
                try
                {
                    if (lockValue.equals(redisTemplate.opsForValue().get(CLEANUP_LOCK)))
                    {
                        redisTemplate.delete(CLEANUP_LOCK);
                    }
                }
                catch (Exception ignored)
                {
                    // 锁会按 TTL 自动释放
                }
            }
        }
    }

    private Date retentionDate()
    {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -retentionDays);
        return calendar.getTime();
    }
}
