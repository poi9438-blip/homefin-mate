import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { applicationApi } from '../api/applicationApi'
import { errorMessage } from '../utils/errorMessage'

// 추천 상품 신청 버튼 (분석 결과마다 Idempotency-Key 하나, 성공하면 내 신청 링크)
export default function ApplyPanel({ policy, analysis, simulationId }) {
  const [idempotencyKey] = useState(() => crypto.randomUUID())
  const [receipt, setReceipt] = useState(null)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')

  const productCode = policy?.recommendedProductCode
  if (!productCode || !analysis) return null

  const apply = async () => {
    if (!window.confirm(`${policy.recommendedProduct}을(를) 이 입력으로 신청할까요?`)) return
    setSending(true)
    setError('')
    try {
      const res = await applicationApi.apply({ productCode, simulationId: simulationId ?? undefined, analysis }, idempotencyKey)
      setReceipt(res.data.data)
    } catch (err) {
      setError(errorMessage(err, '신청하지 못했습니다. 잠시 후 다시 눌러 주세요.'))
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-xs">
      <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-1">대출 신청</h3>
      <p className="text-sm text-gray-600 dark:text-gray-300 mb-3">
        {policy.recommendedProduct}을(를) 지금 입력한 조건으로 신청합니다. 심사는 신청 처리 시스템(FinFlow)에서 진행됩니다.
      </p>
      {receipt ? (
        <p className="text-sm text-green-700 dark:text-green-300">
          신청이 접수되었습니다 (신청 번호 {receipt.applicationId}).{' '}
          <Link to="/applications" className="underline">내 신청 보기</Link>
        </p>
      ) : (
        <button onClick={apply} disabled={sending}
          className="px-4 py-2 text-sm text-white bg-primary-600 rounded-lg hover:bg-primary-700 disabled:opacity-50">
          {sending ? '신청 중…' : '이 상품으로 신청'}
        </button>
      )}
      {error && <p className="text-sm text-red-600 dark:text-red-400 mt-2">{error}</p>}
    </div>
  )
}
