package com.ruoyi.system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.AiIntent;

/**
 * AI 意图 数据层
 *
 * @author ruoyi
 */

@Mapper
public interface AiIntentMapper
{
    /**
     * 按主键查询意图
     *
     * @param id 意图ID
     * @return 意图
     */
    AiIntent selectAiIntentById(@Param("id") Long id);

    /**
     * 按意图名称查询（intent_name 有唯一索引，可用于新增时查重）
     *
     * @param intentName 意图名称
     * @return 意图
     */
    AiIntent selectAiIntentByName(@Param("intentName") String intentName);

    /**
     * 按条件查询意图列表（名称、描述、关键词模糊匹配）
     *
     * @param aiIntent 查询条件
     * @return 意图列表
     */
    List<AiIntent> selectAiIntentList(AiIntent aiIntent);

    /**
     * 查询全部意图，供意图识别时在内存中做关键词匹配
     *
     * @return 意图列表
     */
    List<AiIntent> selectAllIntents();

    /**
     * 按关键词反查意图：keywords 为逗号分隔串，同时支持精确项和模糊包含
     *
     * @param keyword 关键词，为空时返回全部
     * @return 匹配的意图列表
     */
    List<AiIntent> selectByKeyword(@Param("keyword") String keyword);

    /**
     * 新增意图（intent_name、keywords 为非空列且无默认值，必须显式传入）
     *
     * @param aiIntent 意图，主键回填到 id
     * @return 新增行数
     */
    int insertAiIntent(AiIntent aiIntent);

    /**
     * 按主键修改意图，只更新非空字段
     *
     * @param aiIntent 意图
     * @return 更新行数
     */
    int updateAiIntent(AiIntent aiIntent);

    /**
     * 按主键批量删除意图
     *
     * @param ids 意图ID数组，允许为空（返回 0）
     * @return 删除行数
     */
    int deleteAiIntentByIds(@Param("ids") Long[] ids);
}
