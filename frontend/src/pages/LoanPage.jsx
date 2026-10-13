import React, { useState } from 'react'
import { Doughnut } from 'react-chartjs-2'
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js'
import { analyzeApi } from '../api/analyzeApi'
import { generateTraceId } from '../utils/traceId'
import { useFormDraft } from '../hooks/useFormDraft'
import LoadingState from '../components/LoadingState'
import ErrorState from '../components/ErrorState'
import SummaryCard from '../components/SummaryCard'
import ChartCard from '../components/ChartCard'
import StepGuide from '../components/StepGuide'

// 대출 시뮬레이터 화면 (통합 분석: 대출 + 정책 + 위험 평가 + 지역)
// 도넛 차트에 필요한 chart.js 부품 등록
ChartJS.register(ArcElement, Tooltip, Legend)

// 진행 단계
const steps = ['정보 입력', '분석 중', '결과 확인']

// 지역 선택지
const REGIONS = ['Sydney', 'Melbourne', 'Brisbane', 'Perth', 'Adelaide', 'Darwin', 'Hobart',
                 '서울', '부산', '대구', '인천', '광주']
const PROPERTY_TYPES = [
  { value: 'NONE', label: '생애 최초 주택 구매 (무주택)' },
  { value: 'OWN',  label: '기존 주택 보유자' },
  { value: 'RENT', label: '현재 임차 중' },
]

const INITIAL_FORM = {
  loanAmount: '', annualInterestRate: '', termMonths: '',
  monthlyIncome: '', existingDebt: '', region: '', propertyType: 'NONE',
}

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
  if (form.existingDebt && Number(form.existingDebt) < 0)
    errors.existingDebt = '기존 부채는 0 이상이어야 합니다.'
  return errors
}

// 리스크 평가 패널 (fallback이면 이용 불가 안내, 등급별 색)
function RiskPanel({ risk }) {
  if (!risk) return null
  if (!risk.available) {
    return (
      <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-700 rounded-xl p-5">
        <span className="text-yellow-600 dark:text-yellow-400 font-semibold text-sm">리스크 평가 불가</span>
        <p className="text-sm text-yellow-700 dark:text-yellow-300 mt-1">
          {risk.recommendation || 'RiskWatch 서비스가 일시적으로 이용 불가합니다. 대출 계산 결과는 유효합니다.'}
        </p>
      </div>
    )
  }
  const levelColor = risk.riskLevel === 'LOW'
    ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
    : risk.riskLevel === 'HIGH' || risk.riskLevel === 'CRITICAL'
      ? 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
      : 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200'
  return (
    <div className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-sm">
      <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-3">리스크 평가</h3>
      <div className="flex flex-wrap gap-3 mb-3">
        <span className={`px-3 py-1 rounded-full text-sm font-medium ${levelColor}`}>{risk.riskLevel}</span>
        {risk.riskScore > 0 && (
          <span className="px-3 py-1 rounded-full text-sm font-medium bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-200">
            점수: {risk.riskScore}
          </span>
        )}
      </div>
      {risk.recommendation && <p className="text-sm text-gray-700 dark:text-gray-300 mb-2">{risk.recommendation}</p>}
      {risk.aiExplanation && <p className="text-xs text-gray-500 dark:text-gray-400 italic">{risk.aiExplanation}</p>}
    </div>
  )
}

// 지역 분석 패널 (평균 가격, 성장률, 시장 리스크)
function RegionPanel({ region }) {
  if (!region) return null
  if (region.unavailable) {
    return (
      <div className="bg-gray-50 dark:bg-gray-700/30 border border-gray-200 dark:border-gray-600 rounded-xl p-5">
        <p className="text-sm text-gray-500 dark:text-gray-400">
          지역 분석 서비스가 일시적으로 이용 불가합니다. 대출 및 리스크 결과에는 영향이 없습니다.
        </p>
      </div>
    )
  }
  return (
    <div className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-sm">
      <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-3">지역 분석 — {region.region}</h3>
      <div className="grid grid-cols-3 gap-4">
        <div>
          <p className="text-xs text-gray-500 dark:text-gray-400">평균 가격</p>
          <p className="text-sm font-semibold text-gray-900 dark:text-white">
            ₩{Number(region.averagePrice ?? region.average_price).toLocaleString()}
          </p>
        </div>
        <div>
          <p className="text-xs text-gray-500 dark:text-gray-400">성장률</p>
          <p className="text-sm font-semibold text-gray-900 dark:text-white">
            {(Number(region.growthRate ?? region.growth_rate) * 100).toFixed(1)}%
          </p>
        </div>
        <div>
          <p className="text-xs text-gray-500 dark:text-gray-400">시장 리스크</p>
          <p className="text-sm font-semibold text-gray-900 dark:text-white">
            {Number(region.riskScore ?? region.risk_score).toFixed(1)}
          </p>
        </div>
      </div>
    </div>
  )
}

