<template>
  <div class="ai-page">
    <el-row :gutter="16">
      <!-- 左侧：对话区 -->
      <el-col :xs="24" :sm="24" :md="16" :lg="17" :xl="18">
        <el-card class="ai-chat-card" shadow="never">
          <div slot="header" class="ai-chat-header">
            <div class="ai-chat-title">
              <i class="el-icon-service"></i>
              <span>AI 智能客服</span>
              <el-tag size="mini" type="success" effect="plain">在线</el-tag>
            </div>
            <div>
              <el-button size="mini" type="text" icon="el-icon-time" @click="openHistory">历史记录</el-button>
              <el-button size="mini" type="text" icon="el-icon-delete" @click="clearChat">清空对话</el-button>
            </div>
          </div>

          <div ref="msgBox" class="ai-chat-msgs">
            <div
              v-for="(m, i) in messages"
              :key="i"
              class="ai-row"
              :class="m.role === 'user' ? 'is-user' : 'is-ai'"
            >
              <div v-if="m.role !== 'user'" class="ai-avatar">AI</div>
              <div class="ai-bubble-wrap">
                <div class="ai-bubble" :class="m.role === 'user' ? 'bubble-user' : 'bubble-ai'">
                  <span v-if="m.loading" class="ai-typing"><i></i><i></i><i></i></span>
                  <span v-else-if="m.role === 'user'" class="ai-text">{{ m.content || '…' }}</span>
                  <div v-else class="ai-text ai-md" v-html="renderAiText(m.content)"></div>
                </div>
<!--                <div v-if="m.role === 'ai' && !m.loading" class="ai-bubble-meta">-->
<!--                  <el-tag v-if="m.intent" size="mini" type="info" effect="plain">意图：{{ m.intent }}</el-tag>-->
<!--                  <span v-if="m.responseTime != null" class="ai-rt">{{ m.responseTime }} ms</span>-->
<!--                  <div v-if="m.related && m.related.length" class="ai-related">-->
<!--                    <div class="ai-related-title" @click="toggleRelated(i)">-->
<!--                      参考知识（{{ m.related.length }}）-->
<!--                      <i :class="m._showRelated ? 'el-icon-arrow-up' : 'el-icon-arrow-down'"></i>-->
<!--                    </div>-->
<!--                    <div v-show="m._showRelated" class="ai-related-list">-->
<!--                      <div v-for="(k, idx) in m.related" :key="idx" class="ai-related-item">-->
<!--                        <div class="ai-related-q">Q：{{ k.question }}</div>-->
<!--                        <div class="ai-related-simi">相似度：{{ formatSimilarity(k.similarity) }}</div>-->
<!--                      </div>-->
<!--                    </div>-->
<!--                  </div>-->
<!--                </div>-->
              </div>
              <div v-if="m.role === 'user'" class="ai-avatar user">我</div>
            </div>
          </div>

          <div v-if="quickQuestions.length" class="ai-quick">
            <span class="ai-quick-label">快捷提问：</span>
            <el-tag
              v-for="(q, i) in quickQuestions"
              :key="i"
              size="small"
              effect="plain"
              class="ai-quick-tag"
              @click="send(q)"
            >{{ q }}</el-tag>
          </div>

          <div class="ai-input-bar">
            <el-input
              v-model="input"
              type="textarea"
              :rows="3"
              resize="none"
              :disabled="loading"
              placeholder="请输入您的问题，Enter 发送，Shift+Enter 换行"
              @keydown.enter.native="handleEnter"
            />
            <div class="ai-input-actions">
              <span class="ai-input-tip" v-if="contextId"><i class="el-icon-connection"></i> 连续对话已开启</span>
              <el-button type="primary" icon="el-icon-s-promotion" :loading="loading" @click="send()">发送</el-button>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：热门问题（从 Redis 读取） -->
      <el-col :xs="24" :sm="24" :md="8" :lg="7" :xl="6">
        <el-card class="ai-side-card" shadow="never">
          <div slot="header" class="ai-side-title"><i class="el-icon-star-on"></i> 热门问题</div>
          <div v-if="hotLoading" class="ai-faq-loading"><i class="el-icon-loading"></i> 加载中…</div>
          <div v-else-if="!hotQuestions.length" class="ai-faq-empty">暂无热门问题，先去问问客服吧～</div>
          <el-collapse v-else v-model="activeFaq">
            <el-collapse-item v-for="(f, i) in hotQuestions" :key="i" :title="f.question" :name="i">
              <div class="ai-faq-a">{{ f.answer }}</div>
              <div class="ai-faq-meta">
                <el-tag size="mini" type="warning" effect="plain">热度 {{ f.hitCount }}</el-tag>
                <el-button type="text" size="mini" icon="el-icon-chat-dot-round" @click="send(f.question)">问客服</el-button>
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-col>
    </el-row>

    <!-- 历史记录抽屉 -->
    <el-drawer title="历史对话记录" :visible.sync="historyVisible" direction="rtl" size="520px" :append-to-body="true">
      <div class="ai-history">
        <div v-if="historyLoading" class="ai-history-loading"><i class="el-icon-loading"></i> 加载中…</div>
        <div v-else-if="!historyList.length" class="ai-history-empty">暂无历史对话</div>
        <template v-else>
          <div v-for="(h, i) in historyList" :key="i" class="ai-history-item">
            <div class="ai-history-q"><b>我：</b>{{ h.userQuestion }}</div>
            <div class="ai-history-a"><b>AI：</b>{{ h.aiAnswer }}</div>
            <div class="ai-history-time">{{ formatTime(h.chatTime) }}</div>
          </div>
          <el-pagination
            v-if="historyTotal > historyQuery.pageSize"
            small
            layout="prev, pager, next"
            :total="historyTotal"
            :page-size="historyQuery.pageSize"
            :current-page="historyQuery.pageNum"
            @current-change="handleHistoryPage"
            style="margin-top: 12px; text-align: center;"
          />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { askQuestion, getChatRecords, getHotQuestions, clearChatHistory } from '@/api/biz/chat'
