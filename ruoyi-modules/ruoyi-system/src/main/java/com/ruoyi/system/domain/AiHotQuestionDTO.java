package com.ruoyi.system.domain;

/**
 * AI 智能客服 热门问题 DTO
 * 用于从 Redis 读取热门问答（热度排行 ZSet + 答案 Hash）后返回前端展示。
 *
 * @author ruoyi
 */
public class AiHotQuestionDTO
{
    /** 问题（即热度 ZSet 的 member、答案 Hash 的 field） */
    private String question;

    /** 答案（来自 hot:question:detail Hash） */
    private String answer;

    /** 热度（被命中/提问次数，来自 hot:question:rank ZSet 的 score） */
    private Long hitCount;

    public String getQuestion()
    {
        return question;
    }

    public void setQuestion(String question)
    {
        this.question = question;
    }

    public String getAnswer()
    {
        return answer;
    }

    public void setAnswer(String answer)
    {
        this.answer = answer;
    }

    public Long getHitCount()
    {
        return hitCount;
    }

    public void setHitCount(Long hitCount)
    {
        this.hitCount = hitCount;
    }
}
