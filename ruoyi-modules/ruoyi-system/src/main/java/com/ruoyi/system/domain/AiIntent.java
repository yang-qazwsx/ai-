package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * AI 意图实体 ai_intent
 *
 * @author ruoyi
 */
public class AiIntent implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Schema(description = "意图ID")
    private Long id;

    @Schema(description = "意图名称", required = true)
    private String intentName;

    @Schema(description = "意图描述")
    private String intentDesc;

    @Schema(description = "关键词（多个用逗号分隔）", required = true)
    private String keywords;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIntentName() {
        return intentName;
    }

    public void setIntentName(String intentName) {
        this.intentName = intentName;
    }

    public String getIntentDesc() {
        return intentDesc;
    }

    public void setIntentDesc(String intentDesc) {
        this.intentDesc = intentDesc;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("intentName", getIntentName())
                .append("intentDesc", getIntentDesc())
                .append("keywords", getKeywords())
                .append("createTime", getCreateTime())
                .append("updateTime", getUpdateTime())
                .toString();
    }
}
