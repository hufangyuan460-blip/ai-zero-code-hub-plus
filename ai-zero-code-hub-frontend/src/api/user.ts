import { request } from './request'
import { unwrapBaseResponse } from './baseResponse'
import type {
  LoginUserVO,
  Page,
  UserAddRequest,
  UserLoginRequest,
  UserQueryRequest,
  UserRegisterRequest,
  UserUpdateRequest,
  UserVO,
} from './types'

export const userApi = {
  register: async (data: UserRegisterRequest) => {
    const res = await request.post('/user/register', data)
    return unwrapBaseResponse<number>(res)
  },

  login: async (data: UserLoginRequest) => {
    const res = await request.post('/user/login', data)
    return unwrapBaseResponse<LoginUserVO>(res)
  },

  logout: async () => {
    const res = await request.post('/user/logout')
    return unwrapBaseResponse<boolean>(res)
  },

  getCurrentUser: async () => {
    const res = await request.get('/user/get/currentUser')
    return unwrapBaseResponse<LoginUserVO>(res)
  },

  adminAdd: async (data: UserAddRequest) => {
    const res = await request.post('/user/save', data)
    return unwrapBaseResponse<number>(res)
  },

  adminUpdate: async (data: UserUpdateRequest) => {
    const res = await request.put('/user/update', data)
    return unwrapBaseResponse<boolean>(res)
  },

  adminRemove: async (id: number) => {
    const res = await request.delete(`/user/remove/${id}`)
    return unwrapBaseResponse<boolean>(res)
  },

  adminGetInfo: async (id: number) => {
    const res = await request.get(`/user/getInfo/${id}`)
    return unwrapBaseResponse<UserVO>(res)
  },

  adminList: async (params?: UserQueryRequest) => {
    const res = await request.get('/user/list', { params })
    return unwrapBaseResponse<UserVO[]>(res)
  },

  adminPage: async (params?: UserQueryRequest) => {
    const res = await request.get('/user/page', { params })
    return unwrapBaseResponse<Page<UserVO>>(res)
  },
}