import { parseTime } from '@/utils/ruoyi'
import { renderAiText } from '@/utils/aiMarkdown'

export default {
  name: 'AiAichat',
  data() {
    return {
      input: '',
      loading: false,
      contextId: '',
      messages: [
        { role: 'ai', content: '您好，我是 AI 智能客服，可以帮您解答订单、图书相关的常见问题，也可以直接输入订单号查询真实状态。', loading: false }
      ],
      quickQuestions: [
        '订单多久发货',
        '如何查询订单状态',
        '如何申请退款',
        '如何查询图书库存',
        '图书如何上架下架'
      ],
      hotQuestions: [],
      hotLoading: false,
      activeFaq: [],
      historyVisible: false,
      historyLoading: false,
      historyList: [],
      historyTotal: 0,
      historyQuery: { pageNum: 1, pageSize: 10 }
    }
  },
  created() {
    this.loadHotQuestions()
  },
  computed: {
    userId() {
      const user = this.$store && this.$store.state && this.$store.state.user
      return (user && user.id) || 0
    }
  },
  methods: {
    renderAiText,
    send(text) {
      const q = (typeof text === 'string' ? text : this.input || '').trim()
      if (!q || this.loading) return
      this.messages.push({ role: 'user', content: q, loading: false })
      this.input = ''
      this.loading = true
      this.scrollToBottom()
      const payload = { userId: this.userId, question: q }
      if (this.contextId) payload.contextId = this.contextId
      askQuestion(payload)
        .then(res => {
          // 响应拦截器返回整个 AjaxResult 对象，业务 DTO 在 res.data
          const dto = (res && res.data) || {}
          if (dto.contextId) this.contextId = dto.contextId
          this.messages.push({
            role: 'ai',
            content: dto.answer || '暂无回复',
            intent: dto.intent,
            related: dto.relatedKnowledges || [],
            responseTime: dto.responseTime,
            loading: false,
            _showRelated: false
          })
          // 每次问答后刷新热门问题，让新晋热门实时上墙
          this.loadHotQuestions()
        })
        .catch(() => {
          this.messages.push({ role: 'ai', content: '服务暂时不可用，请稍后再试。', loading: false })
        })
        .finally(() => {
          this.loading = false
          this.scrollToBottom()
        })
    },
    handleEnter(e) {
      if (!e.shiftKey) {
        e.preventDefault()
        this.send()
      }
    },
    toggleRelated(i) {
      const cur = !this.messages[i]._showRelated
      this.$set(this.messages[i], '_showRelated', cur)
    },
    clearChat() {
      const oldContextId = this.contextId
      this.messages = [
        { role: 'ai', content: '对话已清空，有什么可以帮您？', loading: false }
      ]
      this.contextId = ''
      // 同步清理服务端多轮上下文：只改前端的话，后端仍保留旧 history，
      // 下轮一旦复用同一 contextId 就会把“已清空”的对话重新注入 Prompt。
      if (oldContextId) {
        clearChatHistory(oldContextId).catch(() => {
          // 清理失败不影响本地已清空的会话，历史会在 TTL 到期后自动过期
        })
      }
    },
    loadHotQuestions() {
      this.hotLoading = true
      getHotQuestions({ limit: 10 })
        .then(res => {
          // 后端返回 AjaxResult，业务数据在 res.data（List<AiHotQuestionDTO>）
          this.hotQuestions = res && res.data ? res.data : []
        })
        .catch(() => {
          this.hotQuestions = []
        })
        .finally(() => {
          this.hotLoading = false
        })
    },
    formatSimilarity(s) {
      if (s == null) return '—'
      return (s * 100).toFixed(1) + '%'
    },
    formatTime(t) {
      if (!t) return ''
      // if (typeof t === 'number') return parseTime(t, '{y}-{m}-{d} {h}:{i}:{s}')
      // return String(t)
      const timestamp = new Date(t).getTime();
      return parseTime(timestamp, '{y}-{m}-{d} {h}:{i}:{s}');
    },
    openHistory() {
      this.historyVisible = true
      this.historyQuery.pageNum = 1
      this.loadHistory()
    },
    loadHistory() {
      this.historyLoading = true
      getChatRecords({ userId: this.userId, pageNum: this.historyQuery.pageNum, pageSize: this.historyQuery.pageSize })
        .then(res => {
          const data = res || {}
          this.historyList = data.rows || []
          this.historyTotal = data.total || 0
        })
        .catch(() => {
          this.historyList = []
          this.historyTotal = 0
        })
        .finally(() => {
          this.historyLoading = false
        })
    },
    handleHistoryPage(page) {
      this.historyQuery.pageNum = page
      this.loadHistory()
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.msgBox
        if (el) el.scrollTop = el.scrollHeight
      })
    }
  }
}
</script>

