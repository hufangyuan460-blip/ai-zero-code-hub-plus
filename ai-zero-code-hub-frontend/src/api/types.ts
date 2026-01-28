export type BaseResponse<T> = {
  code: number
  data: T
  message: string
}

export type Page<T> = {
  pageNumber: number
  pageSize: number
  totalRow: number
  records: T[]
  [key: string]: unknown
}

export type LoginUserVO = {
  id: number
  userAccount: string
  userName: string
  userAvatar?: string
  userProfile?: string
  userRole: string
  createTime?: string
  [key: string]: unknown
}

export type UserVO = {
  id: number
  userAccount: string
  userName: string
  userAvatar?: string
  userProfile?: string
  userRole: string
  createTime?: string
  [key: string]: unknown
}

export type UserRegisterRequest = {
  userAccount: string
  userPassword: string
  checkPassword: string
}

export type UserLoginRequest = {
  userAccount: string
  userPassword: string
}

export type UserAddRequest = {
  userAccount: string
  userPassword: string
  userName?: string
  userAvatar?: string
  userProfile?: string
  userRole?: string
}

export type UserUpdateRequest = {
  id: number
  userName?: string
  userPassword?: string
  userAvatar?: string
  userProfile?: string
  userRole?: string
}

export type UserQueryRequest = {
  id?: number
  userAccount?: string
  userName?: string
  userProfile?: string
  userRole?: string
  pageNumber?: number
  pageSize?: number
  sortField?: string
  sortOrder?: string
}

