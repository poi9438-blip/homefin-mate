import React from 'react'
import { Navigate } from 'react-router-dom'
import { isAuthenticated } from '../utils/auth'

// 토큰이 없거나 만료됐으면 로그인 화면으로 보내는 보호 라우트 (#77, 서명 확인은 서버)
export default function ProtectedRoute({ children }) {
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />
  }
  return children
}
