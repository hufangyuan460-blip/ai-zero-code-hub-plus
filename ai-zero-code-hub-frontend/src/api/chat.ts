import { request } from './request'
import { unwrapBaseResponse } from './baseResponse'
import type { Page } from './types'

export interface ChatHistoryVO {
  id: string
  appId: string
  userId: string
  messageType: string
  content: string
  createTime: string
  updateTime?: string
}

export interface ChatHistoryQueryRequest {
  pageNumber?: number
  pageSize?: number
  appId?: string
  userId?: string
  messageType?: string
  lastCreateTime?: string
}

/**
 * List Chat History (Cursor/Page)
 */
export const listChatHistoryByPage = async (data: ChatHistoryQueryRequest) => {
  const res = await request.post<any>('/chat/history/list/page', data)
  return unwrapBaseResponse<Page<ChatHistoryVO>>(res)
}

/**
 * Admin List All Chat History
 */
export const adminListChatHistoryByPage = async (data: ChatHistoryQueryRequest) => {
  const res = await request.post<any>('/chat/history/admin/list/page', data)
  return unwrapBaseResponse<Page<ChatHistoryVO>>(res)
}
