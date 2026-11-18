package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.system.domain.BizBook;
import com.ruoyi.system.mapper.BizBookMapper;
import com.ruoyi.system.service.IAiBookContentService;
import com.ruoyi.system.service.IBizBookService;
import com.ruoyi.system.service.IBookSearchService;

/**
 * 图书管理 服务层实现
 *
 * @author ruoyi
 */
@Service
public class BizBookServiceImpl implements IBizBookService
{
    @Autowired
    private BizBookMapper bizBookMapper;

    /** ES 检索索引：图书变更后同步，失败只记日志不影响业务 */
    @Autowired
    private IBookSearchService bookSearchService;

    /** 正文数据：图书删除时级联清理分片/文件/向量 */
    @Autowired
    private IAiBookContentService bookContentService;

    @Override
    public BizBook selectBizBookById(Long bookId)
    {
        return bizBookMapper.selectBizBookById(bookId);
    }

    @Override
    public List<BizBook> selectBizBookList(BizBook bizBook)
    {
        return bizBookMapper.selectBizBookList(bizBook);
    }

    @Override
    public int insertBizBook(BizBook bizBook)
    {
        if (bizBook == null || StringUtils.isEmpty(bizBook.getTitle()))
        {
            throw new ServiceException("书名不能为空");
        }
        if (bizBook.getStock() == null)
        {
            bizBook.setStock(0);
        }
        if (StringUtils.isEmpty(bizBook.getStatus()))
        {
            bizBook.setStatus("1");
        }
        int rows = bizBookMapper.insertBizBook(bizBook);
        if (rows > 0)
        {
            bookSearchService.syncBook(bizBookMapper.selectBizBookById(bizBook.getBookId()));
        }
        return rows;
    }

    @Override
    public int updateBizBook(BizBook bizBook)
    {
        if (bizBook == null || bizBook.getBookId() == null)
        {
            throw new ServiceException("图书ID不能为空");
        }
        int rows = bizBookMapper.updateBizBook(bizBook);
        if (rows > 0)
        {
            bookSearchService.syncBook(bizBookMapper.selectBizBookById(bizBook.getBookId()));
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteBizBookByIds(Long[] ids)
    {
        int rows = bizBookMapper.deleteBizBookByIds(ids);
        if (rows > 0 && ids != null)
        {
            for (Long id : ids)
            {
                bookSearchService.deleteBook(id);
                // 级联清理正文分片/内容元信息/向量/磁盘文件：
                // 否则已删除图书的正文仍会被 RAG 检索到，产生“幽灵内容”且表无限增长。
                // 该方法内部事务提交后才清向量与磁盘，外部资源不参与回滚。
                bookContentService.deleteBookContent(id);
            }
        }
        return rows;
    }
}
