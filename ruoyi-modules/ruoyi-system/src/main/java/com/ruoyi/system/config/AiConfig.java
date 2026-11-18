package com.ruoyi.system.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 相关 Bean 补充配置
 *
 * Spring AI 2.0 的 ChatClientAutoConfiguration 只注册 ChatClient.Builder，
 * 不会注册 ChatClient 本身，而 AiServiceImpl 里直接 @Autowired 的是 ChatClient，
 * 不补这个 Bean 会在 VectorStore 问题解决后紧接着报第二个注入失败。
 *
 * VectorStore 不需要在这里定义：修好 jedis 版本和 Redis 客户端类型后，
 * 由 spring-ai-starter-vector-store-redis 的自动配置提供 RedisVectorStore。
 *
 * @author ruoyi
 */
@Configuration
public class AiConfig
{
    @Bean
    @ConditionalOnMissingBean(ChatClient.class)
    public ChatClient chatClient(ChatClient.Builder builder)
    {
        return builder.build();
    }
}
