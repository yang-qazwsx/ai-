package com.ruoyi.system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.AiKnowledge;

/**
 * AI 问答知识库 数据层
 *
 * @author ruoyi
 */

@Mapper
public interface AiKnowledgeMapper
{
    /**
     * 按主键查询知识条目
     *
     * @param id 知识库ID
     * @return 知识条目
     */
    AiKnowledge selectAiKnowledgeById(@Param("id") Long id);

    /**
     * 按条件查询知识库列表（分类精确匹配、问题/答案模糊匹配）
     * Service 层调用 startPage() 后由 PageHelper 完成分页
     *
     * @param aiKnowledge 查询条件
     * @return 知识库列表
     */
    List<AiKnowledge> selectPageByCategory(AiKnowledge aiKnowledge);

    /**
     * 按关键词模糊查询知识库（问题或答案命中），按命中次数倒序
     *
     * @param keyword 关键词
     * @return 匹配的知识库列表
     */
    List<AiKnowledge> selectByKeyword(@Param("keyword") String keyword);

    /**
     * 批量更新命中次数，命中的条目 hit_count 各加 1
     *
     * @param ids 知识库ID列表，允许为空（返回 0）
     * @return 更新行数
     */
    int batchUpdateHitCount(@Param("ids") List<Long> ids);

    /**
     * 新增知识条目
     *
     * @param aiKnowledge 知识条目，主键回填到 id
     * @return 新增行数
     */
    int insertAiKnowledge(AiKnowledge aiKnowledge);

    /**
     * 按主键修改知识条目，只更新非空字段
     *
     * @param aiKnowledge 知识条目
     * @return 更新行数
     */
    int updateAiKnowledge(AiKnowledge aiKnowledge);

    /**
     * 按主键批量删除知识条目
     *
     * @param ids 知识库ID数组，允许为空（返回 0）
     * @return 删除行数
     */
    int deleteAiKnowledgeByIds(@Param("ids") Long[] ids);
}