<style scoped>
.ai-page {
  padding: 0;
}
.ai-chat-card {
  border-radius: 8px;
}
.ai-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ai-chat-title {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
}
.ai-chat-title i {
  font-size: 20px;
  color: #409eff;
  margin-right: 8px;
}
.ai-chat-title .el-tag {
  margin-left: 8px;
}
.ai-chat-msgs {
  height: calc(100vh - 360px);
  min-height: 360px;
  overflow-y: auto;
  padding: 8px 4px;
}
.ai-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 18px;
}
.ai-row.is-user {
  flex-direction: row-reverse;
}
.ai-avatar {
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  line-height: 38px;
  text-align: center;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  margin: 0 10px;
}
.ai-avatar.user {
  background: #67c23a;
}
.ai-bubble-wrap {
  max-width: 76%;
}
.ai-bubble {
  display: inline-block;
  padding: 10px 14px;
  border-radius: 10px;
  line-height: 1.7;
  font-size: 14px;
  word-break: break-all;
}
.ai-text {
  white-space: pre-wrap;
}
/* AI 回复的结构化渲染（v-html）：换行/列表/书名高亮；
   v-html 生成的节点没有 scoped 属性，必须用 ::v-deep 穿透 */
.ai-md {
  white-space: normal;
}
.ai-md ::v-deep .ai-md-p {
  margin: 0;
}
.ai-md ::v-deep .ai-md-gap {
  height: 6px;
}
.ai-md ::v-deep .ai-md-list {
  margin: 4px 0 0;
  padding-left: 20px;
}
.ai-md ::v-deep .ai-md-list li {
  margin: 2px 0;
}
.ai-md ::v-deep .ai-md-title {
  color: #409eff;
  font-weight: 600;
}
.ai-md ::v-deep .ai-md-code {
  padding: 0 4px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.06);
  font-family: Consolas, Monaco, monospace;
}
.bubble-ai {
  background: #f4f4f5;
  color: #303133;
}
.bubble-user {
  background: #409eff;
  color: #fff;
}
.ai-bubble-meta {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}
.ai-rt {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
.ai-related {
  flex-basis: 100%;
  margin-top: 6px;
  padding: 8px 10px;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.ai-related-title {
  font-size: 12px;
  color: #409eff;
  cursor: pointer;
}
.ai-related-item {
  padding: 4px 0;
  border-top: 1px dashed #ebeef5;
}
.ai-related-q {
  font-size: 13px;
  color: #606266;
}
.ai-related-simi {
  font-size: 12px;
  color: #909399;
}
.ai-quick {
  padding: 10px 0 4px;
  border-top: 1px solid #ebeef5;
}
.ai-quick-label {
  font-size: 13px;
  color: #909399;
}
.ai-quick-tag {
  margin: 0 6px 6px 0;
  cursor: pointer;
}
.ai-input-bar {
  margin-top: 10px;
}
.ai-input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.ai-input-tip {
  font-size: 12px;
  color: #67c23a;
}
.ai-typing {
  display: inline-block;
}
.ai-typing i {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin: 0 2px;
  border-radius: 50%;
  background: #909399;
  animation: ai-blink 1.2s infinite both;
}
.ai-typing i:nth-child(2) { animation-delay: 0.2s; }
.ai-typing i:nth-child(3) { animation-delay: 0.4s; }
@keyframes ai-blink {
  0%, 80%, 100% { opacity: 0.2; }
  40% { opacity: 1; }
}
.ai-side-card {
  border-radius: 8px;
}
.ai-side-title {
  font-size: 15px;
  font-weight: 600;
}
.ai-side-title i {
  color: #409eff;
  margin-right: 6px;
}
.ai-faq-a {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
}
.ai-faq-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ai-faq-loading,
.ai-faq-empty {
  text-align: center;
  color: #909399;
  font-size: 13px;
  padding: 28px 0;
}
.ai-history {
  padding: 0 8px;
}
.ai-history-loading,
.ai-history-empty {
  text-align: center;
  color: #909399;
  padding: 40px 0;
}
.ai-history-item {
  padding: 10px 0;
  border-bottom: 1px solid #ebeef5;
}
.ai-history-q {
  font-size: 14px;
  color: #303133;
}
.ai-history-a {
  font-size: 13px;
  color: #606266;
  margin: 4px 0;
  white-space: pre-wrap;
  word-break: break-all;
}
.ai-history-time {
  font-size: 12px;
  color: #909399;
}
</style>
