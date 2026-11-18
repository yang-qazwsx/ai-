import request from '@/utils/request'

// 智能客服问答：网关 /system/** -> ruoyi-system（StripPrefix=1）
// 后端 Controller 路径为 /api/v1/chat/question
// 普通问题会走大模型，正常响应约 10~20s，必须单独放大超时（全局是 10s，否则必现超时失败）
export function askQuestion(data) {
  return request({
    url: '/system/api/v1/chat/question',
    method: 'post',
    data: data,
    timeout: 60000
  })
}

// 分页查询历史对话记录：后端返回 TableDataInfo，响应拦截器原样返回 res.data，
// 业务数据在 res.rows、总数在 res.total
export function getChatRecords(params) {
  return request({
    url: '/system/api/v1/chat/records',
    method: 'get',
    params: params,
    timeout: 60000
  })
}

// 查询热门问题：从 Redis 读取热度排行 TopN（问题/答案/热度），用于替换原写死的「常见问题」面板
export function getHotQuestions(params) {
  return request({
    url: '/system/api/v1/chat/hot-questions',
    method: 'get',
    params: params,
    timeout: 10000
  })
}

// 清空指定会话的多轮上下文（只删 Redis 历史，不删数据库聊天记录）
export function clearChatHistory(contextId) {
  return request({
    url: '/system/api/v1/chat/history/' + contextId,
    method: 'delete',
    timeout: 10000
  })
}

