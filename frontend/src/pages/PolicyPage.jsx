import React, { useState } from 'react'
import { policyApi } from '../api/policyApi'
import LoadingState from '../components/LoadingState'
import ErrorState from '../components/ErrorState'

// 정책 추천 화면 (입력 => 추천 => 정책 카드)

// 주택 보유 현황 선택지
const PROPERTY_TYPES = [
  { value: 'NONE', label: '무주택' },
  { value: 'OWN',  label: '기존 주택 보유자' },
  { value: 'RENT', label: '현재 임차 중' },
]

// 지역 선택지 (수도권 여부 판단용)
const REGIONS = ['서울', '경기', '인천', '부산', '대구', '광주', '대전', '울산', '세종', '기타 지역']

// 입력값 검증 (소득, 대출 금액 필수, DSR 0~1)
function validate(form) {
  const errors = {}
  if (!form.income || Number(form.income) <= 0)
    errors.income = '월 소득을 입력해주세요.'
  if (!form.loanAmount || Number(form.loanAmount) <= 0)
    errors.loanAmount = '대출 금액을 입력해주세요.'
  if (!form.dsr || Number(form.dsr) < 0)
    errors.dsr = 'DSR을 입력해주세요. (0~1 범위, 예: 0.35)'
  if (Number(form.dsr) > 1)
    errors.dsr = 'DSR은 0과 1 사이 값이어야 합니다.'
  return errors
}


