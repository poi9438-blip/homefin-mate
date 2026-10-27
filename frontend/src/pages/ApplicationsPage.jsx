import React, { useCallback, useEffect, useState } from 'react'
import { applicationApi } from '../api/applicationApi'
import LoadingState from '../components/LoadingState'
import ErrorState from '../components/ErrorState'
import { errorMessage } from '../utils/errorMessage'

// 내 신청 화면 (상태는 FinFlow 값, 접수·심사 중이면 취소 가능)
const STATUS = {
  RECEIVED: '접수',
  REVIEWING: '심사 중',
  APPROVED: '승인',
  REJECTED: '거절',
  CANCELLED: '취소',
  EXPIRED: '만료',
}
const CANCELLABLE = ['RECEIVED', 'REVIEWING']
const PRODUCT = {
  DIDIMDOL: '내집마련 디딤돌대출',
  NEWBORN_DIDIMDOL: '신생아 특례 디딤돌대출',
  BOGEUMJARI: '보금자리론',
}

const dateTime = (value) => (value ? new Date(value).toLocaleString('ko-KR') : '—')

export default function ApplicationsPage() {
  const [items, setItems] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setError('')
    try {
      const res = await applicationApi.list()
      setItems(res.data.data)
    } catch (err) {
      setError(errorMessage(err, '신청 목록을 불러오지 못했습니다.'))
    }
  }, [])

  useEffect(() => { load() }, [load])

  const cancel = async (applicationId) => {
    if (!window.confirm('이 신청을 취소할까요?')) return
    try {
      await applicationApi.cancel(applicationId)
      await load()
    } catch (err) {
      setError(errorMessage(err, '취소하지 못했습니다.'))
    }
  }

  if (error && !items) return <ErrorState message={error} onRetry={load} />
  if (!items) return <LoadingState message="신청 목록을 불러오는 중…" />

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-bold text-gray-900 dark:text-white">내 신청</h2>
      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
      {items.length === 0 ? (
        <p className="text-sm text-gray-600 dark:text-gray-300">아직 신청한 대출이 없습니다. 대출 시뮬레이터의 추천 상품에서 신청할 수 있습니다.</p>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-gray-500 dark:text-gray-400">
              <th className="py-2">상품</th><th>상태</th><th>금액</th><th>기간</th><th>접수</th><th>결정</th><th></th>
            </tr>
          </thead>
          <tbody>
            {items.map((a) => (
              <tr key={a.applicationId} className="border-t border-gray-100 dark:border-gray-700">
                <td className="py-2">{PRODUCT[a.productCode] ?? a.productCode}</td>
                {a.available ? (
                  <>
                    <td>{STATUS[a.status] ?? a.status}{a.rejectReason && <span className="block text-xs text-gray-500">{a.rejectReason}</span>}</td>
                    <td>{a.requestedAmount?.toLocaleString()}원</td>
                    <td>{a.termMonths}개월</td>
                    <td>{dateTime(a.receivedAt)}</td>
                    <td>{dateTime(a.decidedAt)}</td>
                    <td>
                      {CANCELLABLE.includes(a.status) && (
                        <button onClick={() => cancel(a.applicationId)} className="text-xs text-red-600 underline">취소</button>
                      )}
                    </td>
                  </>
                ) : (
                  <td colSpan={6} className="text-gray-500">상태 확인 불가 (잠시 후 다시 확인해 주세요)</td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
