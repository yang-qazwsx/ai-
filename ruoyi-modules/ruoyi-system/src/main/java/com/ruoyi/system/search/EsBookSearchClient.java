package com.ruoyi.system.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.system.domain.BizBook;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图书 ES 检索客户端（官方 Elasticsearch Java Client）。
 *
 * 早期版本是 JDK HttpClient 手写 4 个 REST 调用，理由是"零依赖、curl 可复现、不受客户端版本矩阵影响"。
 * 现在改回官方客户端，原因：
 *   1. 版本已由 spring-boot-dependencies(4.0.6) 统一管理为 9.2.8，与运行中的 ES 9.2.x 服务端同一版本线，
 *      "版本矩阵"的顾虑不再成立，手写版本号反而更容易漂移；
 *   2. 连接池、超时、节点选择、重试、故障节点隔离由 rest5-client（Apache HttpClient5）统一负责，
 *      手写 HttpClient 只有连接池，没有重试/熔断/多节点；
 *   3. 后续要上 API Key / HTTPS / 多节点集群，官方客户端改构造参数即可，手写要给每个请求加 header；
 *   4. 请求/响应是强类型，索引 mapping、query DSL 写错编译期就能发现，不用手拼 JSON 字符串。
 *
 * 中文分词：仍用 ES 内置 ngram tokenizer（min_gram=2,max_gram=2），等价于 MySQL 的 ngram，
 * 且**不需要安装 IK 插件**；bigram 也让英文单词（Java → ja/av/va）能被命中，
 * 正好绕开 MySQL ngram 漏检英文词的问题。
 *
 * @author ruoyi
 */
@Component
public class EsBookSearchClient
{
    private static final Logger log = LoggerFactory.getLogger(EsBookSearchClient.class);

    @Value("${ai.book.search.es-uri:http://127.0.0.1:9220}")
    private String esUri;

    @Value("${ai.book.search.index-name:biz_book_search}")
    private String indexName;

    @Value("${ai.book.search.es-enabled:true}")
    private boolean enabled;

    @Value("${ai.book.search.connect-timeout-seconds:2}")
    private int connectTimeoutSeconds;

    @Value("${ai.book.search.request-timeout-seconds:5}")
    private int requestTimeoutSeconds;

