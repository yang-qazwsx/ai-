package com.ruoyi.system.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 书籍阅读正文（单页/单分片）
 *
 * @author ruoyi
 */
@Schema(description = "书籍阅读正文")
public class BookChunkDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Schema(description = "图书ID")
    private Long bookId;

    @Schema(description = "当前页（分片序号）")
    private Integer chunkNo;

    @Schema(description = "章节标题")
    private String chapter;

    @Schema(description = "正文")
    private String content;

    @Schema(description = "本页字数")
    private Integer charCount;

    @Schema(description = "总页数")
    private Integer total;

    public Long getBookId()
    {
        return bookId;
    }

    public void setBookId(Long bookId)
    {
        this.bookId = bookId;
    }

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

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public Integer getCharCount()
    {
        return charCount;
    }

    public void setCharCount(Integer charCount)
    {
        this.charCount = charCount;
    }

    public Integer getTotal()
    {
        return total;
    }

    public void setTotal(Integer total)
    {
        this.total = total;
    }
}
