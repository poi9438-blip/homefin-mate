import React, { useEffect, useState } from 'react'
import { loanApi } from '../api/loanApi'

// 대출 시뮬레이터 화면 (입력 => 계산 => 결과, 내 계산 이력)

// 지역 선택지
const REGIONS = ['Sydney', 'Melbourne', 'Brisbane', 'Perth', 'Adelaide', 'Darwin', 'Hobart',
                 '서울', '부산', '대구', '인천', '광주']

const INITIAL_FORM = {
  loanAmount: '', annualInterestRate: '', termMonths: '', monthlyIncome: '', region: '',
}

// 입력값 검증 (금액/이자율/기간 필수, 이자율 0~30%, 기간 1~600개월)
function validate(form) {
  const errors = {}
  if (!form.loanAmount || Number(form.loanAmount) <= 0)
    errors.loanAmount = '대출 금액은 0보다 커야 합니다.'
  if (!form.annualInterestRate || Number(form.annualInterestRate) <= 0)
    errors.annualInterestRate = '이자율은 0보다 커야 합니다.'
  if (Number(form.annualInterestRate) > 30)
    errors.annualInterestRate = '이자율이 너무 높습니다. (최대 30%)'
  if (!form.termMonths || Number(form.termMonths) < 1)
    errors.termMonths = '대출 기간은 최소 1개월 이상이어야 합니다.'
  if (Number(form.termMonths) > 600)
    errors.termMonths = '대출 기간은 600개월(50년)을 초과할 수 없습니다.'
  if (form.monthlyIncome && Number(form.monthlyIncome) <= 0)
    errors.monthlyIncome = '월 소득은 0보다 커야 합니다.'
  return errors
}

// 금액 표시 (₩, 천 단위 쉼표)
const won = v => `₩${Number(v).toLocaleString()}`

export default function LoanPage() {
  const [form, setForm]               = useState(INITIAL_FORM)
  const [fieldErrors, setFieldErrors] = useState({})
  const [result, setResult]           = useState(null)
  const [history, setHistory]         = useState([])
  const [error, setError]             = useState('')
  const [loading, setLoading]         = useState(false)

  // 내 계산 이력 불러오기
  const loadHistory = async () => {
    try {
      const res = await loanApi.getHistory()
      setHistory(res.data.data || [])
    } catch {
      setHistory([])
    }
  }

  useEffect(() => { loadHistory() }, [])

  const handleChange = e => {
    setForm(prev => ({ ...prev, [e.target.name]: e.target.value }))
    if (fieldErrors[e.target.name]) setFieldErrors(prev => ({ ...prev, [e.target.name]: '' }))
  }

  // 계산: 검증 => API 호출 => 결과 표시 => 이력 새로고침
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
      const res = await loanApi.calculate({
        loanAmount:         Number(form.loanAmount),
        annualInterestRate: Number(form.annualInterestRate),
        termMonths:         Number(form.termMonths),
        monthlyIncome:      form.monthlyIncome ? Number(form.monthlyIncome) : undefined,
        region:             form.region || undefined,
      })
      setResult(res.data.data)
      loadHistory()
    } catch (err) {
      setError(err.response?.data?.message || '계산에 실패했습니다. 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">대출 시뮬레이터</h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">원리금균등 상환 기준 월 상환액과 DSR을 계산합니다</p>
      </div>

      <div className="max-w-lg bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm border border-gray-100 dark:border-gray-700">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {[
            { name: 'loanAmount',         label: '대출 금액 (원)',   placeholder: '예: 180000000', required: true  },
            { name: 'annualInterestRate', label: '연 이자율 (%)',    placeholder: '예: 3.8',       required: true  },
            { name: 'termMonths',         label: '대출 기간 (개월)', placeholder: '예: 360',       required: true  },
            { name: 'monthlyIncome',      label: '월 소득 (원)',     placeholder: '예: 4000000',   required: false },
          ].map(({ name, label, placeholder, required }) => (
            <div key={name}>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                {label}{required && <span className="text-red-500 ml-1">*</span>}
              </label>
              <input
                type="number" name={name} value={form[name]} onChange={handleChange}
                placeholder={placeholder}
                className={`w-full px-4 py-2 border rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500 ${
                  fieldErrors[name] ? 'border-red-500 dark:border-red-400' : 'border-gray-300 dark:border-gray-600'
                }`}
              />
              {fieldErrors[name] && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{fieldErrors[name]}</p>}
            </div>
          ))}

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              지역 <span className="text-gray-400 text-xs">(선택)</span>
            </label>
            <select name="region" value={form.region} onChange={handleChange}
              className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500">
              <option value="">— 지역 선택 —</option>
              {REGIONS.map(r => <option key={r} value={r.toLowerCase()}>{r}</option>)}
            </select>
          </div>

          <button type="submit" disabled={loading}
            className="w-full py-2.5 bg-primary-600 hover:bg-primary-700 text-white font-semibold rounded-lg transition-colors disabled:opacity-60">
            {loading ? '계산 중...' : '계산하기'}
          </button>
        </form>
      </div>

      {error && (
        <div className="max-w-lg p-3 bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-400 rounded-lg text-sm">
          {error}
        </div>
      )}

      {/* 계산 결과 (월 상환금, 총 이자, 총 상환액, DSR) */}
      {result && !loading && (
        <div className="flex flex-col gap-4 max-w-2xl">
          <div className="flex items-center gap-3">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">계산 결과</h2>
            <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
              result.dsrStatus === 'ACCEPTABLE'
                ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
            }`}>
              DSR: {result.dsrStatus === 'ACCEPTABLE' ? '적정' : '초과'}
            </span>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {[
              { title: '월 상환금', value: won(result.monthlyPayment) },
              { title: '총 이자',   value: won(result.totalInterest) },
              { title: '총 상환액', value: won(result.totalPayment) },
              { title: 'DSR',       value: `${(result.dsr * 100).toFixed(1)}%`,
                sub: result.dsrStatus === 'ACCEPTABLE' ? '적정 범위 이내' : '높음 — 예산 검토 필요' },
            ].map(({ title, value, sub }) => (
              <div key={title} className="bg-white dark:bg-gray-800 rounded-xl p-5 shadow-sm border border-gray-100 dark:border-gray-700">
                <p className="text-sm text-gray-500 dark:text-gray-400">{title}</p>
                <p className="mt-1 text-xl font-bold text-gray-900 dark:text-white">{value}</p>
                {sub && <p className="mt-1 text-xs text-gray-400">{sub}</p>}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 내 계산 이력 (최신순) */}
      {history.length > 0 && (
        <div className="max-w-2xl bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm border border-gray-100 dark:border-gray-700">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-3">내 계산 이력</h2>
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 dark:text-gray-400 border-b border-gray-100 dark:border-gray-700">
                <th className="py-2">대출 금액</th>
                <th className="py-2">월 상환금</th>
                <th className="py-2">DSR</th>
              </tr>
            </thead>
            <tbody>
              {history.map(h => (
                <tr key={h.simulationId} className="border-b border-gray-50 dark:border-gray-700/50 text-gray-900 dark:text-white">
                  <td className="py-2">{won(h.loanAmount)}</td>
                  <td className="py-2">{won(h.monthlyPayment)}</td>
                  <td className="py-2">
                    {(h.dsr * 100).toFixed(1)}%{' '}
                    <span className={h.dsrStatus === 'ACCEPTABLE' ? 'text-green-600' : 'text-red-600'}>
                      ({h.dsrStatus === 'ACCEPTABLE' ? '적정' : '초과'})
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
