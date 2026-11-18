package com.ruoyi.system.service;

import com.ruoyi.system.domain.BizBook;

import java.util.List;
import java.util.Map;

/**
 * 图书检索服务（ES 为检索索引，MySQL 为事实源）
 *
 * @author ruoyi
 */
public interface IBookSearchService
{
    /** ES 检索是否启用（配置关闭或 ES 不可用时应降级 MySQL） */
    boolean isEnabled();

    /**
     * 按关键词检索图书 ID。
     * ES 不可用时返回 null（区别于“无结果”的空列表），由调用方降级到 MySQL。
     *
     * @param keyword    关键词
     * @param onSaleOnly 是否只查上架图书
     * @param limit      最大条数
     */
    List<Long> searchBookIds(String keyword, boolean onSaleOnly, int limit);

    /** 单本同步（新增/修改后调用，失败不影响主流程） */
    void syncBook(BizBook book);

    /** 删除某本书的索引 */
    void deleteBook(Long bookId);

    /** 全量重建索引，返回写入条数 */
    int reindexAll();

    /** 索引状态：是否启用、ES 是否可达、索引名、文档数 */
    Map<String, Object> status();
}
