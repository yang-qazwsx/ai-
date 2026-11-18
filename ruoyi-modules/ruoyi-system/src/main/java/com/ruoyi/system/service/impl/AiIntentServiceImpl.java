package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.system.domain.AiIntent;
import com.ruoyi.system.mapper.AiIntentMapper;
import com.ruoyi.system.service.IAiIntentService;

/**
 * AI 意图 服务层实现
 *
 * @author ruoyi
 */
@Service
public class AiIntentServiceImpl implements IAiIntentService
{
    @Autowired
    private AiIntentMapper aiIntentMapper;

    @Override
    public AiIntent selectAiIntentById(Long id)
    {
        return aiIntentMapper.selectAiIntentById(id);
    }

    @Override
    public AiIntent selectAiIntentByName(String intentName)
    {
        return aiIntentMapper.selectAiIntentByName(intentName);
    }

    @Override
    public List<AiIntent> selectAiIntentList(AiIntent aiIntent)
    {
        return aiIntentMapper.selectAiIntentList(aiIntent);
    }

    @Override
    public List<AiIntent> selectAllIntents()
    {
        return aiIntentMapper.selectAllIntents();
    }

    @Override
    public List<AiIntent> selectByKeyword(String keyword)
    {
        return aiIntentMapper.selectByKeyword(StringUtils.isEmpty(keyword) ? null : keyword.trim());
    }

    @Override
    public int insertAiIntent(AiIntent aiIntent)
    {
        // intent_name / keywords 在表中都是非空且无默认值
        if (aiIntent == null || StringUtils.isEmpty(aiIntent.getIntentName())
                || StringUtils.isEmpty(aiIntent.getKeywords()))
        {
            throw new ServiceException("意图名称和关键词不能为空");
        }
        // intent_name 上有唯一索引 uk_intent_name，先查重给出友好提示
        if (aiIntentMapper.selectAiIntentByName(aiIntent.getIntentName()) != null)
        {
            throw new ServiceException("意图名称已存在：" + aiIntent.getIntentName());
        }
        return aiIntentMapper.insertAiIntent(aiIntent);
    }

    @Override
    public int updateAiIntent(AiIntent aiIntent)
    {
        if (aiIntent == null || aiIntent.getId() == null)
        {
            throw new ServiceException("意图ID不能为空");
        }
        if (StringUtils.isNotEmpty(aiIntent.getIntentName()))
        {
            AiIntent exists = aiIntentMapper.selectAiIntentByName(aiIntent.getIntentName());
            if (exists != null && !exists.getId().equals(aiIntent.getId()))
            {
                throw new ServiceException("意图名称已存在：" + aiIntent.getIntentName());
            }
        }
        return aiIntentMapper.updateAiIntent(aiIntent);
    }

    @Override
    public int deleteAiIntentByIds(Long[] ids)
    {
        return aiIntentMapper.deleteAiIntentByIds(ids);
    }
}
