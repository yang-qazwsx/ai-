import request from '@/utils/request'

// 查询图书列表
export function listBook(query) {
  return request({ url: '/system/biz/book/list', method: 'get', params: query })
}

// 查询图书详细
export function getBook(bookId) {
  return request({ url: '/system/biz/book/' + bookId, method: 'get' })
}

// 新增图书
export function addBook(data) {
  return request({ url: '/system/biz/book', method: 'post', data: data })
}

// 修改图书
export function updateBook(data) {
  return request({ url: '/system/biz/book', method: 'put', data: data })
}

// 删除图书
export function delBook(bookId) {
  return request({ url: '/system/biz/book/' + bookId, method: 'delete' })
}

// 查询书籍内容解析状态
export function getBookContent(bookId) {
  return request({ url: '/system/api/v1/book-content/' + bookId, method: 'get' })
}

// 阅读目录（文件元信息 + 章节目录）
export function getBookOutline(bookId) {
  return request({ url: '/system/api/v1/book-content/' + bookId + '/outline', method: 'get' })
}

// 按页读取正文，chunkNo 即页码
export function readBookChunk(bookId, chunkNo) {
  return request({
    url: '/system/api/v1/book-content/' + bookId + '/read',
    method: 'get',
    params: { chunkNo: chunkNo }
  })
}

// 原文件流（PDF 预览用），返回 Blob
export function getBookFileBlob(bookId) {
  return request({
    url: '/system/api/v1/book-content/' + bookId + '/file',
    method: 'get',
    responseType: 'blob',
    timeout: 60000
  })
}
