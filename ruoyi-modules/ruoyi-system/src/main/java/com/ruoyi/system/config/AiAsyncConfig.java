package com.ruoyi.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 模块专用异步线程池与定时任务开关。
 *
 * 背景：AiMemoryServiceImpl 的记忆抽取（每轮对话调大模型 + 写库 + 写向量库）以及
 * BookSearchServiceImpl 的索引初始化/对账，此前都用默认无界线程池或裸 new Thread，
 * 存在任务无限堆积、线程不可控、服务关闭不优雅等问题。
 *
 * 这里定义有界线程池 aiTaskExecutor：
 *   - core/max 有上限，队列有界（200），避免无界入队导致 OOM；
 *   - 拒绝策略 CallerRunsPolicy：队列满时由调用线程执行，形成天然背压；
 *   - allowCoreThreadTimeOut 让空闲核心线程可回收。
 *
 * @author ruoyi
 */
@Configuration
@EnableScheduling
public class AiAsyncConfig
{
    /** 记忆抽取 / ES 索引初始化与对账共用的受管线程池 */
    public static final String AI_TASK_EXECUTOR = "aiTaskExecutor";

    /** 记忆抽取专用线程池：队列满时直接丢弃，绝不能回压到 Tomcat 请求线程 */
    public static final String AI_MEMORY_EXECUTOR = "aiMemoryExecutor";

    @Bean(AI_TASK_EXECUTOR)
    public Executor aiTaskExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    /**
     * 记忆抽取专用线程池。
     *
     * 记忆抽取是“LLM 调用 + 写库 + 写向量”的重任务，不能和请求线程共用 CallerRunsPolicy：
     * 队列满时若回压到请求线程，会让普通问答 RT 飙升甚至超过前端 60s 超时。
     * 记忆属于 best-effort 能力，这里用 DiscardPolicy，宁可丢一轮抽取也不拖垮问答。
     */
    @Bean(AI_MEMORY_EXECUTOR)
    public Executor aiMemoryExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ai-memory-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
