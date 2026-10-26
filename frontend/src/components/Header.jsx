import React from 'react'
import ThemeToggle from './ThemeToggle'
import LargeTextToggle from './LargeTextToggle'
import { clearSession } from '../utils/auth'
import { authApi } from '../api/authApi'
import { useNavigate } from 'react-router-dom'

// 상단 헤더: 서비스 이름, 로그아웃 버튼
export default function Header() {
  const navigate = useNavigate()
  

  // 로그아웃: 서버가 쿠키 삭제 → 화면 상태 정리 → 로그인 화면
  const handleLogout = async () => {
    try {
      await authApi.logout()
    } catch {
      // 이미 만료됐거나 연결 실패여도 화면은 로그아웃 처리
    }
    clearSession()
    navigate('/login')
  }

  return (
    <header className="flex items-center justify-between px-6 py-4 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 shadow-xs">
      <div className="flex items-center gap-2">
        <span className="text-xl font-bold text-primary-600 dark:text-primary-400">HomeFin Mate</span>
      </div>
      <div className="flex items-center gap-4">
        {/* 큰 글씨, 테마 전환 버튼 */}
        <LargeTextToggle />
        <ThemeToggle />
        <button
          onClick={handleLogout}
          className="text-sm text-gray-600 dark:text-gray-300 hover:text-red-500 transition-colors"
        >
          로그아웃
        </button>
      </div>
    </header>
  )
}
