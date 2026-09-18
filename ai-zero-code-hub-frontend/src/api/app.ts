import { request } from './request'
import { unwrapBaseResponse } from './baseResponse'
import type { Page } from './types'

// Types
export interface AppVO {
  id: string
  appName: string
  cover?: string
  initPrompt?: string
  codeGenType?: string
  deployKey?: string
  deployedTime?: string
  priority?: number
  userId?: number
  createTime?: string
  updateTime?: string
  editTime?: string
}

export interface AppCreateRequest {
  initPrompt: string
  appName?: string
  cover?: string
  codeGenType?: string
}

export interface AppUpdateMyRequest {
  id: string
  appName?: string
}

export interface AppMyQueryRequest {
  pageNumber?: number
  pageSize?: number
  appName?: string
  sortField?: string
  sortOrder?: string
}

export interface AppFeaturedQueryRequest {
  pageNumber?: number
  pageSize?: number
  appName?: string
}

export interface AppAdminUpdateRequest {
  id: string
  appName?: string
  cover?: string
  priority?: number
}

export interface AppAdminQueryRequest {
  pageNumber?: number
  pageSize?: number
  id?: string
  appName?: string
  cover?: string
  initPrompt?: string
  codeGenType?: string
  deployKey?: string
  priority?: number
  userId?: number
  sortField?: string
  sortOrder?: string
}

export interface AppDeployRequest {
  appId: string
}

export type ExecutionMode = 'DIRECT' | 'WORKFLOW'
export type GenerationRunStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'TIMED_OUT'

export interface GenerationCreateRequest {
  appId: string | number
  userMessage: string
  codeGenType?: string
  executionMode?: ExecutionMode
}

export interface GenerationCreateResponse {
  runId: string
  status: GenerationRunStatus
}

export interface GenerationRunStatusResponse {
  runId: string
  appId: string | number
  userId: string | number
  executionMode: ExecutionMode
  status: GenerationRunStatus
  currentStep?: string
  startedAt?: string
  updatedAt?: string
  finishedAt?: string
  errorMessage?: string
  retryCount?: number
  maxRetryCount?: number
  cancelRequested?: boolean
  validationFingerprint?: string | null
  artifactHash?: string | null
  changedFiles?: string[]
  validationIssueCount?: number
  llmCallCount?: number
  toolCallCount?: number
  buildCallCount?: number
  repairAttempt?: number
  maxRepairAttempts?: number
}

// API Methods

/**
 * Create App
 */
export const createApp = async (data: AppCreateRequest) => {
  const res = await request.post<any>('/app/create', data)
  // json-bigint will parse the response, so we need to handle the potential string/BigInt/number type
  // Since we use storeAsString: true, it should be a string
  return unwrapBaseResponse<string>(res)
}

/**
 * Capture screenshot and upload to COS, return cover URL
 */
export const captureAndUploadScreenshot = async (appId: string | number, webUrl: string) => {
  const res = await request.post<any>('/app/screenshot', { appId, webUrl })
  return unwrapBaseResponse<string>(res)
}

/**
 * Update My App
 */
export const updateMyApp = async (data: AppUpdateMyRequest) => {
  const res = await request.put<any>('/app/my/update', data)
  return unwrapBaseResponse<boolean>(res)
}

/**
 * Remove My App
 */
export const removeMyApp = async (id: string) => {
  const res = await request.delete<any>(`/app/my/remove/${id}`)
  return unwrapBaseResponse<boolean>(res)
}

/**
 * Get My App Info
 */
export const getMyAppInfo = async (id: string) => {
  const res = await request.get<any>(`/app/my/getInfo/${id}`)
  return unwrapBaseResponse<AppVO>(res)
}

/**
 * Page My Apps
 */
export const listMyAppByPage = async (params: AppMyQueryRequest) => {
  const res = await request.get<any>('/app/my/page', { params })
  return unwrapBaseResponse<Page<AppVO>>(res)
}

/**
 * Page Featured Apps
 */
export const listFeaturedAppByPage = async (params: AppFeaturedQueryRequest) => {
  const res = await request.get<any>('/app/featured/page', { params })
  return unwrapBaseResponse<Page<AppVO>>(res)
}

/**
 * Admin Remove App
 */
export const adminRemoveApp = async (id: string) => {
  const res = await request.delete<any>(`/app/admin/remove/${id}`)
  return unwrapBaseResponse<boolean>(res)
}

/**
 * Admin Update App
 */
export const adminUpdateApp = async (data: AppAdminUpdateRequest) => {
  const res = await request.put<any>('/app/admin/update', data)
  return unwrapBaseResponse<boolean>(res)
}

/**
 * Admin Get App Info
 */
export const adminGetAppInfo = async (id: string) => {
  const res = await request.get<any>(`/app/admin/getInfo/${id}`)
  return unwrapBaseResponse<AppVO>(res)
}

/**
 * Admin Page Apps
 */
export const adminListAppByPage = async (params: AppAdminQueryRequest) => {
  const res = await request.get<any>('/app/admin/page', { params })
  return unwrapBaseResponse<Page<AppVO>>(res)
}

/**
 * Deploy App
 */
export const deployApp = async (data: AppDeployRequest) => {
  const res = await request.post<any>('/app/deploy', data)
  return unwrapBaseResponse<string>(res)
}

/**
 * Get Download Link
 */
export const getDownloadLink = async (appId: string | number) => {
  const res = await request.get<any>(`/app/download/link/${appId}`)
  return unwrapBaseResponse<string>(res)
}

/** Create a server-side generation run. The server assigns runId. */
export const createGeneration = async (data: GenerationCreateRequest) => {
  const res = await request.post<any>('/app/generation', data)
  return unwrapBaseResponse<GenerationCreateResponse>(res)
}

/** Query a run without re-executing it. */
export const getGeneration = async (runId: string) => {
  const res = await request.get<any>(`/app/generation/${encodeURIComponent(runId)}`)
  return unwrapBaseResponse<GenerationRunStatusResponse>(res)
}

/** SSE endpoint for a previously created run. */
export const generationStreamUrl = (runId: string, afterSequence = 0) => {
  const suffix = afterSequence > 0 ? `?afterSequence=${encodeURIComponent(String(afterSequence))}` : ''
  return `/api/app/generation/stream/${encodeURIComponent(runId)}${suffix}`
}

/**
 * Request cooperative cancellation of a server-side generation run.
 */
export const cancelGeneration = async (runId: string) => {
  const res = await request.post<any>(`/app/generation/cancel/${encodeURIComponent(runId)}`)
  return unwrapBaseResponse<boolean>(res)
}
