<template>
  <div class="ai-chat-wrapper">
    <el-tooltip content="智能客服" placement="left">
      <el-button class="ai-chat-fab" type="primary" icon="el-icon-service" circle @click="visible = true" />
    </el-tooltip>

    <el-drawer :title="title" :visible.sync="visible" direction="rtl" size="400px" :append-to-body="true">
      <div class="ai-chat-body">
        <div ref="msgBox" class="ai-chat-msgs">
          <div v-for="(m, i) in messages" :key="i" :class="['ai-msg', m.role === 'user' ? 'is-user' : 'is-ai']">
            <div v-if="m.role === 'user'" class="ai-msg-content">{{ m.content }}</div>
            <div v-else class="ai-msg-content ai-md" v-html="renderAiText(m.content)"></div>
            <div v-if="m.intent" class="ai-msg-meta">识别意图：{{ m.intent }}</div>
            <div v-if="m.related && m.related.length" class="ai-msg-meta">
              命中知识：<span v-for="(k, idx) in m.related" :key="idx">{{ k.question }}<span v-if="idx < m.related.length - 1">、</span></span>
            </div>
          </div>
        </div>

        <div v-if="quickQuestions && quickQuestions.length" class="ai-chat-quick">
          <el-tag
            v-for="(q, i) in quickQuestions"
            :key="i"
            size="mini"
            effect="plain"
            class="ai-quick-tag"
            @click="send(q)"
          >{{ q }}</el-tag>
        </div>

        <div class="ai-chat-input">
          <el-input v-model="input" placeholder="请输入您的问题" :disabled="loading" @keyup.enter.native="send()">
            <el-button slot="append" icon="el-icon-s-promotion" :loading="loading" @click="send()" />
          </el-input>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { askQuestion } from '@/api/biz/chat'
import { renderAiText } from '@/utils/aiMarkdown'

export default {
  name: 'AiChat',
  props: {
    title: { type: String, default: '智能客服' },
    quickQuestions: { type: Array, default: () => [] }
  },
  data() {
    return {
      visible: false,
      input: '',
      loading: false,
      contextId: null,
      messages: [
        { role: 'ai', content: '您好，我是智能客服，可以帮您解答订单、图书相关的常见问题。' }
      ]
    }
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
      this.messages.push({ role: 'user', content: q })
      this.input = ''
      this.loading = true
      this.scrollToBottom()
      askQuestion({ userId: this.userId, question: q, contextId: this.contextId || undefined })
        .then(res => {
          // request 响应拦截器返回整个 AjaxResult，业务对象在 res.data
          const dto = (res && res.data) || {}
          // 保存后端生成的 contextId，下一轮带上，浮动客服也具备多轮能力
          if (dto.contextId) {
            this.contextId = dto.contextId
          }
          this.messages.push({
            role: 'ai',
            content: dto.answer || '暂无回复',
            intent: dto.intent,
            related: dto.relatedKnowledges
          })
        })
        .catch(() => {
          this.messages.push({ role: 'ai', content: '服务暂时不可用，请稍后再试。' })
        })
        .finally(() => {
          this.loading = false
          this.scrollToBottom()
        })
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
.ai-chat-fab {
  position: fixed;
  right: 28px;
  bottom: 40px;
  z-index: 2000;
  width: 50px;
  height: 50px;
  font-size: 20px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, .18);
}
.ai-chat-body {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 80px);
  padding: 0 16px 16px;
}
.ai-chat-msgs {
  flex: 1;
  overflow-y: auto;
  padding: 8px 4px;
}
.ai-msg {
  margin-bottom: 14px;
}
.ai-msg-content {
  display: inline-block;
  max-width: 88%;
  padding: 9px 12px;
  border-radius: 8px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
}
.ai-msg.is-ai .ai-msg-content {
  background: #f4f4f5;
  color: #303133;
}
.ai-msg.is-user {
  text-align: right;
}
.ai-msg.is-user .ai-msg-content {
  background: #409eff;
  color: #fff;
}
.ai-msg-meta {
  margin-top: 5px;
  font-size: 11px;
  color: #909399;
}
/* AI 回复结构化渲染；v-html 节点没有 scoped 属性，需要 ::v-deep 穿透 */
.ai-md {
  white-space: normal;
  text-align: left;
}
.ai-md ::v-deep .ai-md-p {
  margin: 0;
}
.ai-md ::v-deep .ai-md-gap {
  height: 5px;
}
.ai-md ::v-deep .ai-md-list {
  margin: 4px 0 0;
  padding-left: 18px;
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
.ai-chat-quick {
  padding: 8px 0;
}
.ai-quick-tag {
  margin: 0 6px 6px 0;
  cursor: pointer;
}
.ai-chat-input {
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
}
</style>
