import React from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/Layout'
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import DashboardPage from './pages/DashboardPage'
import LoanPage from './pages/LoanPage'
import PolicyPage from './pages/PolicyPage'
import ReportPage from './pages/ReportPage'
import AdminPage from './pages/AdminPage'
import ProtectedRoute from './components/ProtectedRoute'
import { useTheme } from './hooks/useTheme'
import { useLargeText } from './hooks/useLargeText'

export default function App() {
  // 저장된 테마, 큰 글씨 설정을 전체 화면에 적용
  const { theme } = useTheme()
  const { largeText } = useLargeText()

  return (
    <div className={`${theme === 'dark' ? 'dark' : ''} ${largeText ? 'large-text' : ''}`}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          {/* 로그인이 필요한 화면 (Layout 아래 전체) */}
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }
          >
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="loan" element={<LoanPage />} />
            <Route path="policy" element={<PolicyPage />} />
            <Route path="report" element={<ReportPage />} />
            <Route path="admin" element={<AdminPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </div>
  )
}
