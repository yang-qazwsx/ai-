package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.AiChatRecord;

/**
 * AI 对话记录 数据层
 *
 * @author ruoyi
 */

@Mapper
public interface AiChatRecordMapper
{
    /**
     * 按主键查询对话记录
     *
     * @param id 记录ID
     * @return 对话记录
     */
    AiChatRecord selectAiChatRecordById(@Param("id") Long id);

    /**
     * 查询指定用户的对话历史，按对话时间倒序
     * Service 层调用 startPage() 后由 PageHelper 完成分页
     *
     * @param userId 用户ID
     * @return 对话记录列表
     */
    List<AiChatRecord> selectPageByUserId(@Param("userId") Long userId);

    /**
     * 按条件查询对话记录（用户ID、提问内容、关联知识库ID）
     *
     * @param aiChatRecord 查询条件
     * @return 对话记录列表
     */
    List<AiChatRecord> selectAiChatRecordList(AiChatRecord aiChatRecord);

    /**
     * 统计指定用户的对话条数
     *
     * @param userId 用户ID
     * @return 对话条数
     */
    int countByUserId(@Param("userId") Long userId);

    /**
     * 新增对话记录（chat_time 由数据库默认值维护）
     *
     * @param aiChatRecord 对话记录，主键回填到 id
     * @return 新增行数
     */
    int insertAiChatRecord(AiChatRecord aiChatRecord);

    /**
     * 按主键修改对话记录，只更新非空字段（可用于补写关联知识库ID）
     *
     * @param aiChatRecord 对话记录
     * @return 更新行数
     */
    int updateAiChatRecord(AiChatRecord aiChatRecord);

    /**
     * 按主键批量删除对话记录
     *
     * @param ids 记录ID数组，允许为空（返回 0）
     * @return 删除行数
     */
    int deleteAiChatRecordByIds(@Param("ids") Long[] ids);

    /**
     * 清空指定用户的对话历史
     *
     * @param userId 用户ID
     * @return 删除行数
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 清理早于指定时间的对话记录（每次最多删除 limit 条，避免大事务锁表）。
     *
     * @param before 截止时间
     * @param limit  单批条数
     * @return 删除行数
     */
    int deleteBefore(@Param("before") Date before, @Param("limit") int limit);
}
