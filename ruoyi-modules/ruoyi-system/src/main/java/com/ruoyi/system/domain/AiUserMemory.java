package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.Date;
import java.math.BigDecimal;

/**
 * 用户长期记忆
 *
 * @author ruoyi
 */
public class AiUserMemory implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** id */
    private Long id;

    /** userId */
    private Long userId;

    /** memoryType */
    private String memoryType;

    /** content */
    private String content;

    /** keywords */
    private String keywords;

    /** vectorId */
    private String vectorId;

    /** confidence（预留字段：抽取时未赋值，始终为默认值 0.80，暂未消费） */
    @Deprecated
    private BigDecimal confidence;

    /** sourceRecordId（预留字段：当前抽取流程未回填来源对话ID） */
    @Deprecated
    private Long sourceRecordId;

    /** hitCount */
    private Integer hitCount;

    /** lastHitTime（预留字段：incrementHit 只加 hitCount，未同步更新此时间） */
    @Deprecated
    private Date lastHitTime;

    /** createTime */
    private Date createTime;

    /** updateTime */
    private Date updateTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getMemoryType()
    {
        return memoryType;
    }

    public void setMemoryType(String memoryType)
    {
        this.memoryType = memoryType;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getKeywords()
    {
        return keywords;
    }

    public void setKeywords(String keywords)
    {
        this.keywords = keywords;
    }

    public String getVectorId()
    {
        return vectorId;
    }

    public void setVectorId(String vectorId)
    {
        this.vectorId = vectorId;
    }

    public BigDecimal getConfidence()
    {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence)
    {
        this.confidence = confidence;
    }

    public Long getSourceRecordId()
    {
        return sourceRecordId;
    }

    public void setSourceRecordId(Long sourceRecordId)
    {
        this.sourceRecordId = sourceRecordId;
    }

    public Integer getHitCount()
    {
        return hitCount;
    }

    public void setHitCount(Integer hitCount)
    {
        this.hitCount = hitCount;
    }

    public Date getLastHitTime()
    {
        return lastHitTime;
    }

    public void setLastHitTime(Date lastHitTime)
    {
        this.lastHitTime = lastHitTime;
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
