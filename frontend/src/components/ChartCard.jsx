import React from 'react'

export default function ChartCard({ title, children, className = '' }) {
  return (
    <div className={`bg-white dark:bg-gray-800 rounded-xl p-5 shadow-sm border border-gray-100 dark:border-gray-700 ${className}`}>
      {title && (
        <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-200 mb-4">{title}</h3>
      )}
      <div className="relative">{children}</div>
    </div>
  )
}
