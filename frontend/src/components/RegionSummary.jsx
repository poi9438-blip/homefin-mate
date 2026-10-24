import React from 'react'

// 지역 분석 결과 표시 (대출 결과·대시보드 공통)
const UNAVAILABLE_MESSAGE = {
  UNSUPPORTED_REGION: '이 지역은 지역 분석 데이터가 없습니다.',
  SERVICE_UNAVAILABLE: '지역 분석 서비스를 일시적으로 이용할 수 없습니다. 대출 및 리스크 결과에는 영향이 없습니다.',
}

export default function RegionSummary({ data }) {
  if (!data) return null
  if (data.available === false) {
    return (
      <p className="text-sm text-gray-500 dark:text-gray-400">
        {UNAVAILABLE_MESSAGE[data.unavailableReason] ?? UNAVAILABLE_MESSAGE.SERVICE_UNAVAILABLE}
      </p>
    )
  }
  return (
    <div>
      {data.sample !== false && (
        <span className="inline-block mb-3 px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200">
          예시 데이터 · 실제 시세 아님
        </span>
      )}
      <div className="grid grid-cols-3 gap-4">
        <div>
          <p className="text-xs text-gray-500 dark:text-gray-400">평균 가격 (단위 모름)</p>
          <p className="text-sm font-semibold text-gray-900 dark:text-white">{Number(data.averagePrice).toLocaleString()}</p>
        </div>
        <div>
          <p className="text-xs text-gray-500 dark:text-gray-400">성장률</p>
          <p className="text-sm font-semibold text-gray-900 dark:text-white">{(Number(data.growthRate) * 100).toFixed(1)}%</p>
        </div>
        <div>
          <p className="text-xs text-gray-500 dark:text-gray-400">시장 리스크</p>
          <p className="text-sm font-semibold text-gray-900 dark:text-white">{Number(data.riskScore).toFixed(1)}</p>
        </div>
      </div>
    </div>
  )
}
