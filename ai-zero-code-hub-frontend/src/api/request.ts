import axios from 'axios'
import JSONBig from 'json-bigint'

export const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  withCredentials: true,
  timeout: 30_000,
  transformResponse: [
    (data) => {
      try {
        // use JSONBig to parse large numbers safely
        return JSONBig({ storeAsString: true }).parse(data)
      } catch (e) {
        return data
      }
    },
  ],
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
