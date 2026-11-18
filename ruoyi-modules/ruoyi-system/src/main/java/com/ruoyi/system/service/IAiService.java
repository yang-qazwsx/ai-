package com.ruoyi.system.service;

import com.ruoyi.system.domain.AiChatRecord;
import com.ruoyi.system.domain.AiHotQuestionDTO;
import com.ruoyi.system.domain.AiIntent;
import com.ruoyi.system.domain.AiResponseDTO;
import com.ruoyi.system.domain.UserQuestionDTO;

import java.util.List;

/**
 * AI 意图 服务层
 *
 * @author ruoyi
 */
public interface IAiService
{
    AiResponseDTO handleUserQuestion(UserQuestionDTO questionDTO);

    /**
     * 从 Redis 读取热门问题（热度排行 TopN + 关联答案）
     *
     * @param limit 返回条数上限
     * @return 热门问题列表（问题、答案、热度）
     */
    List<AiHotQuestionDTO> getHotQuestions(int limit);

    /**
     * 清空指定会话的多轮历史（Redis），用于前端“清空对话”。
     *
     * @param contextId 会话ID
     * @return 是否删除了历史
     */
    boolean clearChatHistory(String contextId);



    /**
     * 初始化知识库向量（系统启动时调用）
     */
    void initKnowledgeVector();
}
