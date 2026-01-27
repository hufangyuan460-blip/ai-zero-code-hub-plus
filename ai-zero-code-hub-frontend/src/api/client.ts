import { OpenAPI } from './generated'
export { OpenAPI }
export * from './generated'

OpenAPI.BASE = import.meta.env.VITE_API_BASE_URL ?? '/api'
OpenAPI.WITH_CREDENTIALS = true
OpenAPI.WITH_CREDENTIALS = true
