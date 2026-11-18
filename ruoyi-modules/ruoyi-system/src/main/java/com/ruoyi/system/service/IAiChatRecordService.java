package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.AiChatRecord;

/**
 * AI 对话记录 服务层
 *
 * @author ruoyi
 */
public interface IAiChatRecordService
{
    /** 按主键查询对话记录 */
    AiChatRecord selectAiChatRecordById(Long id);

    /** 按条件查询对话记录（配合 PageHelper 分页） */
    List<AiChatRecord> selectAiChatRecordList(AiChatRecord aiChatRecord);

    /** 查询指定用户的对话历史，按时间倒序（配合 PageHelper 分页） */
    List<AiChatRecord> selectPageByUserId(Long userId);

    /** 统计指定用户的对话条数 */
    int countByUserId(Long userId);

    /** 新增对话记录 */
    int insertAiChatRecord(AiChatRecord aiChatRecord);

    /** 修改对话记录（可用于补写关联知识库ID） */
    int updateAiChatRecord(AiChatRecord aiChatRecord);

    /** 批量删除对话记录 */
    int deleteAiChatRecordByIds(Long[] ids);

    /** 清空指定用户的对话历史 */
    int deleteByUserId(Long userId);
}
