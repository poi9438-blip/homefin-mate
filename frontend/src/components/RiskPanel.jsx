import React from 'react'

// 위험 평가 패널 (판정·근거 / 이용 불가 안내 / 판정하지 않은 이유)
const VERDICT = {
  ELIGIBLE: { label: '적격', color: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' },
  REVIEW: { label: '검토 필요', color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200' },
  INELIGIBLE: { label: '부적격', color: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200' },
}
const RESULT = { PASS: '충족', WARN: '확인 필요', FAIL: '미충족' }
const PRODUCT = {
  DIDIMDOL: '내집마련 디딤돌대출',
  NEWBORN_DIDIMDOL: '신생아 특례 디딤돌대출',
  BOGEUMJARI: '보금자리론',
}

export default function RiskPanel({ risk }) {
  if (!risk) return null

  // 입력이 모자라 판정하지 않음: 서버가 보낸 한국어 이유를 그대로 (장애 안내가 아님)
  if (risk.status === 'NOT_EVALUATED') {
    return (
      <div className="bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5">
        <span className="text-gray-700 dark:text-gray-200 font-semibold text-sm">심사 기준 판정 안 함</span>
        <p className="text-sm text-gray-600 dark:text-gray-300 mt-1">{risk.recommendation}</p>
      </div>
    )
  }

  // RiskWatch 이용 불가 (서버의 영어 fallback 문구는 보여 주지 않음)
  if (!risk.available) {
    return (
      <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-700 rounded-xl p-5">
        <span className="text-yellow-600 dark:text-yellow-400 font-semibold text-sm">리스크 평가 불가</span>
        <p className="text-sm text-yellow-700 dark:text-yellow-300 mt-1">
          RiskWatch 서비스가 일시적으로 이용 불가합니다. 대출 계산 결과는 유효합니다.
        </p>
      </div>
    )
  }

  const verdict = VERDICT[risk.verdict]
  return (
    <div className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-xs">
      <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-1">심사 기준 판정</h3>
      <p className="text-xs text-gray-500 dark:text-gray-400 mb-3">
        {PRODUCT[risk.productCode] ?? risk.productCode} · 기준 버전 {risk.criteriaVersion}
      </p>
      <div className="flex flex-wrap gap-3 mb-3">
        <span className={`px-3 py-1 rounded-full text-sm font-medium ${verdict?.color ?? 'bg-gray-100 text-gray-700'}`}>
          {verdict?.label ?? risk.verdict}
        </span>
        {risk.riskScore > 0 && (
          <span className="px-3 py-1 rounded-full text-sm font-medium bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-200">
            점수: {risk.riskScore}
          </span>
        )}
      </div>
      {risk.recommendation && <p className="text-sm text-gray-700 dark:text-gray-300 mb-3">{risk.recommendation}</p>}
      {risk.reasons?.length > 0 && (
        <ul className="text-sm divide-y divide-gray-100 dark:divide-gray-700 mb-3">
          {risk.reasons.map((r) => (
            <li key={r.code} className="py-2">
              <span className="font-medium text-gray-800 dark:text-gray-100">{r.name}</span>
              <span className="ml-2 text-xs text-gray-500 dark:text-gray-400">{RESULT[r.result] ?? r.result}</span>
              {r.detail && <p className="text-xs text-gray-600 dark:text-gray-300 mt-0.5">{r.detail}</p>}
            </li>
          ))}
        </ul>
      )}
      {risk.notEvaluated?.length > 0 && (
        <p className="text-xs text-gray-500 dark:text-gray-400">
          판정하지 않은 기준: {risk.notEvaluated.join(', ')} (신청 기관 심사에서 확인)
        </p>
      )}
    </div>
  )
}
