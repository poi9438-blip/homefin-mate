import React from 'react'
import { Navigate } from 'react-router-dom'
import { isAuthenticated } from '../utils/auth'

// 토큰이 없으면 로그인 화면으로 보내는 보호 라우트 (만료 여부는 서버가 판단)
export default function ProtectedRoute({ children }) {
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />
  }
  return children
}
