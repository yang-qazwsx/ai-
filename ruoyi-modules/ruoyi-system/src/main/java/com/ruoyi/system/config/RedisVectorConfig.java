package com.ruoyi.system.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;

import java.nio.charset.StandardCharsets;

/**
 * Redis 向量库配置 —— 按业务域拆分为三个独立索引，不再共用 aa_rag_index。
 *
 * 拆分前：知识库(kb-*)、书籍正文(book-*)、用户记忆(memory-*) 共用一个索引，
 * schema 只声明 knowledgeId/answer，type/bookId/userId 无法做 filter，
 * 检索只能放大 topK（记忆 200、正文 100）后在内存里按 ID 前缀过滤：
 *   - 书多时片段互相挤占，答非所问；
 *   - 记忆检索把别人的记忆拉回来再丢掉，既慢又浪费向量召回位。
 *
 * 拆分后每个索引只有单一业务数据，schema 声明各自的可过滤字段，
 * 检索直接用 filterExpression 下推到 Redis，topK 回归真实值。
 *
 * 连接参数统一复用 spring.data.redis 的 host/port/username/password/database/timeout，
 * 避免 RedisClient.builder().build() 固定连 localhost、忽略生产密码与库号的问题。
 *
 * @author ruoyi
 */
@Configuration
public class RedisVectorConfig {

    // ---------- 索引名/前缀：三个业务域完全隔离 ----------

    /** 知识库索引 */
    @Value("${ai.vector.knowledge.index-name:aa_knowledge_index}")
    private String knowledgeIndexName;

    @Value("${ai.vector.knowledge.prefix:aa_knowledge_prefix}")
    private String knowledgePrefix;

    /** 书籍正文分片索引 */
    @Value("${ai.vector.book.index-name:aa_book_index}")
    private String bookIndexName;

    @Value("${ai.vector.book.prefix:aa_book_prefix}")
    private String bookPrefix;

    /** 用户长期记忆索引 */
    @Value("${ai.vector.memory.index-name:aa_memory_index}")
    private String memoryIndexName;

    @Value("${ai.vector.memory.prefix:aa_memory_prefix}")
    private String memoryPrefix;

    // ---------- Redis 连接 ----------

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.username:}")
    private String redisUsername;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.timeout:3000}")
    private long redisTimeoutMillis;

    @Bean
    public RedisClient jedisClient() {
        DefaultJedisClientConfig.Builder clientConfig = DefaultJedisClientConfig.builder()
                .database(redisDatabase)
                .timeoutMillis(Math.toIntExact(redisTimeoutMillis));

        if (StringUtils.hasText(redisUsername)) {
            clientConfig.user(redisUsername);
        }
        if (StringUtils.hasText(redisPassword)) {
            clientConfig.password(redisPassword);
        }

        return RedisClient.builder()
                .hostAndPort(redisHost, redisPort)
                .clientConfig(clientConfig.build())
                .build();
    }

    /**
     * 知识库向量索引。
     * metadata 字段：knowledgeId(TAG)、docType/category/question/answer(TEXT)。
     * 索引不存在才 initializeSchema，存在则保留，避免重启清空数据。
     */
    @Bean
    @Primary
    public VectorStore knowledgeVectorStore(RedisClient jedisClient, StringRedisTemplate redisTemplate,
                                            EmbeddingModel embeddingModel) {
        return buildStore(jedisClient, redisTemplate, embeddingModel,
                knowledgeIndexName, knowledgePrefix,
                RedisVectorStore.MetadataField.tag("knowledgeId"),
                RedisVectorStore.MetadataField.text("docType"),
                RedisVectorStore.MetadataField.text("category"),
                RedisVectorStore.MetadataField.text("question"),
                RedisVectorStore.MetadataField.text("answer"));
    }

    /**
     * 书籍正文分片索引。
     * bookId/chunkNo 为 TAG，chapter/bookTitle 为 TEXT，支持按书过滤下推。
     */
    @Bean
    public VectorStore bookVectorStore(RedisClient jedisClient, StringRedisTemplate redisTemplate,
                                       EmbeddingModel embeddingModel) {
        return buildStore(jedisClient, redisTemplate, embeddingModel,
                bookIndexName, bookPrefix,
                RedisVectorStore.MetadataField.tag("type"),
                RedisVectorStore.MetadataField.tag("bookId"),
                RedisVectorStore.MetadataField.tag("chunkNo"),
                RedisVectorStore.MetadataField.text("bookTitle"),
                RedisVectorStore.MetadataField.text("chapter"));
    }

    /**
     * 用户长期记忆索引。
     * userId/memoryId 为 TAG，支持按用户过滤下推，杜绝跨用户记忆串线。
     */
    @Bean
    public VectorStore memoryVectorStore(RedisClient jedisClient, StringRedisTemplate redisTemplate,
                                         EmbeddingModel embeddingModel) {
        return buildStore(jedisClient, redisTemplate, embeddingModel,
                memoryIndexName, memoryPrefix,
                RedisVectorStore.MetadataField.tag("type"),
                RedisVectorStore.MetadataField.tag("userId"),
                RedisVectorStore.MetadataField.tag("memoryId"));
    }

    /**
     * 兼容别名：历史代码里 @Qualifier("customRedisVectorStore") 的注入点统一指向知识库索引。
     * 保留该 bean 可避免第三方/未改到的代码因找不到 bean 而启动失败。
     */
    @Bean("customRedisVectorStore")
    public VectorStore legacyVectorStore(VectorStore knowledgeVectorStore) {
        return knowledgeVectorStore;
    }

    private VectorStore buildStore(RedisClient jedisClient, StringRedisTemplate redisTemplate,
                                   EmbeddingModel embeddingModel, String indexName, String prefix,
                                   RedisVectorStore.MetadataField... metadataFields) {
        boolean indexExists = checkIndexExists(redisTemplate, indexName);
        RedisVectorStore store = RedisVectorStore.builder(jedisClient, embeddingModel)
                .indexName(indexName)
                .prefix(prefix)
                .initializeSchema(!indexExists)
                .metadataFields(metadataFields)
                .build();
        // 关键：Spring AI 2.0.2 的 build() 不会自动建索引，必须显式触发 afterPropertiesSet，
        // 否则新索引（book/memory）永远不会 FT.CREATE，检索报 "No such index"。
        // initializeSchema=false 时该调用内部会跳过，安全。
        if (store instanceof org.springframework.beans.factory.InitializingBean) {
            try {
                ((org.springframework.beans.factory.InitializingBean) store).afterPropertiesSet();
            } catch (Exception e) {
                throw new IllegalStateException("初始化向量索引失败：" + indexName, e);
            }
        }
        return store;
    }

    /**
     * 通过 FT.INFO 判断索引是否存在。索引不存在时 Redis 返回错误，捕获后返回 false。
     */
    private boolean checkIndexExists(StringRedisTemplate redisTemplate, String indexName) {
        return Boolean.TRUE.equals(
                redisTemplate.execute((RedisCallback<Boolean>) connection -> {
                    try {
                        connection.execute("FT.INFO", indexName.getBytes(StandardCharsets.UTF_8));
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                })
        );
    }
}