    /** 索引定义：bigram 分词 + 只保留检索需要的字段（settings/mappings 用原始 JSON，避免冗长的强类型嵌套） */
    private static final String INDEX_BODY = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "analysis": {
                  "tokenizer": {
                    "cjk_bigram": {
                      "type": "ngram",
                      "min_gram": 2,
                      "max_gram": 2,
                      "token_chars": ["letter", "digit"]
                    }
                  },
                  "analyzer": {
                    "cjk": {
                      "type": "custom",
                      "tokenizer": "cjk_bigram",
                      "filter": ["lowercase"]
                    }
                  }
                }
              },
              "mappings": {
                "properties": {
                  "bookId":         { "type": "long" },
                  "title":          { "type": "text", "analyzer": "cjk", "search_analyzer": "cjk" },
                  "titleKeyword":   { "type": "keyword" },
                  "author":         { "type": "text", "analyzer": "cjk" },
                  "category":       { "type": "text", "analyzer": "cjk" },
                  "description":    { "type": "text", "analyzer": "cjk" },
                  "status":         { "type": "keyword" },
                  "price":          { "type": "scaled_float", "scaling_factor": 100 },
                  "stock":          { "type": "integer" }
                }
              }
            }
            """;

    private volatile ElasticsearchClient client;
    private volatile ElasticsearchTransport transport;

    @PostConstruct
    public void init()
    {
        if (!enabled)
        {
            log.info("ES 检索已关闭（ai.book.search.es-enabled=false），图书检索走 MySQL");
            return;
        }
        try
        {
            client();
            log.info("ES 客户端就绪：{}，索引={}", esUri, indexName);
        }
        catch (RuntimeException e)
        {
            // 启动时 ES 未就绪不阻塞应用，后续调用会再尝试建连，失败则由上层降级 MySQL
            log.error("ES 客户端初始化失败，图书检索将降级 MySQL：{}", e.getMessage());
        }
    }

    @PreDestroy
    public void close()
    {
        try
        {
            if (transport != null)
            {
                transport.close();
            }
        }
        catch (Exception e)
        {
            log.warn("关闭 ES 客户端异常：{}", e.getMessage());
        }
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public String getIndexName()
    {
        return indexName;
    }

    /** ES 是否可达 */
    public boolean ping()
    {
        try
        {
            return client().ping().value();
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /** 索引不存在则创建（幂等） */
    public void ensureIndex() throws Exception
    {
        ElasticsearchClient c = client();
        if (c.indices().exists(e -> e.index(indexName)).value())
        {
            return;
        }
        c.indices().create(cr -> cr.index(indexName).withJson(new StringReader(INDEX_BODY)));
    }

    /**
     * 删除并重建索引：全量重建时必须先删，
     * 否则已从 MySQL 删除的图书会留在索引里，检索出“幽灵数据”。
     */
    public void recreateIndex() throws Exception
    {
        ElasticsearchClient c = client();
        try
        {
            c.indices().delete(d -> d.index(indexName));
        }
        catch (ElasticsearchException e)
        {
            // 索引本来就不存在，忽略
            if (e.status() != 404)
            {
                throw e;
            }
        }
        ensureIndex();
    }

    /** 单条写入（bookId 作为文档 id，天然幂等） */
    public void indexBook(BizBook book) throws Exception
    {
        client().index(i -> i.index(indexName)
                .id(String.valueOf(book.getBookId()))
                .document(toDoc(book))
                // 图书增删改后立刻可检索，避免"改完还要等 1 秒才生效"
                .refresh(Refresh.True));
    }

    public void deleteBook(Long bookId) throws Exception
    {
        try
        {
            // 与写入的 refresh=true 保持一致：删除也要立即可见，否则删完立刻搜索仍能命中幽灵文档。
            client().delete(d -> d.index(indexName)
                    .id(String.valueOf(bookId))
                    .refresh(Refresh.True));
        }
        catch (ElasticsearchException e)
        {
            // 404 表示本来就没索引到，忽略
            if (e.status() != 404)
            {
                throw e;
            }
        }
    }

    /** 批量重建（每批 500 条），返回真正写入成功的条数 */
    public int bulkIndex(List<BizBook> books) throws Exception
    {
        if (books == null || books.isEmpty())
        {
            return 0;
        }
        ElasticsearchClient c = client();
        int done = 0;
        for (int i = 0; i < books.size(); i += 500)
        {
            List<BizBook> batch = books.subList(i, Math.min(i + 500, books.size()));
            BulkRequest.Builder rb = new BulkRequest.Builder();
            // 不在每批用 refresh=true（会制造大量小 segment）；全部批次写完末尾统一 refresh 一次。
            for (BizBook book : batch)
            {
                rb.operations(op -> op.index(idx -> idx
                        .index(indexName)
                        .id(String.valueOf(book.getBookId()))
                        .document(toDoc(book))));
            }
            BulkResponse resp = c.bulk(rb.build());
            // HTTP 200 不代表全部成功：逐条看 items 的 error，避免静默丢数据。
            for (BulkResponseItem item : resp.items())
            {
                if (item.error() != null)
                {
                    logBulkFailure(item.id(), item.error().reason());
                }
                else
                {
                    done++;
                }
            }
        }
        c.indices().refresh(r -> r.index(indexName));
        return done;
    }

    private void logBulkFailure(String docId, String error)
    {
        log.error("[ES bulk] 文档写入失败 id={} error={}", docId, error);
    }

    /** 索引中的文档数（索引不存在返回 -1） */
    public long count() throws Exception
    {
        try
        {
            return client().count(cc -> cc.index(indexName)).count();
        }
        catch (ElasticsearchException e)
        {
            if (e.status() == 404)
            {
                return -1;
            }
            throw e;
        }
    }

    /** 取索引内全部 bookId，用于对账找出幽灵文档（索引不存在返回 null） */
    public Set<Long> allIndexedBookIds() throws Exception
    {
        try
        {
            SearchResponse<Map> resp = client().search(s -> s
                    .index(indexName)
                    .size(10000)
                    .source(src -> src.filter(f -> f.includes("bookId")))
                    .query(q -> q.matchAll(m -> m)), Map.class);
            return toIds(resp);
        }
        catch (ElasticsearchException e)
        {
            if (e.status() == 404)
            {
                return null;
            }
            throw e;
        }
    }

    /**
     * 按关键词检索图书 ID。
     *
     * 打分优先级：书名完全相等(20) > 书名短语命中(8) > 书名分词命中(5)
     *            > 分类命中(2) > 简介命中(1)
     *
     * @param keyword    关键词
     * @param onSaleOnly true 只返回上架图书
     * @param limit      最大条数
     * @return bookId 列表；ES 不可用时抛异常由上层降级
     */
    public List<Long> searchIds(String keyword, boolean onSaleOnly, int limit) throws Exception
    {
        // size 上限校验：防止外部配置异常导致一次拉全索引
        int size = Math.max(1, Math.min(limit, 200));
        SearchResponse<Map> resp = client().search(s -> s
                .index(indexName)
                .size(size)
                .source(src -> src.filter(f -> f.includes("bookId")))
                // description 的 bigram 命中分极低，无门槛会占满 topN 挤掉真正相关的书
                .minScore(2.0)
                .query(buildQuery(keyword, onSaleOnly)), Map.class);
        return new ArrayList<>(toIds(resp));
    }

    private Query buildQuery(String keyword, boolean onSaleOnly)
    {
        return Query.of(q -> q.bool(b ->
        {
            b.should(sh -> sh.term(t -> t.field("titleKeyword").value(keyword).boost(20f)));
            b.should(sh -> sh.matchPhrase(m -> m.field("title").query(keyword).boost(8f)));
            b.should(sh -> sh.match(m -> m.field("title").query(keyword).boost(5f)));
            b.should(sh -> sh.match(m -> m.field("category").query(keyword).boost(2f)));
            b.should(sh -> sh.match(m -> m.field("description").query(keyword).boost(1f)));
            b.minimumShouldMatch("1");
            if (onSaleOnly)
            {
                b.filter(f -> f.term(t -> t.field("status").value("1")));
            }
            return b;
        }));
    }

    private Set<Long> toIds(SearchResponse<Map> resp)
    {
        Set<Long> ids = new LinkedHashSet<>();
        for (Hit<Map> hit : resp.hits().hits())
        {
            Map<?, ?> source = hit.source();
            if (source == null)
            {
                continue;
            }
            Object value = source.get("bookId");
            if (value instanceof Number number)
            {
                ids.add(number.longValue());
            }
        }
        return ids;
    }

    /** 只索引检索需要的字段；价格/库存仍以 MySQL 为准，ES 里仅作参考 */
    private Map<String, Object> toDoc(BizBook book)
    {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("bookId", book.getBookId());
        doc.put("title", nullSafe(book.getTitle()));
        doc.put("titleKeyword", nullSafe(book.getTitle()));
        doc.put("author", nullSafe(book.getAuthor()));
        doc.put("category", nullSafe(book.getCategory()));
        doc.put("description", nullSafe(book.getDescription()));
        doc.put("status", nullSafe(book.getStatus()));
        if (book.getPrice() != null)
        {
            doc.put("price", book.getPrice().doubleValue());
        }
        doc.put("stock", book.getStock() == null ? 0 : book.getStock());
        return doc;
    }

    private String nullSafe(String value)
    {
        return StringUtils.hasText(value) ? value : "";
    }

    /** 懒加载 + 双检：@Value 注入晚于构造，且 ES 晚于应用启动时也能在建连失败后重试 */
    private ElasticsearchClient client()
    {
        ElasticsearchClient current = this.client;
        if (current != null)
        {
            return current;
        }
        synchronized (this)
        {
            if (this.client == null)
            {
                Rest5Client rest5 = Rest5Client.builder(URI.create(esUri))
                        .setConnectionConfigCallback(c -> c.setConnectTimeout(Timeout.ofSeconds(connectTimeoutSeconds)))
                        .setRequestConfigCallback(r -> r.setResponseTimeout(Timeout.ofSeconds(requestTimeoutSeconds)))
                        .build();
                this.transport = new Rest5ClientTransport(rest5, new JacksonJsonpMapper());
                this.client = new ElasticsearchClient(this.transport);
            }
            return this.client;
        }
    }
}
