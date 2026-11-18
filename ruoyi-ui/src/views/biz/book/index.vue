<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="70px">
      <el-form-item label="书名" prop="title">
        <el-input v-model="queryParams.title" placeholder="请输入书名" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="作者" prop="author">
        <el-input v-model="queryParams.author" placeholder="请输入作者" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="ISBN" prop="isbn">
        <el-input v-model="queryParams.isbn" placeholder="请输入ISBN" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="分类" prop="category">
        <el-input v-model="queryParams.category" placeholder="请输入分类" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" v-hasPermi="['biz:book:add']" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" v-hasPermi="['biz:book:edit']" @click="handleUpdate">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" v-hasPermi="['biz:book:remove']" @click="handleDelete">删除</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="bookList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="图书ID" align="center" prop="bookId" width="80" />
      <el-table-column label="书名" align="center" prop="title" :show-overflow-tooltip="true" />
      <el-table-column label="作者" align="center" prop="author" width="140" />
      <el-table-column label="出版社" align="center" prop="publisher" width="150" :show-overflow-tooltip="true" />
      <el-table-column label="ISBN" align="center" prop="isbn" width="150" />
      <el-table-column label="定价" align="center" prop="price" width="90" />
      <el-table-column label="库存" align="center" prop="stock" width="80" />
      <el-table-column label="分类" align="center" prop="category" width="100" />
      <el-table-column label="状态" align="center" width="90">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.status === '1' ? 'success' : 'info'">{{ scope.row.status === '1' ? '上架' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="260" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-reading" @click="handleView(scope.row)">浏览</el-button>
          <el-button size="mini" type="text" icon="el-icon-upload2" v-hasPermi="['biz:book:edit']" @click="handleUpload(scope.row)">上传内容</el-button>
          <el-button size="mini" type="text" icon="el-icon-edit" v-hasPermi="['biz:book:edit']" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" v-hasPermi="['biz:book:remove']" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" :visible.sync="open" width="620px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="书名" prop="title">
              <el-input v-model="form.title" placeholder="请输入书名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="作者" prop="author">
              <el-input v-model="form.author" placeholder="请输入作者" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="ISBN" prop="isbn">
              <el-input v-model="form.isbn" placeholder="请输入ISBN" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出版社" prop="publisher">
              <el-input v-model="form.publisher" placeholder="请输入出版社" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="定价" prop="price">
              <el-input-number v-model="form.price" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存" prop="stock">
              <el-input-number v-model="form.stock" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-input v-model="form.category" placeholder="请输入分类" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="上架" value="1" />
                <el-option label="下架" value="0" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="简介" prop="description">
              <el-input v-model="form.description" type="textarea" rows="2" placeholder="请输入简介" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <ai-chat title="图书智能客服" :quick-questions="['如何查询图书库存', '图书如何上架下架', '如何查询订单状态']" />

    <!-- 上传书籍正文：解析分片并写入向量库，供 AI 客服按内容问答 -->
    <el-dialog :title="uploadTitle" :visible.sync="uploadOpen" width="520px" append-to-body>
      <el-alert
        v-if="existingContent"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px;"
        :title="'当前已有内容：' + (existingContent.fileName || '') + '（' + (existingContent.charCount || 0) + ' 字 / ' + (existingContent.chunkCount || 0) + ' 页）'"
        description="重新上传会覆盖旧分片、旧向量与旧原文件，且不可恢复" />
      <el-upload
        ref="contentUpload"
        :action="uploadAction"
        :headers="uploadHeaders"
        :data="{ bookId: uploadBookId }"
        :limit="1"
        :auto-upload="false"
        :on-change="onFileChange"
        :on-success="onUploadSuccess"
        :on-error="onUploadError"
        :file-list="uploadFileList"
        name="file"
        accept=".txt,.md,.pdf">
        <el-button size="small" type="primary" icon="el-icon-folder-opened">选择文件</el-button>
        <div slot="tip" class="el-upload__tip">支持 txt / md / pdf，单文件最大 50MB；PDF 解析后可在线预览原件，重复上传会覆盖旧内容</div>
      </el-upload>
      <span slot="footer" class="dialog-footer">
        <el-button @click="uploadOpen = false">取 消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">{{ existingContent ? '重新上传并覆盖' : '开始解析' }}</el-button>
      </span>
    </el-dialog>

    <!-- 在线阅读：PDF 走原件内嵌预览，文本类走章节目录 + 分页正文 -->
    <el-drawer :title="viewerTitle" :visible.sync="viewerOpen" size="72%" @close="closeViewer">
      <div v-loading="viewerLoading" class="book-viewer">
        <div v-if="outline" class="viewer-meta">
          <span class="meta-file">{{ outline.fileName }}</span>
          <el-tag size="mini" type="info">{{ (outline.fileType || '').toUpperCase() }}</el-tag>
          <span class="meta-count">{{ outline.charCount || 0 }} 字 / {{ outline.chunkCount || 0 }} 页</span>
          <el-tag v-if="outline.parseStatus === 3" size="mini" type="warning">{{ outline.parseMsg }}</el-tag>
          <el-button
            v-hasPermi="['biz:book:edit']"
            size="mini"
            type="primary"
            icon="el-icon-upload2"
            style="margin-left: auto;"
            @click="reuploadFromViewer">重新上传</el-button>
        </div>

        <el-tabs v-if="outline" v-model="viewerTab" class="viewer-tabs">
          <el-tab-pane v-if="isPdf" label="原件预览" name="pdf">
            <iframe v-if="pdfUrl" :src="pdfUrl" class="pdf-frame"></iframe>
            <div v-else class="viewer-empty">原件加载失败，可尝试重新上传</div>
          </el-tab-pane>
          <el-tab-pane v-if="hasText" label="文字版" name="text">
            <div class="reader-wrap">
              <div class="toc">
                <div class="toc-title">目录（{{ outline.chunkCount }}）</div>
                <div
                  v-for="c in outline.chapters"
                  :key="c.chunkNo"
                  :class="['toc-item', c.chunkNo === currentChunkNo ? 'active' : '']"
                  @click="loadChunk(c.chunkNo)">
                  {{ c.chapter }}
                </div>
              </div>
              <div ref="readerBox" v-loading="chunkLoading" class="reader">
                <h4 class="reader-chapter">{{ currentChapter }}</h4>
                <pre class="reader-content">{{ chunkContent }}</pre>
                <div class="reader-pager">
                  <el-button size="mini" :disabled="currentChunkNo <= 1" @click="loadChunk(currentChunkNo - 1)">上一页</el-button>
                  <span class="pager-text">{{ currentChunkNo }} / {{ totalChunks }}</span>
                  <el-button size="mini" :disabled="currentChunkNo >= totalChunks" @click="loadChunk(currentChunkNo + 1)">下一页</el-button>
                </div>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane v-if="!isPdf && !hasText" label="无可读内容" name="empty">
            <div class="viewer-empty">该文件暂无可读文本，请重新上传</div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { listBook, getBook, addBook, updateBook, delBook, getBookContent, getBookOutline, readBookChunk, getBookFileBlob } from '@/api/biz/book'
