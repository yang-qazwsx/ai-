package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.BizBook;

/**
 * 图书管理 服务层
 *
 * @author ruoyi
 */
public interface IBizBookService
{
    BizBook selectBizBookById(Long bookId);

    List<BizBook> selectBizBookList(BizBook bizBook);

    int insertBizBook(BizBook bizBook);

    int updateBizBook(BizBook bizBook);

    int deleteBizBookByIds(Long[] ids);
}
