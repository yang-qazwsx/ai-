package com.ruoyi.system.controller;

import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.system.domain.BizBookContent;
import com.ruoyi.system.domain.BookChunkDTO;
import com.ruoyi.system.domain.BookOutlineDTO;
import com.ruoyi.system.service.IAiBookContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 书籍内容管理：上传正文、在线浏览（目录 / 正文 / 原文件）
 *
 * 鉴权：上传/覆盖需要 biz:book:edit；查看正文/目录/原文件需要 biz:book:list。
 * 该功能属于图书后台管理能力，不对普通前台用户开放。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/api/v1/book-content")
public class AiBookContentController
{
    @Autowired
    private IAiBookContentService bookContentService;

    /** 上传书籍正文（txt/md/pdf），解析分片并向量化；重复上传即覆盖 */
    @PostMapping("/upload")
    @RequiresPermissions("biz:book:edit")
    public AjaxResult upload(@RequestParam("bookId") Long bookId,
                             @RequestParam("file") MultipartFile file)
    {
        return AjaxResult.success(bookContentService.uploadAndParse(bookId, file));
    }

    /** 查询某本书的解析状态 */
    @GetMapping("/{bookId}")
    @RequiresPermissions("biz:book:list")
    public AjaxResult get(@PathVariable("bookId") Long bookId)
    {
        return AjaxResult.success(bookContentService.getContent(bookId));
    }

    /** 阅读目录：文件元信息 + 章节目录（不含正文，整本书可能几十万字） */
    @GetMapping("/{bookId}/outline")
    @RequiresPermissions("biz:book:list")
    public AjaxResult outline(@PathVariable("bookId") Long bookId)
    {
        BookOutlineDTO outline = bookContentService.getOutline(bookId);
        if (outline == null)
        {
            return AjaxResult.error("该图书还没有上传内容");
        }
        return AjaxResult.success(outline);
    }

    /** 分页读取正文，chunkNo 即页码 */
    @GetMapping("/{bookId}/read")
    @RequiresPermissions("biz:book:list")
    public AjaxResult read(@PathVariable("bookId") Long bookId,
                           @RequestParam(value = "chunkNo", defaultValue = "1") int chunkNo)
    {
        BookChunkDTO chunk = bookContentService.readChunk(bookId, chunkNo);
        if (chunk == null)
        {
            return AjaxResult.error("未找到该页内容，可能尚未上传或页码超出范围");
        }
        return AjaxResult.success(chunk);
    }

    /**
     * 原文件流：PDF 供前端 iframe 内嵌预览，txt/md 也可另存。
     * 用 inline + 文件名 UTF-8 编码，中文名不会乱码，也不会被强制下载。
     */
    @GetMapping("/{bookId}/file")
    @RequiresPermissions("biz:book:list")
    public ResponseEntity<Resource> file(@PathVariable("bookId") Long bookId) throws IOException
    {
        File file = bookContentService.getRawFile(bookId);
        if (file == null)
        {
            return ResponseEntity.notFound().build();
        }
        BizBookContent content = bookContentService.getContent(bookId);
        String fileType = content == null ? null : content.getFileType();
        String fileName = content == null || !StringUtils.hasText(content.getFileName())
                ? file.getName() : content.getFileName();

        MediaType mediaType = "pdf".equalsIgnoreCase(fileType)
                ? MediaType.APPLICATION_PDF
                : new MediaType("text", "plain", StandardCharsets.UTF_8);
        String disposition = ContentDisposition.inline()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentLength(file.length())
                .body(new FileSystemResource(file));
    }
}
