package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.AiKnowledge;

/**
 * AI 问答知识库 服务层
 *
 * @author ruoyi
 */
public interface IAiKnowledgeService
{
    /** 按主键查询知识条目 */
    AiKnowledge selectAiKnowledgeById(Long id);

    /** 按条件查询知识库列表（配合 PageHelper 分页） */
    List<AiKnowledge> selectAiKnowledgeList(AiKnowledge aiKnowledge);

    /** 按关键词检索（问题或答案命中），热门优先 */
    List<AiKnowledge> searchByKeyword(String keyword);

    /** 批量增加命中次数，每次 +1；空列表返回 0 */
    int increaseHitCount(List<Long> ids);

    /** 新增知识条目 */
    int insertAiKnowledge(AiKnowledge aiKnowledge);

    /** 修改知识条目 */
    int updateAiKnowledge(AiKnowledge aiKnowledge);

    /** 批量删除知识条目 */
    int deleteAiKnowledgeByIds(Long[] ids);
}
