import React, { useState, useEffect } from 'react'
import { adminApi } from '../api/adminApi'
import LoadingState from '../components/LoadingState'
import ErrorState from '../components/ErrorState'
import { errorMessage } from '../utils/errorMessage'

// 관리자 화면 탭
const TABS = ['감사 로그', '로그인 실패', '사용자 활동', '연동 실패']

// 공통 표 (열 정의 + 행 데이터, 비어 있으면 안내 문구)
function Table({ columns, rows, emptyMessage }) {
  if (!rows || rows.length === 0) {
    return <p className="text-sm text-gray-500 dark:text-gray-400 py-4">{emptyMessage}</p>
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm text-left">
        <thead>
          <tr className="border-b border-gray-200 dark:border-gray-700">
            {columns.map(col => (
              <th key={col.key}
                className="pb-2 pr-4 font-medium text-gray-500 dark:text-gray-400 whitespace-nowrap">
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, idx) => (
            <tr key={idx} className="border-b border-gray-100 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/30">
              {columns.map(col => (
                <td key={col.key} className="py-2 pr-4 text-gray-800 dark:text-gray-200 whitespace-nowrap max-w-xs truncate">
                  {col.render ? col.render(row[col.key], row) : (row[col.key] ?? '—')}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

const AUDIT_COLS = [
  { key: 'id',        label: 'ID' },
  { key: 'actorType', label: '행위자 유형' },
  { key: 'actorId',   label: '행위자' },
  { key: 'action',    label: '작업' },
  { key: 'traceId',   label: '트레이스 ID' },
  { key: 'createdAt', label: '시간', render: v => v ? new Date(v).toLocaleString() : '—' },
]

// 로그인 실패 원인 → 한국어 (모르는 값은 그대로)
const FAILURE_REASONS = {
  'User not found': '가입되지 않은 이메일',
  'Incorrect password': '비밀번호 틀림',
  'Too many attempts': '시도 횟수 초과로 차단',
}

const FAILURE_COLS = [
  { key: 'id',        label: 'ID' },
  // 이메일 대신 HMAC 값 (#33)
  { key: 'emailHash', label: '이메일 (해시)' },
  { key: 'reason',    label: '원인', render: v => FAILURE_REASONS[v] ?? v ?? '—' },
  { key: 'traceId',   label: '트레이스 ID' },
  { key: 'createdAt', label: '시간', render: v => v ? new Date(v).toLocaleString() : '—' },
]

const ACTIVITY_COLS = [
  { key: 'id',        label: 'ID' },
  { key: 'userId',    label: '사용자 ID' },
  { key: 'action',    label: '작업' },
  { key: 'traceId',   label: '트레이스 ID' },
  { key: 'createdAt', label: '시간', render: v => v ? new Date(v).toLocaleString() : '—' },
]

const INTEGRATION_COLS = [
  { key: 'id',        label: 'ID' },
  { key: 'actorId',   label: '시스템' },
  { key: 'action',    label: '실패 내용', render: v => v?.replace('RISKWATCH_INTEGRATION_FAILURE: ', '') ?? '—' },
  { key: 'traceId',   label: '트레이스 ID' },
  { key: 'createdAt', label: '시간', render: v => v ? new Date(v).toLocaleString() : '—' },
]

// 관리자 화면 (탭별 기록 조회)
export default function AdminPage() {
  const [activeTab, setActiveTab] = useState(0)
  const [data, setData]       = useState({ audit: null, failures: null, activity: null, integrations: null })
  const [loading, setLoading] = useState({})
  const [errors, setErrors]   = useState({})

  // 로그인 실패 검색: 입력 중인 값 / 실제로 검색한 값 (비우면 최근 100건 전체)
  const [failureEmail, setFailureEmail] = useState('')
  const [failureQuery, setFailureQuery] = useState('')

  // 탭 데이터 불러오기 (처음 한 번, force면 항상 다시, 로그인 실패는 검색어 기준)
  const fetchTab = async (tabIndex, { force = false, email = failureQuery } = {}) => {
    const key = ['audit', 'failures', 'activity', 'integrations'][tabIndex]
    if (!force && data[key] !== null) return
    setLoading(prev => ({ ...prev, [key]: true }))
    setErrors(prev => ({ ...prev, [key]: '' }))
    try {
      let res
      if (tabIndex === 0)      res = await adminApi.getAuditLogs()
      else if (tabIndex === 1) res = email ? await adminApi.searchLoginFailures(email) : await adminApi.getLoginFailures()
      else if (tabIndex === 2) res = await adminApi.getUserActivity()
      else                     res = await adminApi.getIntegrationFailures()
      setData(prev => ({ ...prev, [key]: res.data.data ?? [] }))
    } catch (err) {
      setErrors(prev => ({ ...prev, [key]: errorMessage(err, '데이터를 불러오는데 실패했습니다.') }))
    } finally {
      setLoading(prev => ({ ...prev, [key]: false }))
    }
  }

  useEffect(() => {
    fetchTab(activeTab)
  }, [activeTab])

  const key       = ['audit', 'failures', 'activity', 'integrations'][activeTab]
  const isLoading = loading[key]
  const errorMsg  = errors[key]
  const rows      = data[key]

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">관리자 패널</h1>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">감사 로그 및 활동 현황 조회</p>
      </div>

      <div className="flex flex-wrap gap-1 border-b border-gray-200 dark:border-gray-700">
        {TABS.map((tab, idx) => (
          <button key={idx} onClick={() => setActiveTab(idx)}
            className={`px-4 py-2 text-sm font-medium rounded-t-lg transition-colors ${
              activeTab === idx
                ? 'bg-white dark:bg-gray-800 border border-b-white dark:border-b-gray-800 border-gray-200 dark:border-gray-700 text-primary-600 dark:text-primary-400'
                : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'
            }`}>
            {tab}
          </button>
        ))}
      </div>

      <div className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-xs">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-base font-semibold text-gray-900 dark:text-white">{TABS[activeTab]}</h2>
          
          <button onClick={() => fetchTab(activeTab, { force: true })}
            className="text-xs text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 px-3 py-1 border border-gray-200 dark:border-gray-700 rounded-lg">
            새로고침
          </button>
        </div>

        {isLoading && <LoadingState message="로딩 중..." />}
        {errorMsg && <ErrorState message={errorMsg} onRetry={() => fetchTab(activeTab, { force: true })} />}

        {/* 로그인 실패 이메일 검색 (서버가 HMAC으로 바꿔 검색, 비우면 전체) */}
        {activeTab === 1 && (
          <form className="flex flex-wrap gap-2 mb-4" onSubmit={e => {
            e.preventDefault()
            const email = failureEmail.trim()
            setFailureQuery(email)
            fetchTab(1, { force: true, email })
          }}>
            <input type="email" value={failureEmail} onChange={e => setFailureEmail(e.target.value)}
              placeholder="이메일로 검색" aria-label="로그인 실패 이메일 검색"
              className="px-3 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
            <button type="submit" className="px-3 py-1.5 text-sm bg-primary-600 hover:bg-primary-700 text-white rounded-lg">검색</button>
            {failureQuery && (
              <button type="button" onClick={() => {
                setFailureEmail('')
                setFailureQuery('')
                fetchTab(1, { force: true, email: '' })
              }} className="px-3 py-1.5 text-sm text-gray-600 dark:text-gray-300 border border-gray-300 dark:border-gray-600 rounded-lg">전체 보기</button>
            )}
          </form>
        )}

        {!isLoading && !errorMsg && (
          <>
            {activeTab === 0 && <Table columns={AUDIT_COLS}       rows={rows} emptyMessage="감사 로그가 없습니다." />}
            {activeTab === 1 && <Table columns={FAILURE_COLS}     rows={rows}
              emptyMessage={failureQuery ? `${failureQuery}의 로그인 실패 기록이 없습니다.` : '로그인 실패 기록이 없습니다.'} />}
            {activeTab === 2 && <Table columns={ACTIVITY_COLS}    rows={rows} emptyMessage="사용자 활동 기록이 없습니다." />}
            {activeTab === 3 && <Table columns={INTEGRATION_COLS} rows={rows} emptyMessage="연동 실패 기록이 없습니다." />}
          </>
        )}
      </div>
    </div>
  )
}
