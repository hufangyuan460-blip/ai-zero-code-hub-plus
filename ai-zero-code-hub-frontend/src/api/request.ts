import axios from 'axios'

export const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  withCredentials: true,
  timeout: 30_000,
})

request.interceptors.response.use(
  (response) => response,
  (error) => {
    const message =
      error?.response?.data?.message ??
      error?.response?.data?.error ??
      error?.message ??
      '请求失败'
    return Promise.reject(new Error(message))
  },
)
