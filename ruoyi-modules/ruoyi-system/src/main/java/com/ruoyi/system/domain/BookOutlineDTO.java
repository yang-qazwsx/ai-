package com.ruoyi.system.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * 书籍阅读目录：文件元信息 + 章节目录（不含正文）
 *
 * @author ruoyi
 */
@Schema(description = "书籍阅读目录")
public class BookOutlineDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Schema(description = "图书ID")
    private Long bookId;

    @Schema(description = "书名")
    private String bookTitle;

    @Schema(description = "上传文件名")
    private String fileName;

    @Schema(description = "文件类型：txt/md/pdf")
    private String fileType;

    @Schema(description = "正文字数")
    private Integer charCount;

    @Schema(description = "分片数")
    private Integer chunkCount;

    @Schema(description = "解析状态：1解析中 2成功 3仅可预览（无可抽取文本）")
    private Integer parseStatus;

    @Schema(description = "解析说明")
    private String parseMsg;

    @Schema(description = "是否支持在线预览原文件（PDF 直接内嵌预览）")
    private boolean previewable;

    @Schema(description = "章节目录")
    private List<ChapterDTO> chapters;

    /**
     * 章节目录项：chunkNo 作为阅读页码
     */
    @Schema(description = "章节目录项")
    public static class ChapterDTO implements Serializable
    {
        private static final long serialVersionUID = 1L;

        @Schema(description = "分片序号（阅读页码）")
        private Integer chunkNo;

        @Schema(description = "章节标题")
        private String chapter;

        @Schema(description = "本页字数")
        private Integer charCount;

        public Integer getChunkNo()
        {
            return chunkNo;
        }

        public void setChunkNo(Integer chunkNo)
        {
            this.chunkNo = chunkNo;
        }

        public String getChapter()
        {
            return chapter;
        }

        public void setChapter(String chapter)
        {
            this.chapter = chapter;
        }

        public Integer getCharCount()
        {
            return charCount;
        }

        public void setCharCount(Integer charCount)
        {
            this.charCount = charCount;
        }
    }

    public Long getBookId()
    {
        return bookId;
    }

    public void setBookId(Long bookId)
    {
        this.bookId = bookId;
    }

    public String getBookTitle()
    {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle)
    {
        this.bookTitle = bookTitle;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getFileType()
    {
        return fileType;
    }

    public void setFileType(String fileType)
    {
        this.fileType = fileType;
    }

    public Integer getCharCount()
    {
        return charCount;
    }

    public void setCharCount(Integer charCount)
    {
        this.charCount = charCount;
    }

    public Integer getChunkCount()
    {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount)
    {
        this.chunkCount = chunkCount;
    }

    public Integer getParseStatus()
    {
        return parseStatus;
    }

    public void setParseStatus(Integer parseStatus)
    {
        this.parseStatus = parseStatus;
    }

    public String getParseMsg()
    {
        return parseMsg;
    }

    public void setParseMsg(String parseMsg)
    {
        this.parseMsg = parseMsg;
    }

    public boolean isPreviewable()
    {
        return previewable;
    }

    public void setPreviewable(boolean previewable)
    {
        this.previewable = previewable;
    }

    public List<ChapterDTO> getChapters()
    {
        return chapters;
    }

    public void setChapters(List<ChapterDTO> chapters)
    {
        this.chapters = chapters;
    }
}
