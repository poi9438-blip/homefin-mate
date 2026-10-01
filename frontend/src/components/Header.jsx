import React from 'react'
import { removeToken } from '../utils/auth'
import { useNavigate } from 'react-router-dom'

// 상단 헤더: 서비스 이름, 로그아웃 버튼
export default function Header() {
  const navigate = useNavigate()
  
  // 로그아웃: 토큰 삭제 후 로그인 화면으로
  const handleLogout = () => {
    removeToken()
    navigate('/login')
  }

  return (
    <header className="flex items-center justify-between px-6 py-4 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 shadow-sm">
      <div className="flex items-center gap-2">
        <span className="text-xl font-bold text-primary-600 dark:text-primary-400">HomeFin Mate</span>
      </div>
      <div className="flex items-center gap-4">
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
