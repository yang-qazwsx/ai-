package com.ruoyi.system.service.impl;

import com.ruoyi.system.domain.BizBook;
import com.ruoyi.system.config.AiAsyncConfig;
import com.ruoyi.system.mapper.BizBookMapper;
import com.ruoyi.system.search.EsBookSearchClient;
import com.ruoyi.system.service.IBookSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 图书检索服务实现
 *
 * 一致性策略：MySQL 是事实源，ES 只做检索。
 *   - 写入：图书增删改后同步索引（失败只记日志，不影响业务）
 *   - 读取：先从 ES 拿 bookId，再回 MySQL 查实时价格/库存
 *     （价格与库存变动频繁，直接从 ES 取会展示过期数据）
 * 降级：ES 不可用时 searchBookIds 返回 null，调用方落回 MySQL 全文/LIKE 检索。
 *
 * @author ruoyi
 */
@Service
public class BookSearchServiceImpl implements IBookSearchService
{
    private static final Logger log = LoggerFactory.getLogger(BookSearchServiceImpl.class);
    @Autowired
    private EsBookSearchClient searchClient;

    @Autowired
    private BizBookMapper bookMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    @Qualifier(AiAsyncConfig.AI_TASK_EXECUTOR)
    private Executor aiTaskExecutor;

    /** 同步失败的 bookId 重试集合：单条同步失败后放入，定时任务补偿重投 */
    private final Set<Long> pendingSync = ConcurrentHashMap.newKeySet();

    /** 分布式锁 key：保证多实例只有一个执行索引初始化/对账 */
    private static final String ES_BOOTSTRAP_LOCK = "lock:es:book:bootstrap";

    /** 启动时索引为空则自动全量同步一次 */
    @Value("${ai.book.search.bootstrap-on-start:true}")
    private boolean bootstrapOnStart;

    @Override
    public boolean isEnabled()
    {
        return searchClient.isEnabled();
    }

