import type { AxiosResponse } from 'axios'
import type { BaseResponse } from './types'

export const unwrapBaseResponse = <T>(response: AxiosResponse<BaseResponse<T>>): T => {
  const payload = response.data
  if (!payload) {
    throw new Error('响应为空')
  }
  if (payload.code !== 0) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

