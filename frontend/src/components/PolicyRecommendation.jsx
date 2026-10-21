import React from 'react'

// 상품 종합 판정 배지 (입력한 정보 기준)
const ELIGIBILITY = {
  ELIGIBLE:     { label: '조건 충족', color: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' },
  NEEDS_CHECK:  { label: '확인 필요', color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200' },
  NOT_ELIGIBLE: { label: '조건 미충족', color: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300' },
}

export function EligibilityBadge({ eligibility }) {
  const e = ELIGIBILITY[eligibility] || ELIGIBILITY.NEEDS_CHECK
  return <span className={`px-2 py-0.5 rounded-full text-xs font-semibold whitespace-nowrap ${e.color}`}>{e.label}</span>
}

const formatWon = n => (n == null ? '-' : `${Math.floor(Number(n)).toLocaleString('ko-KR')}원`)

// 둘 중 추천 카드 (추천 상품의 한도,금리,이유, 전체 상품 판정 배지, 상세 보기 버튼)
export function RecommendationCard({ policy, onDetail }) {
  if (!policy) return null
  const recommended = policy.recommendedPolicies?.find(p => p.name === policy.recommendedProduct)

  return (
    <div className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-sm">
      <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-3">정책대출 추천</h3>

      {recommended ? (
        <div className="rounded-lg bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 p-4 mb-3">
          <div className="flex items-center justify-between gap-2 mb-1">
            <span className="font-semibold text-blue-900 dark:text-blue-100">{recommended.name}</span>
            <span className="text-xs text-blue-700 dark:text-blue-300">{recommended.provider}</span>
          </div>
          <div className="text-xs text-blue-800 dark:text-blue-200">
            입력 기준 최대 한도 <b>{formatWon(recommended.maxLoanAmount)}</b> · {recommended.interestRate}
          </div>
        </div>
      ) : (
        <div className="rounded-lg bg-gray-50 dark:bg-gray-700/40 border border-gray-200 dark:border-gray-600 p-4 mb-3 text-sm text-gray-700 dark:text-gray-200">
          추천할 수 있는 상품이 없습니다.
        </div>
      )}

      <p className="text-sm text-gray-700 dark:text-gray-300 mb-3">{policy.recommendationReason}</p>

      <ul className="flex flex-col gap-1 mb-3">
        {policy.recommendedPolicies?.map(p => (
          <li key={p.name} className="flex items-center justify-between text-xs text-gray-600 dark:text-gray-300">
            <span>{p.name}</span>
            <EligibilityBadge eligibility={p.eligibility} />
          </li>
        ))}
      </ul>

      <p className="text-xs text-gray-500 dark:text-gray-400 mb-3">
        입력한 정보로 판단한 결과입니다. 최종 자격과 한도는 각 기관 심사에서 결정됩니다.
      </p>

      {onDetail && (
        <button type="button" onClick={onDetail}
          className="w-fit px-4 py-2 text-sm text-primary-700 dark:text-primary-300 border border-primary-300 dark:border-primary-600 rounded-lg hover:bg-primary-50 dark:hover:bg-gray-700">
          조건별 판정·근거 자세히 보기
        </button>
      )}
    </div>
  )
}
