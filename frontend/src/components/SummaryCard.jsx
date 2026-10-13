import React from 'react'

// 요약 카드 (제목, 값, 부가 설명, 아이콘, 증감)
export default function SummaryCard({ title, value, subtitle, icon, trend }) {
  return (
    <div className="bg-white dark:bg-gray-800 rounded-xl p-5 shadow-sm border border-gray-100 dark:border-gray-700">
      <div className="flex items-start justify-between">
        <div className="flex flex-col gap-1">
          <span className="text-sm text-gray-500 dark:text-gray-400">{title}</span>
          <span className="text-2xl font-bold text-gray-900 dark:text-white">{value ?? '—'}</span>
          {subtitle && <span className="text-xs text-gray-400">{subtitle}</span>}
        </div>
        {icon && <span className="text-2xl">{icon}</span>}
      </div>
      {trend !== undefined && (
        <div className={`mt-3 text-xs font-medium ${trend >= 0 ? 'text-green-500' : 'text-red-500'}`}>
          {trend >= 0 ? '▲' : '▼'} {Math.abs(trend)}%
        </div>
      )}
    </div>
  )
}
