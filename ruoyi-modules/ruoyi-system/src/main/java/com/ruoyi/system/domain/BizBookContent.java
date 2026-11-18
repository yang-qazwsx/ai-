package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 书籍内容文件
 *
 * @author ruoyi
 */
public class BizBookContent implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** id */
    private Long id;

    /** bookId */
    private Long bookId;

    /** fileName */
    private String fileName;

    /** filePath */
    private String filePath;

    /** fileType */
    private String fileType;

    /** charCount */
    private Integer charCount;

    /** chunkCount */
    private Integer chunkCount;

    /** parseStatus */
    private Integer parseStatus;

    /** parseMsg */
    private String parseMsg;

    /** createTime */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** updateTime */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

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

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getFilePath()
    {
        return filePath;
    }

    public void setFilePath(String filePath)
    {
        this.filePath = filePath;
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

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

}
