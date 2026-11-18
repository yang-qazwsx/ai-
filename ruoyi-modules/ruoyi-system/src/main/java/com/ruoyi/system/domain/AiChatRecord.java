package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * AI 对话记录实体 ai_chat_record
 *
 * @author ruoyi
 */
public class AiChatRecord implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "用户ID", required = true)
    private Long userId;

    @Schema(description = "用户提问", required = true)
    private String userQuestion;

    @Schema(description = "AI回复", required = true)
    private String aiAnswer;

    @Schema(description = "关联知识库ID（多个用逗号分隔）")
    private String knowledgeIds;

    @Schema(description = "对话时间")
    private Date chatTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserQuestion() {
        return userQuestion;
    }

    public void setUserQuestion(String userQuestion) {
        this.userQuestion = userQuestion;
    }

    public String getAiAnswer() {
        return aiAnswer;
    }

    public void setAiAnswer(String aiAnswer) {
        this.aiAnswer = aiAnswer;
    }

    public String getKnowledgeIds() {
        return knowledgeIds;
    }

    public void setKnowledgeIds(String knowledgeIds) {
        this.knowledgeIds = knowledgeIds;
    }

    public Date getChatTime() {
        return chatTime;
    }

    public void setChatTime(Date chatTime) {
        this.chatTime = chatTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("userId", getUserId())
                .append("userQuestion", getUserQuestion())
                .append("aiAnswer", getAiAnswer())
                .append("knowledgeIds", getKnowledgeIds())
                .append("chatTime", getChatTime())
                .toString();
    }
}
