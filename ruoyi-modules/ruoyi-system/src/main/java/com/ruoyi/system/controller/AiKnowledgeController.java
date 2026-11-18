package com.ruoyi.system.controller;

import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.system.domain.AiKnowledge;
import com.ruoyi.system.service.IAiKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.ruoyi.common.core.utils.PageUtils.startPage;

/**
 * AI 知识库后台管理接口。
 *
 * 之前只有 Service 没有 Controller，知识库只能直接改数据库；
 * 现在提供标准 CRUD，Service 层会在写入后同步向量库。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "AI 知识库管理", description = "知识库条目 CRUD")
public class AiKnowledgeController extends BaseController {

    @Autowired
    private IAiKnowledgeService knowledgeService;

    @GetMapping("/list")
    @RequiresPermissions("ai:knowledge:list")
    @Operation(summary = "分页查询知识库")
    public TableDataInfo list(AiKnowledge query) {
        startPage();
        List<AiKnowledge> list = knowledgeService.selectAiKnowledgeList(query);
        return getDataTable(list);
    }

    @GetMapping("/{id}")
    @RequiresPermissions("ai:knowledge:query")
    @Operation(summary = "查询知识库详情")
    public AjaxResult get(@PathVariable("id") Long id) {
        return success(knowledgeService.selectAiKnowledgeById(id));
    }

    @PostMapping
    @RequiresPermissions("ai:knowledge:add")
    @Operation(summary = "新增知识库条目")
    public AjaxResult add(@RequestBody AiKnowledge knowledge) {
        return toAjax(knowledgeService.insertAiKnowledge(knowledge));
    }

    @PutMapping
    @RequiresPermissions("ai:knowledge:edit")
    @Operation(summary = "修改知识库条目")
    public AjaxResult edit(@RequestBody AiKnowledge knowledge) {
        return toAjax(knowledgeService.updateAiKnowledge(knowledge));
    }

    @DeleteMapping("/{ids}")
    @RequiresPermissions("ai:knowledge:remove")
    @Operation(summary = "删除知识库条目")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(knowledgeService.deleteAiKnowledgeByIds(ids));
    }
}
