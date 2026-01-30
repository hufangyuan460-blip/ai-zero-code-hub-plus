import { request } from './request'
import type { Page } from './types'

// Types
export interface AppVO {
  id: number
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
  id: number
  appName?: string
}

export interface AppMyQueryRequest {
  current?: number
  pageSize?: number
  appName?: string
  sortField?: string
  sortOrder?: string
}

export interface AppFeaturedQueryRequest {
  current?: number
  pageSize?: number
  appName?: string
}

export interface AppAdminUpdateRequest {
  id: number
  appName?: string
  cover?: string
  priority?: number
}

export interface AppAdminQueryRequest {
  current?: number
  pageSize?: number
  appName?: string
  appStatus?: number
  appType?: number
  userId?: number
  sortField?: string
  sortOrder?: string
}

export interface AppDeployRequest {
  appId: number
}

// API Methods

/**
 * Create App
 */
export const createApp = (data: AppCreateRequest) => {
  return request.post<any, number>('/app/create', data)
}

/**
 * Update My App
 */
export const updateMyApp = (data: AppUpdateMyRequest) => {
  return request.put<any, boolean>('/app/my/update', data)
}

/**
 * Remove My App
 */
export const removeMyApp = (id: number) => {
  return request.delete<any, boolean>(`/app/my/remove/${id}`)
}

/**
 * Get My App Info
 */
export const getMyAppInfo = (id: number) => {
  return request.get<any, AppVO>(`/app/my/getInfo/${id}`)
}

/**
 * Page My Apps
 */
export const listMyAppByPage = (params: AppMyQueryRequest) => {
  return request.get<any, Page<AppVO>>('/app/my/page', { params })
}

/**
 * Page Featured Apps
 */
export const listFeaturedAppByPage = (params: AppFeaturedQueryRequest) => {
  return request.get<any, Page<AppVO>>('/app/featured/page', { params })
}

/**
 * Admin Remove App
 */
export const adminRemoveApp = (id: number) => {
  return request.delete<any, boolean>(`/app/admin/remove/${id}`)
}

/**
 * Admin Update App
 */
export const adminUpdateApp = (data: AppAdminUpdateRequest) => {
  return request.put<any, boolean>('/app/admin/update', data)
}

/**
 * Admin Get App Info
 */
export const adminGetAppInfo = (id: number) => {
  return request.get<any, AppVO>(`/app/admin/getInfo/${id}`)
}

/**
 * Admin Page Apps
 */
export const adminListAppByPage = (params: AppAdminQueryRequest) => {
  return request.get<any, Page<AppVO>>('/app/admin/page', { params })
}

/**
 * Deploy App
 */
export const deployApp = (data: AppDeployRequest) => {
  return request.post<any, string>('/app/deploy', data)
}
