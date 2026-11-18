<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="分类" prop="category">
        <el-input v-model="queryParams.category" placeholder="请输入分类" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="问题" prop="question">
        <el-input v-model="queryParams.question" placeholder="请输入问题关键词" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
                   v-hasPermi="['ai:knowledge:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate"
                   v-hasPermi="['ai:knowledge:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
                   v-hasPermi="['ai:knowledge:remove']">删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="knowledgeList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" prop="id" width="80" align="center" />
      <el-table-column label="分类" prop="category" width="140" show-overflow-tooltip />
      <el-table-column label="问题" prop="question" min-width="200" show-overflow-tooltip />
      <el-table-column label="答案" prop="answer" min-width="240" show-overflow-tooltip />
      <el-table-column label="命中次数" prop="hitCount" width="90" align="center" />
      <el-table-column label="更新时间" width="160" align="center">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.updateTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)"
                     v-hasPermi="['ai:knowledge:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)"
                     v-hasPermi="['ai:knowledge:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog :title="title" :visible.sync="open" width="640px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="分类" prop="category">
          <el-input v-model="form.category" placeholder="如：订单 / 图书 / 售后" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="问题" prop="question">
          <el-input v-model="form.question" type="textarea" :rows="3" placeholder="用户可能的问法" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="答案" prop="answer">
          <el-input v-model="form.answer" type="textarea" :rows="8" placeholder="标准答案，会直接用于回答或作为知识库检索依据" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="cancel">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listKnowledge, getKnowledge, addKnowledge, updateKnowledge, delKnowledge } from '@/api/biz/knowledge'
import { parseTime } from '@/utils/ruoyi'

export default {
  name: 'AiKnowledge',
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 知识库表格数据
      knowledgeList: [],
      // 弹出层标题
      title: '',
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        category: null,
        question: null
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        category: [{ required: true, message: '分类不能为空', trigger: 'blur' }],
        question: [{ required: true, message: '问题不能为空', trigger: 'blur' }],
        answer: [{ required: true, message: '答案不能为空', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    parseTime,
    /** 查询知识库列表 */
    getList() {
      this.loading = true
      listKnowledge(this.queryParams)
        .then(res => {
          this.knowledgeList = (res && res.rows) || []
          this.total = (res && res.total) || 0
        })
        .catch(() => {
          this.knowledgeList = []
          this.total = 0
        })
        .finally(() => {
          this.loading = false
        })
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        id: undefined,
        category: null,
        question: null,
        answer: null
      }
      if (this.$refs.form) {
        this.$refs.form.resetFields()
      }
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.$refs.queryForm.resetFields()
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleAdd() {
      this.reset()
      this.title = '新增知识库条目'
      this.open = true
    },
    handleUpdate(row) {
      this.reset()
      const id = row.id || this.ids[0]
      getKnowledge(id).then(res => {
        this.form = (res && res.data) || {}
        this.title = '修改知识库条目'
        this.open = true
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) {
          return
        }
        const action = this.form.id ? updateKnowledge(this.form) : addKnowledge(this.form)
        action.then(() => {
          this.$modal.msgSuccess(this.form.id ? '修改成功' : '新增成功')
          this.open = false
          this.getList()
        })
      })
    },
    handleDelete(row) {
      const ids = row.id ? [row.id] : this.ids
      this.$modal.confirm('确认删除选中的 ' + ids.length + ' 条知识库条目？删除后 AI 客服将不再命中这些内容。')
        .then(() => delKnowledge(ids.join(',')))
        .then(() => {
          this.$modal.msgSuccess('删除成功')
          this.getList()
        })
        .catch(() => {})
    }
  }
}
</script>
