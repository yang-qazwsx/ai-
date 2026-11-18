package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.AiIntent;

/**
 * AI 意图 服务层
 *
 * @author ruoyi
 */
public interface IAiIntentService
{
    /** 按主键查询意图 */
    AiIntent selectAiIntentById(Long id);

    /** 按意图名称精确查询（用于新增/修改时查重） */
    AiIntent selectAiIntentByName(String intentName);

    /** 按条件查询意图列表（配合 PageHelper 分页） */
    List<AiIntent> selectAiIntentList(AiIntent aiIntent);

    /** 查询全部意图，供意图识别时做关键词匹配 */
    List<AiIntent> selectAllIntents();

    /** 按关键词反查意图；关键词为空时返回全部 */
    List<AiIntent> selectByKeyword(String keyword);

    /** 新增意图 */
    int insertAiIntent(AiIntent aiIntent);

    /** 修改意图 */
    int updateAiIntent(AiIntent aiIntent);

    /** 批量删除意图 */
    int deleteAiIntentByIds(Long[] ids);
}
