import React from 'react'

// 진행 단계 표시 (완료 / 현재 / 남음)
export default function StepGuide({ steps = [], currentStep = 0 }) {
  return (
    <div className="flex items-center gap-2">
      {steps.map((step, index) => (
        <React.Fragment key={index}>
          <div className="flex flex-col items-center gap-1">
            <div
              className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-semibold
                ${index < currentStep ? 'bg-green-500 text-white' : ''}
                ${index === currentStep ? 'bg-primary-600 text-white' : ''}
                ${index > currentStep ? 'bg-gray-200 dark:bg-gray-600 text-gray-500' : ''}
              `}
            >
              {index < currentStep ? '✓' : index + 1}
            </div>
            <span className="text-xs text-gray-500 dark:text-gray-400 max-w-16 text-center">{step}</span>
          </div>
          {index < steps.length - 1 && (
            <div className={`flex-1 h-0.5 mb-5 ${index < currentStep ? 'bg-green-500' : 'bg-gray-200 dark:bg-gray-600'}`} />
          )}
        </React.Fragment>
      ))}
    </div>
  )
}
