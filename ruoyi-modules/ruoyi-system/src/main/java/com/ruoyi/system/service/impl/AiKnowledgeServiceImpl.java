package com.ruoyi.system.service.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.system.domain.AiKnowledge;
import com.ruoyi.system.mapper.AiKnowledgeMapper;
import com.ruoyi.system.service.IAiKnowledgeService;

/**
 * AI 问答知识库 服务层实现
 *
 * 知识库是 RAG 的事实来源：DB 写入成功后必须同步向量库，
 * 否则后台改完答案，线上仍会召回旧内容，直到下次重启全量重建。
 *
 * @author ruoyi
 */
@Service
public class AiKnowledgeServiceImpl implements IAiKnowledgeService
{
    @Autowired
    private AiKnowledgeMapper aiKnowledgeMapper;

    @Autowired
    @Qualifier("knowledgeVectorStore")
    private VectorStore vectorStore;

    @Override
    public AiKnowledge selectAiKnowledgeById(Long id)
    {
        return aiKnowledgeMapper.selectAiKnowledgeById(id);
    }

    @Override
    public List<AiKnowledge> selectAiKnowledgeList(AiKnowledge aiKnowledge)
    {
        return aiKnowledgeMapper.selectPageByCategory(aiKnowledge);
    }

    @Override
    public List<AiKnowledge> searchByKeyword(String keyword)
    {
        if (StringUtils.isEmpty(keyword))
        {
            return Collections.emptyList();
        }
        return aiKnowledgeMapper.selectByKeyword(keyword.trim());
    }

    @Override
    public int increaseHitCount(List<Long> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return 0;
        }
        return aiKnowledgeMapper.batchUpdateHitCount(ids);
    }

    @Override
    public int insertAiKnowledge(AiKnowledge aiKnowledge)
    {
        // question / answer / category 在表中都是非空且无默认值
        if (aiKnowledge == null || StringUtils.isEmpty(aiKnowledge.getQuestion())
                || StringUtils.isEmpty(aiKnowledge.getAnswer())
                || StringUtils.isEmpty(aiKnowledge.getCategory()))
        {
            throw new ServiceException("问题、答案和分类不能为空");
        }
        if (aiKnowledge.getHitCount() == null)
        {
            aiKnowledge.setHitCount(0);
        }
        int rows = aiKnowledgeMapper.insertAiKnowledge(aiKnowledge);
        if (rows > 0 && aiKnowledge.getId() != null)
        {
            syncVector(aiKnowledge);
        }
        return rows;
    }

    @Override
    public int updateAiKnowledge(AiKnowledge aiKnowledge)
    {
        if (aiKnowledge == null || aiKnowledge.getId() == null)
        {
            throw new ServiceException("知识库ID不能为空");
        }
        int rows = aiKnowledgeMapper.updateAiKnowledge(aiKnowledge);
        if (rows > 0)
        {
            // 先删旧向量再写新向量，避免答案修改后仍召回旧文本
            AiKnowledge latest = aiKnowledgeMapper.selectAiKnowledgeById(aiKnowledge.getId());
            if (latest != null)
            {
                syncVector(latest);
            }
        }
        return rows;
    }

    @Override
    public int deleteAiKnowledgeByIds(Long[] ids)
    {
        int rows = aiKnowledgeMapper.deleteAiKnowledgeByIds(ids);
        if (rows > 0 && ids != null && ids.length > 0)
        {
            try
            {
                List<String> docIds = java.util.Arrays.stream(ids)
                        .map(id -> "kb-" + id)
                        .collect(Collectors.toList());
                vectorStore.delete(docIds);
            }
            catch (Exception e)
            {
                // 向量是派生数据，删除失败只告警；下次启动全量重建会清理
                org.slf4j.LoggerFactory.getLogger(AiKnowledgeServiceImpl.class)
                        .warn("删除知识库向量失败：{}", e.getMessage());
            }
        }
        return rows;
    }

    /** 知识库文档写入向量库：id 固定为 kb-<id>，便于更新/删除时精确定位 */
    private void syncVector(AiKnowledge knowledge)
    {
        try
        {
            vectorStore.delete(Collections.singletonList("kb-" + knowledge.getId()));
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("knowledgeId", String.valueOf(knowledge.getId()));
            metadata.put("docType", "knowledge");
            metadata.put("category", knowledge.getCategory());
            metadata.put("question", knowledge.getQuestion());
            metadata.put("answer", knowledge.getAnswer());
            Document document = new Document("kb-" + knowledge.getId(),
                    knowledge.getQuestion() + " " + knowledge.getAnswer(), metadata);
            vectorStore.add(Collections.singletonList(document));
        }
        catch (Exception e)
        {
            org.slf4j.LoggerFactory.getLogger(AiKnowledgeServiceImpl.class)
                    .warn("同步知识库向量失败：id={}，{}", knowledge.getId(), e.getMessage());
        }
    }
}
