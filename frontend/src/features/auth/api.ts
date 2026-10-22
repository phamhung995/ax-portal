import { api, request } from '@/lib/api'
import type { LoginResponse } from './types'

export interface OtpRequestPayload {
  email: string
}

export interface OtpVerifyPayload {
  email: string
  otp: string
}

export interface RegisterPayload {
  name: string
  email: string
  password: string
  confirmPassword: string
}

export interface ResetPasswordPayload {
  email: string
  newPassword: string
  confirmPassword: string
}

export const authApi = {
  login: (email: string, password: string) =>
    request<LoginResponse>(api.post('/auth/login', { email, password })),
  requestRegisterOtp: (email: string) =>
    request<void>(api.post('/auth/register/request-otp', { email })),
  verifyRegisterOtp: (email: string, otp: string) =>
    request<void>(api.post('/auth/register/verify-otp', { email, otp })),
  register: (payload: RegisterPayload) =>
    request<void>(api.post('/auth/register', payload)),
  requestForgotOtp: (email: string) =>
    request<void>(api.post('/auth/forgot-password/request-otp', { email })),
  verifyForgotOtp: (email: string, otp: string) =>
    request<void>(api.post('/auth/forgot-password/verify-otp', { email, otp })),
  resetPassword: (payload: ResetPasswordPayload) =>
    request<void>(api.post('/auth/reset-password', payload)),
  logout: () => request<void>(api.post('/auth/logout')),
}
