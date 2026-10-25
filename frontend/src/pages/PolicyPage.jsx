import React, { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { policyApi } from '../api/policyApi'
import LoadingState from '../components/LoadingState'
import ErrorState from '../components/ErrorState'
import PolicyInputs from '../components/PolicyInputs'
import { REGIONS } from '../utils/regions'
import { EligibilityBadge, RecommendationCard } from '../components/PolicyRecommendation'
import { POLICY_INPUT_DEFAULTS, numOrUndefined, policyInputPayload, validatePolicyInputs } from '../utils/policyInput'
import { errorMessage } from '../utils/errorMessage'

// 정책 추천 화면 (입력 => 추천 => 정책 카드)

// 주택 보유 현황 선택지
const PROPERTY_TYPES = [
  { value: 'NONE', label: '무주택' },
  { value: 'OWN',  label: '기존 주택 보유자' },
  { value: 'RENT', label: '현재 임차 중' },
]

// 지역 선택지 (수도권 여부 판단용)

// 입력값 검증 (월 소득, 대출 금액, DTI 0~1, 정책 판정 입력 모두 필수)
function validate(form) {
  const errors = {}
  if (!form.income)
    errors.income = '월 소득을 입력해주세요.'
  else if (Number(form.income) <= 0)
    errors.income = '월 소득은 0보다 커야 합니다.'
  if (!form.loanAmount || Number(form.loanAmount) <= 0)
    errors.loanAmount = '대출 금액을 입력해주세요.'
  if (!form.dti || Number(form.dti) < 0)
    errors.dti = 'DTI를 입력해주세요. (0~1 범위, 예: 0.35)'
  if (Number(form.dti) > 1)
    errors.dti = 'DTI는 0과 1 사이 값이어야 합니다.'
  return { ...errors, ...validatePolicyInputs(form) }
}

// 대출 화면에서 넘어온 값으로 시작 (지역이 목록에 없으면 '기타 지역')
function initialForm(prefill) {
  const base = {
    income: '', loanAmount: '', dti: '',
    propertyType: 'NONE', region: '서울', ...POLICY_INPUT_DEFAULTS,
  }
  if (!prefill) return base
  const merged = { ...base, ...Object.fromEntries(Object.entries(prefill).filter(([, v]) => v != null)) }
  if (!REGIONS.includes(merged.region)) merged.region = '기타 지역'
  return merged
}


// 조건 하나의 판정 표시 (충족 ✓, 미충족 ✗, 확인 필요 ?)
const CONDITION_ICON = {
  MET:     { icon: '✓', color: 'text-green-600 dark:text-green-400' },
  NOT_MET: { icon: '✗', color: 'text-red-600 dark:text-red-400' },
  UNKNOWN: { icon: '?', color: 'text-yellow-600 dark:text-yellow-400' },
}

const formatWon = n => (n == null ? '-' : `${Number(n).toLocaleString('ko-KR')}원`)

export default function PolicyPage() {
  // 대출 화면의 '자세히 보기'로 들어오면 location.state.prefill에 입력값과 계산된 DTI가 있음
  const prefill = useLocation().state?.prefill
  const [form, setForm] = useState(() => initialForm(prefill))
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
  const handleSubmit = e => {
    e.preventDefault()
    submit(form)
  }

  // 대출 화면에서 넘어왔으면 바로 판정 결과를 보여 줌 (처음 한 번)
  useEffect(() => {
    if (prefill) submit(initialForm(prefill))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const submit = async form => {
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
        income:       numOrUndefined(form.income),
        loanAmount:   Number(form.loanAmount),
        dti:          Number(form.dti),
        propertyType: form.propertyType,
        region:       form.region,
        ...policyInputPayload(form),
      })
      setResult(res.data.data)
    } catch (err) {
      setError(errorMessage(err, '추천에 실패했습니다. 다시 시도해주세요.'))
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

      <div className="max-w-lg bg-white dark:bg-gray-800 rounded-xl p-6 shadow-xs border border-gray-100 dark:border-gray-700">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              월 소득 (원) <span className="text-red-500">*</span>
            </label>
            <input type="number" name="income" value={form.income} onChange={handleChange}
              placeholder="예: 4500000"
              className={`w-full px-4 py-2 border rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-hidden focus:ring-2 focus:ring-primary-500 ${fieldErrors.income ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'}`} />
            {fieldErrors.income && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{fieldErrors.income}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              대출 금액 (원) <span className="text-red-500">*</span>
            </label>
            <input type="number" name="loanAmount" value={form.loanAmount} onChange={handleChange}
              placeholder="예: 250000000"
              className={`w-full px-4 py-2 border rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-hidden focus:ring-2 focus:ring-primary-500 ${fieldErrors.loanAmount ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'}`} />
            {fieldErrors.loanAmount && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{fieldErrors.loanAmount}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              DTI (0~1) <span className="text-red-500">*</span>
              <span className="text-gray-400 text-xs ml-1">예: 0.35 = 35%</span>
              {/* 기존 대출 이자는 DTI에 이미 들어 있어서 따로 받지 않음 */}
              <span className="block text-gray-400 text-xs">(신규 대출 월 상환액 + 기존 대출 월 이자) ÷ 월 소득, 대출 계산 결과에서 넘어옴</span>
            </label>
            <input type="number" name="dti" value={form.dti} onChange={handleChange}
              placeholder="예: 0.35" step="0.01" min="0" max="1"
              className={`w-full px-4 py-2 border rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-hidden focus:ring-2 focus:ring-primary-500 ${fieldErrors.dti ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'}`} />
            {fieldErrors.dti && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{fieldErrors.dti}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">주택 보유 현황</label>
            <select name="propertyType" value={form.propertyType} onChange={handleChange}
              className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-hidden focus:ring-2 focus:ring-primary-500">
              {PROPERTY_TYPES.map(pt => (
                <option key={pt.value} value={pt.value}>{pt.label}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">지역</label>
            <select name="region" value={form.region} onChange={handleChange}
              className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-hidden focus:ring-2 focus:ring-primary-500">
              {REGIONS.map(r => (
                <option key={r} value={r}>{r}</option>
              ))}
            </select>
          </div>

          <PolicyInputs form={form} onChange={handleChange} errors={fieldErrors} />

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
          {/* 둘 중 추천 결과 (아래에 상품별 조건 판정·근거) */}
          <RecommendationCard policy={result} />
          {result.summary && (
            <div className="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-xl p-4 text-sm text-blue-800 dark:text-blue-200">
              {result.summary}
            </div>
          )}
          <div className="flex flex-col gap-3">
            {result.recommendedPolicies?.map((policy, idx) => (
              <div key={idx}
                className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-xs">
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
                        <span>
                          <b>{c.name}</b> · {c.detail}
                          {/* 충족하려면 무엇을 바꾸거나 확인하면 되는지 */}
                          {c.hint && <span className="block text-primary-700 dark:text-primary-300">→ {c.hint}</span>}
                        </span>
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
