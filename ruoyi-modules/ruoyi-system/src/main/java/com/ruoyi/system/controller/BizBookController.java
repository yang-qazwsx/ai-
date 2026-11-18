package com.ruoyi.system.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.system.domain.BizBook;
import com.ruoyi.system.service.IBizBookService;

/**
 * 图书管理 信息操作处理
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/biz/book")
public class BizBookController extends BaseController
{
    @Autowired
    private IBizBookService bizBookService;

    @RequiresPermissions("biz:book:list")
    @GetMapping("/list")
    public TableDataInfo list(BizBook bizBook)
    {
        startPage();
        List<BizBook> list = bizBookService.selectBizBookList(bizBook);
        return getDataTable(list);
    }

    @RequiresPermissions("biz:book:query")
    @GetMapping("/{bookId}")
    public AjaxResult getInfo(@PathVariable Long bookId)
    {
        return success(bizBookService.selectBizBookById(bookId));
    }

    @RequiresPermissions("biz:book:add")
    @Log(title = "图书管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BizBook bizBook)
    {
        return toAjax(bizBookService.insertBizBook(bizBook));
    }

    @RequiresPermissions("biz:book:edit")
    @Log(title = "图书管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BizBook bizBook)
    {
        return toAjax(bizBookService.updateBizBook(bizBook));
    }

    @RequiresPermissions("biz:book:remove")
    @Log(title = "图书管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{bookIds}")
    public AjaxResult remove(@PathVariable Long[] bookIds)
    {
        return toAjax(bizBookService.deleteBizBookByIds(bookIds));
    }
}
