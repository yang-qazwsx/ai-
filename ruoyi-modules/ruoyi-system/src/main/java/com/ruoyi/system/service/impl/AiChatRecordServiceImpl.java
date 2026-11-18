package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.system.domain.AiChatRecord;
import com.ruoyi.system.mapper.AiChatRecordMapper;
import com.ruoyi.system.service.IAiChatRecordService;

/**
 * AI 对话记录 服务层实现
 *
 * @author ruoyi
 */
@Service
public class AiChatRecordServiceImpl implements IAiChatRecordService
{
    @Autowired
    private AiChatRecordMapper aiChatRecordMapper;

    @Override
    public AiChatRecord selectAiChatRecordById(Long id)
    {
        return aiChatRecordMapper.selectAiChatRecordById(id);
    }

    @Override
    public List<AiChatRecord> selectAiChatRecordList(AiChatRecord aiChatRecord)
    {
        return aiChatRecordMapper.selectAiChatRecordList(aiChatRecord);
    }

    @Override
    public List<AiChatRecord> selectPageByUserId(Long userId)
    {
        return aiChatRecordMapper.selectPageByUserId(userId);
    }

    @Override
    public int countByUserId(Long userId)
    {
        return aiChatRecordMapper.countByUserId(userId);
    }

    @Override
    public int insertAiChatRecord(AiChatRecord aiChatRecord)
    {
        // user_id / user_question / ai_answer 在表中都是非空且无默认值
        if (aiChatRecord == null || aiChatRecord.getUserId() == null
                || StringUtils.isEmpty(aiChatRecord.getUserQuestion())
                || StringUtils.isEmpty(aiChatRecord.getAiAnswer()))
        {
            throw new ServiceException("用户ID、用户提问和AI回复不能为空");
        }
        return aiChatRecordMapper.insertAiChatRecord(aiChatRecord);
    }

    @Override
    public int updateAiChatRecord(AiChatRecord aiChatRecord)
    {
        if (aiChatRecord == null || aiChatRecord.getId() == null)
        {
            throw new ServiceException("记录ID不能为空");
        }
        return aiChatRecordMapper.updateAiChatRecord(aiChatRecord);
    }

    @Override
    public int deleteAiChatRecordByIds(Long[] ids)
    {
        return aiChatRecordMapper.deleteAiChatRecordByIds(ids);
    }

    @Override
    public int deleteByUserId(Long userId)
    {
        return aiChatRecordMapper.deleteByUserId(userId);
    }
}
