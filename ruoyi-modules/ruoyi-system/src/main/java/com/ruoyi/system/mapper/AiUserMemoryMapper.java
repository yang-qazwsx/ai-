package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.AiUserMemory;
import com.ruoyi.system.domain.AiUserProfile;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户长期记忆与画像 Mapper
 *
 * 记忆按用户维度写入/召回，画像按用户唯一一条聚合更新。
 *
 * @author ruoyi
 */
public interface AiUserMemoryMapper
{
    /** 写入一条记忆（INSERT IGNORE，重复返回 0） */
    int insertMemory(AiUserMemory memory);

    /** 原子自增提问计数（不存在则插入），避免并发丢失更新 */
    int incrementProfileQuestion(@Param("userId") Long userId);

    /** 查询某用户最近的记忆（用于注入 Prompt） */
    List<AiUserMemory> selectMemoriesByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /** 按类型查询记忆 */
    List<AiUserMemory> selectMemoriesByUserIdAndType(@Param("userId") Long userId,
                                                     @Param("memoryType") String memoryType,
                                                     @Param("limit") int limit);

    /** 召回计数 +1（批量） */
    int incrementHit(@Param("ids") List<Long> ids);

    /** 全量记忆（用于向量库重建） */
    List<AiUserMemory> selectAllMemories();

    /** 删除某用户的全部记忆（清空画像时使用） */
    int deleteMemoriesByUserId(Long userId);

    /** 按用户查询画像 */
    AiUserProfile selectProfileByUserId(Long userId);

    /** 新增画像 */
    int insertProfile(AiUserProfile profile);

    /** 更新画像 */
    int updateProfile(AiUserProfile profile);
}
