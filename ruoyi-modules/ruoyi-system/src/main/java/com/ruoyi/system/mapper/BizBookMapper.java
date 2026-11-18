package com.ruoyi.system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.BizBook;

/**
 * 图书管理 数据层
 *
 * @author ruoyi
 */
@Mapper
public interface BizBookMapper
{
    BizBook selectBizBookById(@Param("bookId") Long bookId);

    List<BizBook> selectBizBookList(BizBook bizBook);

    /**
     * 查询可用于推荐的图书（上架 + 有库存，可选价格上限、分类）。
     * 供 AI 推荐场景使用：只返回真实在售数据，避免模型编造书名与价格。
     *
     * @param maxPrice 预算上限，null 表示不限
     * @param category 品类，null 表示不限
     * @param limit    返回条数
     */
    List<BizBook> selectRecommendCandidates(@Param("maxPrice") java.math.BigDecimal maxPrice,
                                            @Param("category") String category,
                                            @Param("limit") int limit);

    /**
     * 全文索引检索（title/category/description 上的 ngram 索引）。
     * 注意：ngram 对中英混排里的英文词会漏（"Java 编程思想" 搜不到 "Java"），
     * 调用方必须在结果为空时用 {@link #searchBookByLike(String, int)} 兜底。
     *
     * @param keyword 已清洗掉 BOOLEAN MODE 特殊字符的关键词
     * @param limit   返回条数
     */
    List<BizBook> searchBookByFulltext(@Param("keyword") String keyword, @Param("limit") int limit);

    /** LIKE 兜底检索（全文索引未命中时使用），只回传作答所需字段 */
    List<BizBook> searchBookByLike(@Param("keyword") String keyword, @Param("limit") int limit);

    /** 在售且有余量的图书（泛问「有书吗」时使用） */
    List<BizBook> selectOnSaleBooks(@Param("limit") int limit);

    /**
     * 按 ID 批量查询（ES 召回后回查，保证价格/库存是 MySQL 实时值）。
     */
    List<BizBook> selectBooksByIds(@Param("ids") java.util.List<Long> ids);

    /** 全部书名（仅标题列），用于校验模型回答里是否出现了库里没有的书 */
    List<String> selectBookTitles();

    /**
     * 全部图书分类（去重），用于识别"我喜欢看科幻""有科幻的吗"这类只提品类、不提"书"字的问法。
     */
    List<String> selectBookCategories();

    int insertBizBook(BizBook bizBook);

    int updateBizBook(BizBook bizBook);

    int deleteBizBookByIds(@Param("ids") Long[] ids);
}
