import React, { useEffect, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { authApi } from '../api/authApi'

// 사이드바 메뉴 목록 (주소, 이름)
const navItems = [
  { to: '/dashboard', label: '대시보드' },
  { to: '/loan',      label: '대출 시뮬레이터' },
  { to: '/policy',    label: '정책 추천' },
  { to: '/report',    label: '재무 리포트' },
  { to: '/admin',     label: '관리자', adminOnly: true },
]

// 사이드바 (관리자 메뉴는 ADMIN에게만)
export default function Sidebar() {
  const [isAdmin, setIsAdmin] = useState(false)

  useEffect(() => {
    let cancelled = false
    authApi.me()
      .then(res => { if (!cancelled) setIsAdmin(res.data.data?.role === 'ADMIN') })
      .catch(() => { if (!cancelled) setIsAdmin(false) })
    return () => { cancelled = true }
  }, [])

  return (
    // 작은 화면: 헤더 아래 가로 스크롤 메뉴 / md 이상: 왼쪽 세로 메뉴
    <aside className="shrink-0 flex md:flex-col md:w-56 gap-1 px-3 py-2 md:py-6 overflow-x-auto bg-white dark:bg-gray-800 border-b md:border-b-0 md:border-r border-gray-200 dark:border-gray-700">
      {navItems.filter(item => !item.adminOnly || isAdmin).map(({ to, label }) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) =>
            `whitespace-nowrap rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
              isActive
                ? 'bg-primary-50 text-primary-700 dark:bg-primary-900 dark:text-primary-300'
                : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
            }`
          }
        >
          {label}
        </NavLink>
      ))}
    </aside>
  )
}
