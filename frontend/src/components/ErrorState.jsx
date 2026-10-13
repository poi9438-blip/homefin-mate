import React from 'react'

// 공통 오류 표시 (메시지 + 선택적 다시 시도 버튼)
export default function ErrorState({ message = '오류가 발생했습니다.', onRetry }) {
  return (
    <div className="flex items-center justify-center py-16">
      <div className="flex flex-col items-center gap-4 text-center">
        <span className="text-3xl">⚠️</span>
        <p className="text-red-500 font-medium">{message}</p>
        {onRetry && (
          <button
            onClick={onRetry}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm hover:bg-primary-700 transition-colors"
          >
            다시 시도
          </button>
        )}
      </div>
    </div>
  )
}
