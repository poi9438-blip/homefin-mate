import React from 'react'
import { useLargeText } from '../hooks/useLargeText'

// 큰 글씨 전환 버튼 (A+ / A−)
export default function LargeTextToggle() {
  const { largeText, toggleLargeText } = useLargeText()

  return (
    <button
      onClick={toggleLargeText}
      aria-label="Toggle large text"
      className="p-2 rounded-lg text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors text-sm"
    >
      {largeText ? 'A−' : 'A+'}
    </button>
  )
}
