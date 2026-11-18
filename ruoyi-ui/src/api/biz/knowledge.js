import request from '@/utils/request'

// 分页查询知识库条目（支持 category / question / answer 模糊过滤）
export function listKnowledge(query) {
  return request({
    url: '/system/api/v1/knowledge/list',
    method: 'get',
    params: query
  })
}

// 查询知识库条目详情
export function getKnowledge(id) {
  return request({
    url: '/system/api/v1/knowledge/' + id,
    method: 'get'
  })
}

// 新增知识库条目（后端会自动同步向量库）
export function addKnowledge(data) {
  return request({
    url: '/system/api/v1/knowledge',
    method: 'post',
    data: data
  })
}

// 修改知识库条目（后端会重建对应向量）
export function updateKnowledge(data) {
  return request({
    url: '/system/api/v1/knowledge',
    method: 'put',
    data: data
  })
}

// 删除知识库条目（支持批量，ids 用逗号分隔）
export function delKnowledge(ids) {
  return request({
    url: '/system/api/v1/knowledge/' + ids,
    method: 'delete'
  })
}