    @Override
    public List<Long> searchBookIds(String keyword, boolean onSaleOnly, int limit)
    {
        if (!searchClient.isEnabled())
        {
            return null;
        }
        try
        {
            return searchClient.searchIds(keyword, onSaleOnly, limit);
        }
        catch (Exception e)
        {
            // 连接异常时 message 常为 null，补上异常类型便于排查
            log.info("ES 检索失败，降级 MySQL：" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " - " + e.getMessage()));
            return null;
        }
    }

    @Override
    public void syncBook(BizBook book)
    {
        if (!searchClient.isEnabled() || book == null || book.getBookId() == null)
        {
            return;
        }
        try
        {
            searchClient.ensureIndex();
            searchClient.indexBook(book);
            // 写入成功，从待重试集合移除
            pendingSync.remove(book.getBookId());
        }
        catch (Exception e)
        {
            // 索引同步失败不能影响图书增删改本身；放入待补偿集合，由定时对账重投。
            pendingSync.add(book.getBookId());
            log.error("同步图书索引失败，已加入补偿队列：bookId={}，原因={}",
                    book.getBookId(), e.getMessage());
        }
    }

    @Override
    public void deleteBook(Long bookId)
    {
        if (!searchClient.isEnabled() || bookId == null)
        {
            return;
        }
        try
        {
            searchClient.deleteBook(bookId);
            pendingSync.remove(bookId);
        }
        catch (Exception e)
        {
            pendingSync.add(bookId);
            log.error("删除图书索引失败，已加入补偿队列：bookId={}，原因={}", bookId, e.getMessage());
        }
    }

    @Override
    public int reindexAll()
    {
        List<BizBook> books = bookMapper.selectBizBookList(new BizBook());
        try
        {
            searchClient.recreateIndex();
            int count = searchClient.bulkIndex(books);
            log.info("图书索引重建完成：共 " + count + " 条");
            return count;
        }
        catch (Exception e)
        {
            throw new IllegalStateException("重建图书索引失败：" + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> status()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", searchClient.isEnabled());
        map.put("index", searchClient.getIndexName());
        boolean reachable = searchClient.isEnabled() && searchClient.ping();
        map.put("reachable", reachable);
        if (reachable)
        {
            try
            {
                map.put("docCount", searchClient.count());
            }
            catch (Exception e)
            {
                map.put("docCount", -1);
            }
        }
        return map;
    }

    /**
     * 启动时初始化：ES 可达且索引为空则全量同步。
     * 改进点：
     *   1. 用受管线程池（aiTaskExecutor）而非裸 new Thread，受容器生命周期管理；
     *   2. Redis 分布式锁保证多实例只有一个执行重建，避免互相删索引；
     *   3. ES 未就绪不再"一次失败永久放弃"——由定时对账补偿重试。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapIndex()
    {
        if (!searchClient.isEnabled() || !bootstrapOnStart)
        {
            return;
        }
        // 注意：不能直接调用同类 @Async 方法（代理自调用会退化为同步）。
        // 这里显式提交到受管线程池，确保启动就绪不被全量建索引阻塞。
        aiTaskExecutor.execute(this::bootstrapAsync);
    }

    public void bootstrapAsync()
    {
        String lockValue = String.valueOf(System.currentTimeMillis());
        boolean locked = false;
        try
        {
            locked = Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(ES_BOOTSTRAP_LOCK, lockValue, 3, TimeUnit.MINUTES));
            if (!locked)
            {
                log.info("其他实例正在初始化图书索引，本实例跳过");
                return;
            }
            if (!searchClient.ping())
            {
                log.warn("ES 暂不可达，图书检索降级 MySQL；等待定时对账补偿");
                return;
            }
            searchClient.ensureIndex();
            long count = searchClient.count();
            if (count <= 0)
            {
                reindexAll();
            }
            else
            {
                log.info("图书索引已存在，文档数：" + count);
            }
        }
        catch (Exception e)
        {
            // 不在此处放弃：定时对账会重试，检索侧自动降级 MySQL
            log.error("初始化图书索引失败，等待定时对账补偿：{}", e.getMessage());
        }
        finally
        {
            if (locked && lockValue.equals(redisTemplate.opsForValue().get(ES_BOOTSTRAP_LOCK)))
            {
                redisTemplate.delete(ES_BOOTSTRAP_LOCK);
            }
        }
    }

    /**
     * 定时对账（最终一致性补偿）：
     *   - 重试单条同步失败的 bookId；
     *   - 比对 MySQL 与 ES 的 bookId 集合，补缺失、删幽灵。
     * 仅在 ES 启用且可达时执行，异常只告警不影响业务。
     * 周期由 ai.book.search.reconcile-fixed-delay 配置（单位毫秒，默认 300000=5 分钟），首次延迟 60 秒。
     */
    @Scheduled(fixedDelayString = "${ai.book.search.reconcile-fixed-delay:300000}", initialDelay = 60000)
    public void reconcileIndex()
    {
        if (!searchClient.isEnabled())
        {
            return;
        }
        String lockValue = String.valueOf(System.currentTimeMillis());
        boolean locked = false;
        try
        {
            locked = Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(ES_BOOTSTRAP_LOCK, lockValue, 3, TimeUnit.MINUTES));
            if (!locked)
            {
                return;
            }
            if (!searchClient.ping())
            {
                return;
            }
            searchClient.ensureIndex();

            // 1. 补偿单条失败
            for (Long id : new ArrayList<>(pendingSync))
            {
                BizBook book = bookMapper.selectBizBookById(id);
                try
                {
                    if (book == null)
                    {
                        searchClient.deleteBook(id);
                    }
                    else
                    {
                        searchClient.indexBook(book);
                    }
                    pendingSync.remove(id);
                }
                catch (Exception e)
                {
                    log.warn("对账补偿 bookId={} 仍失败：{}", id, e.getMessage());
                }
            }

            // 2. 全量对账：补缺失 + 删幽灵
            List<BizBook> all = bookMapper.selectBizBookList(new BizBook());
            Set<Long> dbIds = new HashSet<>();
            for (BizBook b : all)
            {
                dbIds.add(b.getBookId());
            }
            Set<Long> esIds = searchClient.allIndexedBookIds();
            if (esIds == null)
            {
                return;
            }
            int fixed = 0;
            for (BizBook b : all)
            {
                if (!esIds.contains(b.getBookId()))
                {
                    searchClient.indexBook(b);
                    fixed++;
                }
            }
            for (Long esId : esIds)
            {
                if (!dbIds.contains(esId))
                {
                    searchClient.deleteBook(esId);
                    fixed++;
                }
            }
            if (fixed > 0)
            {
                log.info("ES 索引对账修复 {} 条", fixed);
            }
        }
        catch (Exception e)
        {
            log.error("ES 索引对账失败：{}", e.getMessage());
        }
        finally
        {
            if (locked && lockValue.equals(redisTemplate.opsForValue().get(ES_BOOTSTRAP_LOCK)))
            {
                redisTemplate.delete(ES_BOOTSTRAP_LOCK);
            }
        }
    }
}
