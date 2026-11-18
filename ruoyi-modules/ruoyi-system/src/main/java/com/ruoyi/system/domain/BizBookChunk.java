package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 书籍内容分片
 *
 * @author ruoyi
 */
public class BizBookChunk implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** id */
    private Long id;

    /** bookId */
    private Long bookId;

    /** chunkNo */
    private Integer chunkNo;

    /** chapter */
    private String chapter;

    /** content */
    private String content;

    /** charCount */
    private Integer charCount;

    /** vectorId */
    private String vectorId;

    /** createTime */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

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

    public String getVectorId()
    {
        return vectorId;
    }

    public void setVectorId(String vectorId)
    {
        this.vectorId = vectorId;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

}
