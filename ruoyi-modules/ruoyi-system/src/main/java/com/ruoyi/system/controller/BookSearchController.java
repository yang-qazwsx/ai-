package com.ruoyi.system.controller;

import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.system.service.IBookSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图书检索索引运维接口（ES）
 *
 * 生产环境建议加 @PreAuthorize("@ss.hasPermi('biz:book:edit')")。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/api/v1/book-search")
public class BookSearchController
{
    @Autowired
    private IBookSearchService bookSearchService;

    /** 索引状态：是否启用、ES 是否可达、索引名、文档数 */
    @GetMapping("/status")
    public AjaxResult status()
    {
        return AjaxResult.success(bookSearchService.status());
    }

    /**
     * 全量重建索引（删索引 → 重建 → 批量写入）。
     * 用于：首次部署、索引与库不一致、或改了分词配置之后。
     */
    @PostMapping("/reindex")
    @RequiresPermissions("biz:book:edit")
    public AjaxResult reindex()
    {
        int count = bookSearchService.reindexAll();
        return AjaxResult.success("重建完成，共 " + count + " 条", count);
    }
}
