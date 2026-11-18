<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
      <el-form-item label="订单号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入订单号" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="图书名称" prop="bookTitle">
        <el-input v-model="queryParams.bookTitle" placeholder="请输入图书名称" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="收货人" prop="receiver">
        <el-input v-model="queryParams.receiver" placeholder="请输入收货人" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="订单状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" v-hasPermi="['biz:order:add']" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" v-hasPermi="['biz:order:edit']" @click="handleUpdate">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" v-hasPermi="['biz:order:remove']" @click="handleDelete">删除</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="orderList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="订单ID" align="center" prop="orderId" width="80" />
      <el-table-column label="订单号" align="center" prop="orderNo" width="170" :show-overflow-tooltip="true" />
      <el-table-column label="图书" align="center" prop="bookTitle" :show-overflow-tooltip="true" />
      <el-table-column label="数量" align="center" prop="quantity" width="70" />
      <el-table-column label="金额" align="center" prop="amount" width="100" />
      <el-table-column label="状态" align="center" width="100">
        <template slot-scope="scope">
          <el-tag size="mini" :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="收货人" align="center" prop="receiver" width="100" />
      <el-table-column label="联系电话" align="center" prop="phone" width="130" />
      <el-table-column label="下单时间" align="center" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" width="140" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" v-hasPermi="['biz:order:edit']" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" v-hasPermi="['biz:order:remove']" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" :visible.sync="open" width="620px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="订单号" prop="orderNo">
              <el-input v-model="form.orderNo" placeholder="请输入订单号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联图书" prop="bookId">
              <el-input-number v-model="form.bookId" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="图书名称" prop="bookTitle">
              <el-input v-model="form.bookTitle" placeholder="请输入图书名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数量" prop="quantity">
              <el-input-number v-model="form.quantity" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="金额" prop="amount">
              <el-input-number v-model="form.amount" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="收货人" prop="receiver">
              <el-input v-model="form.receiver" placeholder="请输入收货人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="收货地址" prop="address">
              <el-input v-model="form.address" placeholder="请输入收货地址" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <ai-chat title="订单智能客服" :quick-questions="['BO202611110001 什么状态', '订单多久发货', '如何申请退款']" />
  </div>
</template>

<script>
import { listOrder, getOrder, addOrder, updateOrder, delOrder } from '@/api/biz/order'
import AiChat from '@/components/AiChat'

export default {
  name: 'BizOrder',
  components: { AiChat },
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      total: 0,
      orderList: [],
      title: '',
      open: false,
      statusOptions: [
        { value: '0', label: '待付款' },
        { value: '1', label: '待发货' },
        { value: '2', label: '已发货' },
        { value: '3', label: '已完成' },
        { value: '4', label: '已退款' }
      ],
      queryParams: { pageNum: 1, pageSize: 10, orderNo: undefined, bookTitle: undefined, receiver: undefined, status: undefined },
      form: {},
      rules: {
        orderNo: [{ required: true, message: '订单号不能为空', trigger: 'blur' }],
        quantity: [{ required: true, message: '数量不能为空', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listOrder(this.queryParams).then(res => {
        this.orderList = res.rows || []
        this.total = res.total || 0
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    statusLabel(value) {
      const item = this.statusOptions.find(s => s.value === String(value))
      return item ? item.label : value
    },
    statusTagType(value) {
      const map = { '0': 'info', '1': 'warning', '2': 'primary', '3': 'success', '4': 'danger' }
      return map[String(value)] || 'info'
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.orderId)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    reset() {
      this.form = { orderId: undefined, orderNo: undefined, bookId: undefined, bookTitle: undefined, quantity: 1, amount: 0, status: '0', receiver: undefined, phone: undefined, address: undefined, remark: undefined }
      this.resetForm('form')
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增订单'
    },
    handleUpdate(row) {
      this.reset()
      const id = row.orderId || this.ids[0]
      getOrder(id).then(res => {
        this.form = res.data || {}
        this.open = true
        this.title = '修改订单'
      })
    },
    submitForm() {
      this.$refs['form'].validate(valid => {
        if (!valid) return
        const action = this.form.orderId ? updateOrder(this.form) : addOrder(this.form)
        action.then(() => {
          this.$message.success(this.form.orderId ? '修改成功' : '新增成功')
          this.open = false
          this.getList()
        })
      })
    },
    cancel() {
      this.open = false
      this.reset()
    },
    handleDelete(row) {
      const ids = row.orderId || this.ids
      this.$confirm('是否确认删除订单编号为 "' + ids + '" 的数据项？', '提示', { type: 'warning' }).then(() => {
        return delOrder(ids)
      }).then(() => {
        this.getList()
        this.$message.success('删除成功')
      }).catch(() => {})
    }
  }
}
</script>
