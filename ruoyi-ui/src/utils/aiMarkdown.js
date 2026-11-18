/**
 * AI 回复的轻量格式化。
 *
 * 模型回答常见结构是「一句话 + 换行 + - 列表 + 《书名》+ 价格」，
 * 直接以纯文本渲染会把换行和列表全部压成一行，可读性很差。
 * 项目未引入 markdown 依赖，这里做一个最小的安全渲染器：
 * 先整体转义 HTML，再只放开换行、列表、粗体、行内代码与书名号高亮。
 */

const ESCAPE_MAP = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;'
}

function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, c => ESCAPE_MAP[c])
}

function renderInline(text) {
  return text
    .replace(/`([^`]+)`/g, '<code class="ai-md-code">$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/《([^》]{1,50})》/g, '<span class="ai-md-title">《$1》</span>')
}

export function renderAiText(text) {
  if (!text) {
    return ''
  }
  const lines = escapeHtml(text).split(/\r?\n/)
  const html = []
  let inList = false

  const closeList = () => {
    if (inList) {
      html.push('</ul>')
      inList = false
    }
  }

  for (const line of lines) {
    const trimmed = line.trim()
    // 无序列表：- / * / +；有序列表：1. 2.
    const listMatch = /^(?:[-*+]|\d+\.)\s+(.*)$/.exec(trimmed)
    if (listMatch) {
      if (!inList) {
        html.push('<ul class="ai-md-list">')
        inList = true
      }
      html.push('<li>' + renderInline(listMatch[1]) + '</li>')
      continue
    }
    closeList()
    if (!trimmed) {
      html.push('<div class="ai-md-gap"></div>')
    } else {
      html.push('<p class="ai-md-p">' + renderInline(trimmed) + '</p>')
    }
  }
  closeList()
  return html.join('')
}

export default renderAiText
