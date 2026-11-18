package com.ruoyi.system.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public class AiResponseDTO {
    @Schema(description = "回复内容")
    private String answer;

    @Schema(description = "关联知识库信息")
    private List<KnowledgeDTO> relatedKnowledges;

    @Schema(description = "本次回复所属分支：热门问题 / 查询订单 / 知识库直达 / 图书推荐 / 识别到的意图 / 未知意图")
    private String intent;

    /**
     * 意图识别算法的真实结果。
     * intent 会被「知识库直达」「热门问题」等分支覆盖，无法反映识别本身是否正确，
     * 因此单独保留该字段；为 null 表示该分支未执行意图识别（缓存命中、订单直达等）。
     */
    @Schema(description = "意图识别算法的真实结果（null 表示该分支未执行意图识别）")
    private String recognizedIntent;

    @Schema(description = "对话上下文ID（用于多轮对话）")
    private String contextId;

    @Schema(description = "响应时间（毫秒）")
    private Long responseTime;

    /**
     * 关联知识库DTO
     */
    @Schema(description = "关联知识库DTO")
    public static class KnowledgeDTO {
        @Schema(description = "知识库ID")
        private Long id;

        @Schema(description = "问题")
        private String question;

        @Schema(description = "答案")
        private String answer;

        @Schema(description = "相似度（0-1）")
        private Float similarity;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }

        public Float getSimilarity() {
            return similarity;
        }

        public void setSimilarity(Float similarity) {
            this.similarity = similarity;
        }
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<KnowledgeDTO> getRelatedKnowledges() {
        return relatedKnowledges;
    }

    public void setRelatedKnowledges(List<KnowledgeDTO> relatedKnowledges) {
        this.relatedKnowledges = relatedKnowledges;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getRecognizedIntent() {
        return recognizedIntent;
    }

    public void setRecognizedIntent(String recognizedIntent) {
        this.recognizedIntent = recognizedIntent;
    }

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public Long getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(Long responseTime) {
        this.responseTime = responseTime;
    }
}
