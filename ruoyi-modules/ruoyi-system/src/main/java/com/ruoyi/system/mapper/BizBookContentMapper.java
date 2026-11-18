package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.BizBookChunk;
import com.ruoyi.system.domain.BizBookContent;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 书籍内容（文件元信息 + 正文分片）Mapper
 *
 * 两张表紧耦合（一本书 1 条 content、N 条 chunk），合并到一个 Mapper 便于一起维护。
 *
 * @author ruoyi
 */
public interface BizBookContentMapper
{
    /** 按图书ID查询内容文件 */
    BizBookContent selectContentByBookId(Long bookId);

    /** 新增内容文件 */
    int insertContent(BizBookContent content);

    /** 更新内容文件（重新上传解析时覆盖） */
    int updateContent(BizBookContent content);

    /** 删除内容文件 */
    int deleteContentByBookId(Long bookId);

    /** 删除某本书的全部分片（重新解析前先清理） */
    int deleteChunksByBookId(Long bookId);

    /** 批量写入分片 */
    int batchInsertChunks(@Param("list") List<BizBookChunk> chunks);

    /** 查询某本书的全部分片（按序号升序） */
    List<BizBookChunk> selectChunksByBookId(Long bookId);

    /**
     * 只查目录所需字段（不含 content），用于在线浏览的章节目录。
     * 整本书正文可能有数十万字符，目录接口不能把正文一起捞出来。
     */
    List<BizBookChunk> selectChunkOutline(@Param("bookId") Long bookId);

    /** 按分片序号读取单个分片正文（分页阅读） */
    BizBookChunk selectChunkByNo(@Param("bookId") Long bookId, @Param("chunkNo") Integer chunkNo);

    /** 统计某本书的分片数 */
    int countChunksByBookId(Long bookId);
}