// 상품 종합 판정 배지
const ELIGIBILITY = {
  ELIGIBLE:     { label: '조건 충족', color: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' },
  NEEDS_CHECK:  { label: '확인 필요', color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200' },
  NOT_ELIGIBLE: { label: '조건 미충족', color: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300' },
}

function EligibilityBadge({ eligibility }) {
  const e = ELIGIBILITY[eligibility] || ELIGIBILITY.NEEDS_CHECK
  return <span className={`px-2 py-0.5 rounded-full text-xs font-semibold whitespace-nowrap ${e.color}`}>{e.label}</span>
}

// 조건 하나의 판정 표시 (충족 ✓, 미충족 ✗, 확인 필요 ?)
const CONDITION_ICON = {
  MET:     { icon: '✓', color: 'text-green-600 dark:text-green-400' },
  NOT_MET: { icon: '✗', color: 'text-red-600 dark:text-red-400' },
  UNKNOWN: { icon: '?', color: 'text-yellow-600 dark:text-yellow-400' },
}

const formatWon = n => (n == null ? '-' : `${Number(n).toLocaleString('ko-KR')}원`)

export default function PolicyPage() {
  const [form, setForm] = useState({
    income:       '',
    existingDebt: '',
    loanAmount:   '',
    dsr:          '',
    propertyType: 'NONE',
    region:       '서울',
  })
  const [fieldErrors, setFieldErrors] = useState({})
  const [result, setResult]   = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState('')

  const handleChange = e => {
    setForm(prev => ({ ...prev, [e.target.name]: e.target.value }))
    if (fieldErrors[e.target.name]) {
      setFieldErrors(prev => ({ ...prev, [e.target.name]: '' }))
    }
  }

  // 검증 => API 호출 => 결과 표시
  const handleSubmit = async e => {
    e.preventDefault()
    const errors = validate(form)
    if (Object.keys(errors).length) {
      setFieldErrors(errors)
      return
    }
    setError('')
    setFieldErrors({})
    setLoading(true)
    try {
      const res = await policyApi.recommend({
        income:       Number(form.income),
        existingDebt: Number(form.existingDebt) || 0,
        loanAmount:   Number(form.loanAmount),
        dsr:          Number(form.dsr),
        propertyType: form.propertyType,
        region:       form.region,
      })
      setResult(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || '추천에 실패했습니다. 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">정책 추천</h1>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
          나에게 맞는 정부 지원 제도와 대출 정책을 확인하세요
        </p>
      </div>

      <div className="max-w-lg bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm border border-gray-100 dark:border-gray-700">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              월 소득 (원) <span className="text-red-500">*</span>
            </label>
            <input type="number" name="income" value={form.income} onChange={handleChange}
              placeholder="예: 4500000"
              className={`w-full px-4 py-2 border rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500 ${fieldErrors.income ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'}`} />
            {fieldErrors.income && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{fieldErrors.income}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              대출 금액 (원) <span className="text-red-500">*</span>
            </label>
            <input type="number" name="loanAmount" value={form.loanAmount} onChange={handleChange}
              placeholder="예: 250000000"
              className={`w-full px-4 py-2 border rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500 ${fieldErrors.loanAmount ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'}`} />
            {fieldErrors.loanAmount && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{fieldErrors.loanAmount}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              기존 부채 (원) <span className="text-gray-400 text-xs">(선택)</span>
            </label>
            <input type="number" name="existingDebt" value={form.existingDebt} onChange={handleChange}
              placeholder="예: 0"
              className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              DSR (0~1) <span className="text-red-500">*</span>
              <span className="text-gray-400 text-xs ml-1">예: 0.35 = 35%</span>
            </label>
            <input type="number" name="dsr" value={form.dsr} onChange={handleChange}
              placeholder="예: 0.35" step="0.01" min="0" max="1"
              className={`w-full px-4 py-2 border rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500 ${fieldErrors.dsr ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'}`} />
            {fieldErrors.dsr && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{fieldErrors.dsr}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">주택 보유 현황</label>
            <select name="propertyType" value={form.propertyType} onChange={handleChange}
              className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500">
              {PROPERTY_TYPES.map(pt => (
                <option key={pt.value} value={pt.value}>{pt.label}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">지역</label>
            <select name="region" value={form.region} onChange={handleChange}
              className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500">
              {REGIONS.map(r => (
                <option key={r} value={r}>{r}</option>
              ))}
            </select>
          </div>

          <button type="submit" disabled={loading}
            className="w-full py-2.5 bg-primary-600 hover:bg-primary-700 text-white font-semibold rounded-lg transition-colors disabled:opacity-60">
            {loading ? '분석 중...' : '정책 추천 받기'}
          </button>
        </form>
      </div>

      {loading && <LoadingState message="프로필 분석 중..." />}
      {error && <ErrorState message={error} onRetry={() => setError('')} />}

      {/* 추천 결과 (요약 + 정책 카드 3개 + 다시 검색) */}
      {result && !loading && (
        <div className="flex flex-col gap-4 max-w-2xl">
          <p className="text-xs text-gray-500 dark:text-gray-400">
            입력한 정보로 판단한 결과입니다. 최종 자격과 한도는 각 기관 심사에서 결정됩니다.
          </p>
          {result.summary && (
            <div className="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-xl p-4 text-sm text-blue-800 dark:text-blue-200">
              {result.summary}
            </div>
          )}
          <div className="flex flex-col gap-3">
            {result.recommendedPolicies?.map((policy, idx) => (
              <div key={idx}
                className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-sm">
                <div className="flex items-start justify-between gap-3 mb-1">
                  <h3 className="font-semibold text-gray-900 dark:text-white">{policy.name}</h3>
                  <EligibilityBadge eligibility={policy.eligibility} />
                </div>
                <p className="text-xs text-gray-500 dark:text-gray-400 mb-2">{policy.provider}</p>
                <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">{policy.description}</p>

                <ul className="flex flex-col gap-1 mb-3">
                  {policy.conditions?.map((c, ci) => {
                    const icon = CONDITION_ICON[c.result] || CONDITION_ICON.UNKNOWN
                    return (
                      <li key={ci} className="flex items-start gap-2 text-xs text-gray-600 dark:text-gray-300">
                        <span className={`font-bold ${icon.color}`}>{icon.icon}</span>
                        <span><b>{c.name}</b> · {c.detail}</span>
                      </li>
                    )
                  })}
                </ul>

                <div className="text-xs text-gray-600 dark:text-gray-300 mb-2">
                  <div>입력 기준 최대 한도: <b>{formatWon(policy.maxLoanAmount)}</b></div>
                  <div>금리: {policy.interestRate}</div>
                </div>

                {policy.additionalChecks?.length > 0 && (
                  <details className="text-xs text-gray-500 dark:text-gray-400 mb-2">
                    <summary className="cursor-pointer">신청 전에 따로 확인할 조건</summary>
                    <ul className="list-disc ml-5 mt-1">
                      {policy.additionalChecks.map((a, ai) => <li key={ai}>{a}</li>)}
                    </ul>
                  </details>
                )}

                <a href={policy.sourceUrl} target="_blank" rel="noopener noreferrer"
                  className="text-xs text-primary-600 dark:text-primary-400 underline">
                  공식 안내 ({policy.checkedDate} 확인)
                </a>
              </div>
            ))}
          </div>
          <button onClick={() => setResult(null)}
            className="w-fit px-4 py-2 text-sm text-gray-600 dark:text-gray-300 border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700">
            다시 검색
          </button>
        </div>
      )}
    </div>
  )
}
