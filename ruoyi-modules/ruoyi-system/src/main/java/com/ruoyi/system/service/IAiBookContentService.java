package com.ruoyi.system.service;

import com.ruoyi.system.domain.BizBookContent;
import com.ruoyi.system.domain.BookChunkDTO;
import com.ruoyi.system.domain.BookOutlineDTO;
import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

/**
 * 书籍内容服务：上传解析、分片向量化、语义检索、在线浏览
 *
 * @author ruoyi
 */
public interface IAiBookContentService
{
    /**
     * 上传并解析书籍正文（TXT/MD/PDF），分片入库并写入向量库。
     * 同一本书重复上传会覆盖旧内容、旧分片、旧原文件与多余向量。
     *
     * @param bookId 图书ID
     * @param file   正文文件
     * @return 解析结果（含分片数、状态）
     */
    BizBookContent uploadAndParse(Long bookId, MultipartFile file);

    /** 查询某本书的内容解析状态 */
    BizBookContent getContent(Long bookId);

    /** 按问题检索书籍正文片段（内存按 type=book 过滤） */
    List<Document> searchBookChunks(String question, int topK);

    /**
     * 阅读目录：文件元信息 + 章节目录（不含正文，避免整本书一次传输）。
     * 未上传内容时返回 null。
     */
    BookOutlineDTO getOutline(Long bookId);

    /**
     * 读取单个分片正文，chunkNo 即阅读页码。
     * 内容不存在或页码越界时返回 null。
     */
    BookChunkDTO readChunk(Long bookId, int chunkNo);

    /**
     * 获取上传的原文件（PDF 在线预览用）。
     * 未上传、文件已丢失或路径越界时返回 null。
     */
    File getRawFile(Long bookId);

    /**
     * 删除某本书的全部正文数据：分片、内容元信息、向量、磁盘原文件。
     * 图书被删除时调用，避免已删书的正文仍被 RAG 检索到。
     * 数据库操作在同一事务内，向量与磁盘文件在事务提交后清理。
     *
     * @param bookId 图书ID
     */
    void deleteBookContent(Long bookId);
}

