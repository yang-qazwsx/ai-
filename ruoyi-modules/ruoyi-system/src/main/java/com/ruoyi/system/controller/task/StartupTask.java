package com.ruoyi.system.controller.task;

import com.ruoyi.system.config.AiAsyncConfig;
import com.ruoyi.system.service.IAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * 启动任务：异步初始化知识库向量。
 *
 * 之前直接在 ApplicationRunner 中同步执行，向量重建耗时会拖慢启动，
 * 且初始化抛异常会导致整个 ruoyi-system 启动失败。现改为提交到受管线程池，
 * 失败只记录日志，不阻塞服务就绪。
 */
@Component
public class StartupTask implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupTask.class);

    @Autowired
    private IAiService iAiService;

    @Autowired
    @Qualifier(AiAsyncConfig.AI_TASK_EXECUTOR)
    private Executor aiTaskExecutor;

    @Override
    public void run(ApplicationArguments args) {
        aiTaskExecutor.execute(() -> {
            try {
                iAiService.initKnowledgeVector();
            } catch (Exception e) {
                log.error("启动期知识库向量初始化失败，服务继续运行", e);
            }
        });
    }
}
