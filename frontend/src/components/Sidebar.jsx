import React from 'react'
import { NavLink } from 'react-router-dom'

// 사이드바 메뉴 목록 (주소, 이름)
const navItems = [
  { to: '/dashboard', label: '대시보드' },
  { to: '/loan',      label: '대출 시뮬레이터' },
  { to: '/policy',    label: '정책 추천' },
  { to: '/report',    label: '재무 리포트' },
  { to: '/admin',     label: '관리자' },
]

export default function Sidebar() {
  return (
    <aside className="w-56 bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 flex flex-col py-6 px-3 gap-1">
      {navItems.map(({ to, label }) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) =>
            `rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
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