import AiChat from '@/components/AiChat'
import { getToken } from '@/utils/auth'

export default {
  name: 'BizBook',
  components: { AiChat },
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      total: 0,
      bookList: [],
      title: '',
      open: false,
      // 书籍正文上传
      uploadOpen: false,
      uploadTitle: '上传书籍正文',
      uploadBookId: null,
      uploading: false,
      uploadFileList: [],
      existingContent: null,
      uploadAction: process.env.VUE_APP_BASE_API + '/system/api/v1/book-content/upload',
      uploadHeaders: { Authorization: 'Bearer ' + getToken() },
      // 在线阅读
      viewerOpen: false,
      viewerLoading: false,
      viewerTitle: '',
      viewerBookId: null,
      viewerTab: 'text',
      outline: null,
      isPdf: false,
      hasText: false,
      pdfUrl: '',
      chunkLoading: false,
      chunkContent: '',
      currentChapter: '',
      currentChunkNo: 1,
      totalChunks: 0,
      queryParams: { pageNum: 1, pageSize: 10, title: undefined, author: undefined, isbn: undefined, category: undefined },
      form: {},
      rules: {
        title: [{ required: true, message: '书名不能为空', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listBook(this.queryParams).then(res => {
        this.bookList = res.rows || []
        this.total = res.total || 0
        this.loading = false
      }).catch(() => { this.loading = false })
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
      this.ids = selection.map(item => item.bookId)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    reset() {
      this.form = { bookId: undefined, title: undefined, author: undefined, isbn: undefined, publisher: undefined, price: 0, stock: 0, category: undefined, status: '1', description: undefined }
      this.resetForm('form')
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增图书'
    },
    handleUpdate(row) {
      this.reset()
      const id = row.bookId || this.ids[0]
      getBook(id).then(res => {
        this.form = res.data || {}
        this.open = true
        this.title = '修改图书'
      })
    },
    submitForm() {
      this.$refs['form'].validate(valid => {
        if (!valid) return
        const action = this.form.bookId ? updateBook(this.form) : addBook(this.form)
        action.then(() => {
          this.$message.success(this.form.bookId ? '修改成功' : '新增成功')
          this.open = false
          this.getList()
        })
      })
    },
    cancel() {
      this.open = false
      this.reset()
    },
    handleUpload(row) {
      this.uploadBookId = row.bookId
      this.uploadFileList = []
      this.uploading = false
      this.existingContent = null
      this.uploadTitle = '上传书籍正文 - ' + (row.title || '')
      this.uploadOpen = true
      // 查一下是否已有内容，决定弹窗提示与按钮文案（重新上传/开始解析）
      getBookContent(row.bookId).then(res => {
        this.existingContent = res.data || null
        if (this.existingContent) {
          this.uploadTitle = '重新上传书籍正文 - ' + (row.title || '')
        }
      }).catch(() => {
        this.existingContent = null
      })
    },
    // ---------- 在线阅读 ----------
    handleView(row) {
      this.viewerBookId = row.bookId
      this.viewerTitle = '在线阅读 - ' + (row.title || '')
      this.viewerOpen = true
      this.viewerLoading = true
      this.outline = null
      this.chunkContent = ''
      this.currentChapter = ''
      this.currentChunkNo = 1
      this.totalChunks = 0
      this.hasText = false
      this.isPdf = false
      this.revokePdf()
      getBookOutline(row.bookId).then(res => {
        const data = res.data || null
        this.outline = data
        if (!data) {
          this.viewerLoading = false
          return
        }
        this.isPdf = (data.fileType || '').toLowerCase() === 'pdf'
        this.hasText = (data.chunkCount || 0) > 0
        this.totalChunks = data.chunkCount || 0
        this.viewerTab = this.isPdf ? 'pdf' : 'text'
        if (this.isPdf && data.previewable) {
          this.loadPdf(row.bookId)
        }
        if (this.hasText) {
          this.loadChunk(1)
        }
        this.viewerLoading = false
      }).catch(() => {
        // 后端返回 code=500「该图书还没有上传内容」时，请求拦截器已弹出提示
        this.viewerLoading = false
        this.viewerOpen = false
      })
    },
    loadChunk(no) {
      if (!no || no < 1) {
        return
      }
      this.chunkLoading = true
      readBookChunk(this.viewerBookId, no).then(res => {
        const d = res.data || {}
        this.chunkContent = d.content || ''
        this.currentChapter = d.chapter || ('第 ' + d.chunkNo + ' 页')
        this.currentChunkNo = d.chunkNo || no
        this.totalChunks = d.total || this.totalChunks
        this.chunkLoading = false
        if (this.$refs.readerBox) {
          this.$refs.readerBox.scrollTop = 0
        }
      }).catch(() => {
        this.chunkLoading = false
      })
    },
    loadPdf(bookId) {
      getBookFileBlob(bookId).then(blob => {
        if (!blob || !(blob instanceof Blob)) {
          return
        }
        this.revokePdf()
        this.pdfUrl = URL.createObjectURL(blob)
      }).catch(() => {
        this.$message.error('原件加载失败，可尝试重新上传')
      })
    },
    revokePdf() {
      if (this.pdfUrl) {
        URL.revokeObjectURL(this.pdfUrl)
        this.pdfUrl = ''
      }
    },
    closeViewer() {
      this.revokePdf()
      this.outline = null
      this.chunkContent = ''
    },
    reuploadFromViewer() {
      const row = this.bookList.find(b => b.bookId === this.viewerBookId) ||
        { bookId: this.viewerBookId, title: this.viewerTitle }
      this.handleUpload(row)
    },
    onFileChange(file, fileList) {
      // 只保留最后选择的 1 个文件
      this.uploadFileList = fileList.slice(-1)
    },
    submitUpload() {
      if (!this.uploadFileList.length) {
        this.$message.warning('请先选择要上传的 txt / md 文件')
        return
      }
      this.uploading = true
      this.$refs.contentUpload.submit()
    },
    onUploadSuccess(res) {
      this.uploading = false
      if (res && res.code === 200) {
        const data = res.data || {}
        if (data.parseStatus === 3) {
          this.$message.warning(data.parseMsg || 'PDF 未抽取出文本，仅支持在线预览')
        } else {
          this.$message.success('解析成功，共 ' + (data.chunkCount || 0) + ' 个页面，AI 客服已可回答本书内容')
        }
        this.uploadOpen = false
        this.getList()
        // 阅读器开着时，上传完直接刷新成新内容
        if (this.viewerOpen && this.viewerBookId === this.uploadBookId) {
          const row = this.bookList.find(b => b.bookId === this.viewerBookId) ||
            { bookId: this.viewerBookId, title: this.viewerTitle }
          this.handleView(row)
        }
      } else {
        this.$message.error((res && res.msg) || '解析失败')
      }
    },
    onUploadError() {
      this.uploading = false
      this.$message.error('上传失败，请检查文件大小与格式（txt / md / pdf，≤50MB）')
    },
    handleDelete(row) {
      const ids = row.bookId || this.ids
      this.$confirm('是否确认删除图书编号为 "' + ids + '" 的数据项？', '提示', { type: 'warning' }).then(() => {
        return delBook(ids)
      }).then(() => {
        this.getList()
        this.$message.success('删除成功')
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.book-viewer {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0 16px 16px;
  box-sizing: border-box;
}
.viewer-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0 12px;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
  color: #606266;
}
.viewer-meta .meta-file {
  font-weight: 600;
  color: #303133;
}
.viewer-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.viewer-tabs /deep/ .el-tabs__content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.viewer-tabs /deep/ .el-tab-pane {
  height: 100%;
}
.pdf-frame {
  width: 100%;
  height: calc(100vh - 200px);
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.reader-wrap {
  display: flex;
  height: calc(100vh - 200px);
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}
.toc {
  width: 220px;
  flex-shrink: 0;
  overflow-y: auto;
  border-right: 1px solid #ebeef5;
  background: #fafafa;
  padding: 8px 0;
}
.toc-title {
  padding: 0 12px 8px;
  font-size: 12px;
  color: #909399;
}
.toc-item {
  padding: 6px 12px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  line-height: 1.4;
}
.toc-item:hover {
  background: #f0f2f5;
}
.toc-item.active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}
.reader {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 12px 20px;
  overflow-y: auto;
}
.reader-chapter {
  margin: 0 0 12px;
  font-size: 15px;
  color: #303133;
}
.reader-content {
  flex: 1;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.9;
  color: #303133;
}
.reader-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}
.pager-text {
  font-size: 13px;
  color: #909399;
}
.viewer-empty {
  padding: 80px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
</style>
