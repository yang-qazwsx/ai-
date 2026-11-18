package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户画像
 *
 * @author ruoyi
 */
public class AiUserProfile implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** id */
    private Long id;

    /** userId */
    private Long userId;

    /** tags */
    private String tags;

    /** preferredCategories（预留字段：当前画像逻辑只用 tags + profileSummary，此字段暂未写入/消费） */
    @Deprecated
    private String preferredCategories;

    /** preferredBooks（预留字段：同上，暂未使用） */
    @Deprecated
    private String preferredBooks;

    /** totalQuestions */
    private Integer totalQuestions;

    /** totalSessions */
    private Integer totalSessions;

    /** lastActiveTime */
    private Date lastActiveTime;

    /** profileSummary */
    private String profileSummary;

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

    public String getTags()
    {
        return tags;
    }

    public void setTags(String tags)
    {
        this.tags = tags;
    }

    public String getPreferredCategories()
    {
        return preferredCategories;
    }

    public void setPreferredCategories(String preferredCategories)
    {
        this.preferredCategories = preferredCategories;
    }

    public String getPreferredBooks()
    {
        return preferredBooks;
    }

    public void setPreferredBooks(String preferredBooks)
    {
        this.preferredBooks = preferredBooks;
    }

    public Integer getTotalQuestions()
    {
        return totalQuestions;
    }

    public void setTotalQuestions(Integer totalQuestions)
    {
        this.totalQuestions = totalQuestions;
    }

    public Integer getTotalSessions()
    {
        return totalSessions;
    }

    public void setTotalSessions(Integer totalSessions)
    {
        this.totalSessions = totalSessions;
    }

    public Date getLastActiveTime()
    {
        return lastActiveTime;
    }

    public void setLastActiveTime(Date lastActiveTime)
    {
        this.lastActiveTime = lastActiveTime;
    }

    public String getProfileSummary()
    {
        return profileSummary;
    }

    public void setProfileSummary(String profileSummary)
    {
        this.profileSummary = profileSummary;
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
