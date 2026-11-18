package com.ruoyi.system.controller;

import com.github.pagehelper.IPage;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.system.domain.AiChatRecord;
import com.ruoyi.system.domain.AiHotQuestionDTO;
import com.ruoyi.system.domain.AiResponseDTO;
import com.ruoyi.system.domain.UserQuestionDTO;
import com.ruoyi.system.service.IAiChatRecordService;
import com.ruoyi.system.service.IAiMemoryService;
import com.ruoyi.system.service.IAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.ruoyi.common.core.utils.PageUtils.startPage;
import static com.ruoyi.common.core.web.domain.AjaxResult.success;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "智能客服接口", description = "用户对话、记录查询等接口")
public class AiController extends BaseController {

    @Autowired
    private  IAiService iAiService;
    @Autowired
    private IAiChatRecordService iAiChatRecordService;
    @Autowired
    private IAiMemoryService iAiMemoryService;
    /**
     * 处理用户提问
     */
    @PostMapping("/question")
    @Operation(summary = "用户提问接口", description = "接收用户提问，返回AI回复（支持多轮对话）")
    @ApiResponse(responseCode = "200", description = "请求成功",
            content = @Content(schema = @Schema(implementation = AiResponseDTO.class)))
    public AjaxResult handleQuestion(
            @Valid @RequestBody @Parameter(description = "用户提问参数", required = true) UserQuestionDTO questionDTO) {
        AiResponseDTO responseDTO = iAiService.handleUserQuestion(questionDTO);


        return success(responseDTO);
    }

    /**
     * 用户画像快照（调试/联调用）
     *
     * 画像只在数据库与 Prompt 里，接口响应看不到，导致"改了偏好却不知道怎么验证"。
     * 这里把画像表、记忆列表和**实际注入 Prompt 的文本**一次性返回。
     */
    @GetMapping("/profile/{userId}")
    @Operation(summary = "用户画像快照", description = "返回画像、长期记忆以及实际注入 Prompt 的上下文文本")
    public AjaxResult profile(@PathVariable("userId") Long userId) {
        // 越权防护：必须登录；仅允许查本人或管理员。未登录直接拒绝（fail-closed）。
        Long loginUserId = SecurityUtils.getUserId();
        if (loginUserId == null || loginUserId <= 0) {
            return AjaxResult.error(401, "请先登录");
        }
        if (!loginUserId.equals(userId) && !SecurityUtils.isAdmin(loginUserId)) {
            return AjaxResult.error(403, "无权查看其他用户的画像");
        }
        return success(iAiMemoryService.getProfileSnapshot(userId));
    }

    /**
     * 分页查询用户对话记录
     *
     * 安全约束：userId 一律以登录态为准，禁止前端通过参数查看他人记录。
     */
    @GetMapping("/records")
    @Operation(summary = "查询对话记录接口", description = "分页查询当前登录用户的历史对话记录")
    @ApiResponse(responseCode = "200", description = "请求成功",
            content = @Content(schema = @Schema(implementation = IPage.class)))
    public TableDataInfo queryChatRecords(AiChatRecord aiChatRecord) {
        Long loginUserId = SecurityUtils.getUserId();
        if (loginUserId == null || loginUserId <= 0) {
            throw new ServiceException("请先登录后查看对话记录");
        }
        aiChatRecord.setUserId(loginUserId);
        startPage();
        List<AiChatRecord> list = iAiChatRecordService.selectAiChatRecordList(aiChatRecord);
        return getDataTable(list);
    }

    /**
     * 查询热门问题（从 Redis 读取热度排行 TopN）
     */
    @GetMapping("/hot-questions")
    @Operation(summary = "查询热门问题接口", description = "从 Redis 读取热门问答（按命中/提问次数排序）")
    @ApiResponse(responseCode = "200", description = "请求成功",
            content = @Content(schema = @Schema(implementation = AiHotQuestionDTO.class)))
    public AjaxResult getHotQuestions(@RequestParam(defaultValue = "10") int limit) {
        return success(iAiService.getHotQuestions(limit));
    }

    /**
     * 清空当前会话的多轮历史（仅 Redis 上下文，不删除数据库聊天记录）。
     */
    @DeleteMapping("/history/{contextId}")
    @Operation(summary = "清空会话历史", description = "删除指定 contextId 的多轮对话缓存")
    public AjaxResult clearHistory(@PathVariable("contextId") String contextId) {
        return success(iAiService.clearChatHistory(contextId));
    }
}