export default function LoanPage() {
  // 입력값 (임시 저장 훅 사용)
  const { values: form, setValues: setForm, clearDraft } = useFormDraft('loan', INITIAL_FORM)
  const [step, setStep]               = useState(0)
  const [fieldErrors, setFieldErrors] = useState({})
  const [result, setResult]           = useState(null)
  const [loading, setLoading]         = useState(false)
  const [error, setError]             = useState('')

  const handleChange = e => {
    setForm(prev => ({ ...prev, [e.target.name]: e.target.value }))
    if (fieldErrors[e.target.name]) setFieldErrors(prev => ({ ...prev, [e.target.name]: '' }))
  }

  const handleSubmit = async e => {
    e.preventDefault()
    const errors = validate(form)
    if (Object.keys(errors).length) { setFieldErrors(errors); return }
    setError('')
    setFieldErrors({})
    setLoading(true)
    setStep(1)
    const traceId = generateTraceId()
    try {
      const res = await analyzeApi.analyze({
        loanAmount:         Number(form.loanAmount),
        annualInterestRate: Number(form.annualInterestRate),
        termMonths:         Number(form.termMonths),
        monthlyIncome:      form.monthlyIncome ? Number(form.monthlyIncome) : undefined,
        existingDebt:       form.existingDebt  ? Number(form.existingDebt)  : undefined,
        region:             form.region        || undefined,
        propertyType:       form.propertyType  || 'NONE',
      }, traceId)
      setResult(res.data.data)
      setStep(2)
    } catch (err) {
      const msg = err.response?.data?.message || '분석에 실패했습니다. 다시 시도해주세요.'
      setError(msg)
      setStep(0)
    } finally {
      setLoading(false)
    }
  }

  // 새 분석: 결과·단계 초기화, 임시 저장 삭제
  const handleNewAnalysis = () => { setResult(null); setStep(0); clearDraft() }

  const loanResult   = result?.loan
  const riskResult   = result?.risk
  const regionResult = result?.region
  const policyResult = result?.policy

  const dsrStatusColor = loanResult?.dsrStatus === 'ACCEPTABLE'
    ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
    : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'


  // 원금 vs 이자 도넛 데이터
  const interestPieData = loanResult ? (() => {
    const principal = Number(form.loanAmount) || 0
    const interest  = Number(loanResult.totalInterest) || 0
    if (!principal && !interest) return null
    const ratio = interest > 0 ? ((interest / (principal + interest)) * 100).toFixed(1) : 0
    return {
      data: {
        labels: ['대출 원금', '총 이자'],
        datasets: [{
          data: [principal, interest],
          backgroundColor: ['#3b82f6', '#f59e0b'],
          borderWidth: 0,
        }],
      },
      ratio,
      principal,
      interest,
    }
  })() : null

  // 월 소득 대비 상환 부담 도넛 데이터
  const incomePieData = (loanResult && form.monthlyIncome) ? (() => {
    const income    = Number(form.monthlyIncome)
    const payment   = Number(loanResult.monthlyPayment)
    const remaining = income - payment
    if (income <= 0) return null
    return {
      data: {
        labels: ['월 상환금', '가처분 소득'],
        datasets: [{
          data: [payment, Math.max(remaining, 0)],
          backgroundColor: ['#ef4444', '#22c55e'],
          borderWidth: 0,
        }],
      },
      income,
      payment,
    }
  })() : null

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">대출 시뮬레이터</h1>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
          월 상환금 계산, 정책 추천, 리스크 평가를 한 번에 받아보세요
        </p>
      </div>

      <div className="max-w-lg">
        <StepGuide steps={steps} currentStep={step} />
      </div>

      <div className="max-w-lg bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm border border-gray-100 dark:border-gray-700">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {[
            { name: 'loanAmount',        label: '대출 금액 (원)',            placeholder: '예: 180000000', required: true  },
            { name: 'annualInterestRate', label: '연 이자율 (%)',             placeholder: '예: 3.8',       required: true  },
            { name: 'termMonths',        label: '대출 기간 (개월)',           placeholder: '예: 360',       required: true  },
            { name: 'monthlyIncome',     label: '월 소득 (원)',               placeholder: '예: 4000000',   required: false },
            { name: 'existingDebt',      label: '기존 월 부채 상환액 (원)',   placeholder: '예: 0',         required: false },
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
              지역 <span className="text-gray-400 text-xs">(선택 — 리스크 및 정책 점수에 반영)</span>
            </label>
            <select name="region" value={form.region} onChange={handleChange}
              className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500">
              <option value="">— 지역 선택 —</option>
              {REGIONS.map(r => <option key={r} value={r.toLowerCase()}>{r}</option>)}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">주택 보유 현황</label>
            <select name="propertyType" value={form.propertyType} onChange={handleChange}
              className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500">
              {PROPERTY_TYPES.map(({ value, label }) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>

          <button type="submit" disabled={loading}
            className="w-full py-2.5 bg-primary-600 hover:bg-primary-700 text-white font-semibold rounded-lg transition-colors disabled:opacity-60">
            {loading ? '분석 중...' : '분석 시작'}
          </button>
        </form>
      </div>

      {loading && <LoadingState message="분석 중입니다..." />}
      {error && <ErrorState message={error} onRetry={() => { setError(''); setStep(0) }} />}

      {loanResult && !loading && (
        <div className="flex flex-col gap-4 max-w-2xl">
          <div className="flex items-center gap-3">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">대출 분석 결과</h2>
            <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${dsrStatusColor}`}>
              DSR: {loanResult.dsrStatus === 'ACCEPTABLE' ? '적정' : '초과'}
            </span>
          </div>

          {/* 요약 카드 */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <SummaryCard title="월 상환금"  value={`₩${Number(loanResult.monthlyPayment).toLocaleString()}`} icon="💳" />
            <SummaryCard title="총 이자"    value={`₩${Number(loanResult.totalInterest).toLocaleString()}`}  icon="📈" />
            <SummaryCard title="총 상환액"  value={`₩${Number(loanResult.totalPayment).toLocaleString()}`}   icon="🏦" />
            <SummaryCard title="DSR"        value={`${(loanResult.dsr * 100).toFixed(1)}%`}                  icon="📊"
              subtitle={loanResult.dsrStatus === 'ACCEPTABLE' ? '적정 범위 이내' : '높음 — 예산 검토 필요'} />
          </div>

          {/* 원금 vs 이자 / 소득 대비 부담 차트 */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {interestPieData && (
              <ChartCard title="원금 vs 총 이자 구성">
                <div className="flex justify-center">
                  <div className="w-44 h-44">
                    <Doughnut data={interestPieData.data}
                      options={{ plugins: { legend: { position: 'bottom' } }, cutout: '65%' }} />
                  </div>
                </div>
                <div className="flex justify-around mt-3 text-xs text-center">
                  <div>
                    <p className="text-gray-500 dark:text-gray-400">대출 원금</p>
                    <p className="font-semibold text-blue-600 dark:text-blue-400">
                      ₩{interestPieData.principal.toLocaleString()}
                    </p>
                  </div>
                  <div>
                    <p className="text-gray-500 dark:text-gray-400">총 이자 ({interestPieData.ratio}%)</p>
                    <p className="font-semibold text-amber-600 dark:text-amber-400">
                      ₩{interestPieData.interest.toLocaleString()}
                    </p>
                  </div>
                </div>
              </ChartCard>
            )}

            {incomePieData && (
              <ChartCard title="월 소득 대비 상환 부담">
                <div className="flex justify-center">
                  <div className="w-44 h-44">
                    <Doughnut data={incomePieData.data}
                      options={{ plugins: { legend: { position: 'bottom' } }, cutout: '65%' }} />
                  </div>
                </div>
                <div className="flex justify-around mt-3 text-xs text-center">
                  <div>
                    <p className="text-gray-500 dark:text-gray-400">월 상환금</p>
                    <p className="font-semibold text-red-600 dark:text-red-400">
                      ₩{incomePieData.payment.toLocaleString()}
                    </p>
                  </div>
                  <div>
                    <p className="text-gray-500 dark:text-gray-400">가처분 소득</p>
                    <p className="font-semibold text-green-600 dark:text-green-400">
                      ₩{Math.max(incomePieData.income - incomePieData.payment, 0).toLocaleString()}
                    </p>
                  </div>
                </div>
              </ChartCard>
            )}
          </div>

          <RiskPanel risk={riskResult} />
          <RegionPanel region={regionResult} />

          {policyResult && (
            <div className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-sm">
              <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-2">정책 추천</h3>
              <p className="text-sm text-gray-700 dark:text-gray-300 mb-3">{policyResult.summary}</p>
              <div className="flex flex-wrap gap-2">
                {policyResult.recommendedPolicies?.slice(0, 3).map((p, i) => (
                  <span key={i} className="px-3 py-1 bg-blue-50 dark:bg-blue-900/30 text-blue-800 dark:text-blue-200 rounded-full text-xs font-medium">
                    {p.name}
                  </span>
                ))}
              </div>
            </div>
          )}

          <button onClick={handleNewAnalysis}
            className="w-fit px-4 py-2 text-sm text-gray-600 dark:text-gray-300 border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700">
            새 분석
          </button>
        </div>
      )}
    </div>
  )
}
