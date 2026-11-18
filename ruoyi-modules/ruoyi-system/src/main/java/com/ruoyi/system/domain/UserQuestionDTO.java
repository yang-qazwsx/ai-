package com.ruoyi.system.domain;


import com.ruoyi.common.core.exception.ServiceException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.util.StringUtils;

@Schema(description = "用户提问DTO")
public class UserQuestionDTO {
    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID", required = true)
    private Long userId;

    @NotBlank(message = "提问内容不能为空")
    @Schema(description = "提问内容", required = true)
    private String question;

    @Schema(description = "对话上下文ID（多轮对话时使用）")
    private String contextId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }
    /**
     * 校验参数合法性。
     * 抛 ServiceException 而非 IllegalArgumentException：前者由全局异常处理器统一转成
     * AjaxResult.error(msg)（code=500），与 @Valid 校验（NotBlank/NotNull）的行为一致；
     * 后者会被业务层 catch 掉、把错误文案塞进 answer，前端会把报错当成答案渲染。
     */
    public void validate() {
        if (!StringUtils.hasText(question)) {
            throw new ServiceException("提问内容不能为空且不能仅包含空格");
        }
        if (question.length() > 500) {
            throw new ServiceException("提问内容不能超过500字符");
        }
    }
}
