import React from 'react'
import { useTheme } from '../hooks/useTheme'

// 다크/라이트 전환 버튼
export default function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()

  return (
    <button
      onClick={toggleTheme}
      aria-label="Toggle theme"
      className="p-2 rounded-lg text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors text-sm"
    >
      {theme === 'dark' ? '☀ Light' : '🌙 Dark'}
    </button>
  )
}
