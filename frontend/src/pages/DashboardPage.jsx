import React, { useState, useEffect } from 'react'
import SummaryCard from '../components/SummaryCard'
import ChartCard from '../components/ChartCard'
import LoadingState from '../components/LoadingState'
import { Line, Bar, Doughnut } from 'react-chartjs-2'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js'
import { loanApi } from '../api/loanApi'
import { DTI_LIMIT, isNewLoanOnly, NEW_LOAN_ONLY_NOTE, dtiStatusView } from '../utils/dti'
import DtiBadge from '../components/DtiBadge'
import { reportApi } from '../api/reportApi'
import { analysisApi } from '../api/analysisApi'
import RegionSummary from '../components/RegionSummary'
import { REGIONS } from '../utils/regions'

// 꺾은선,막대,도넛 차트에 필요한 chart.js 부품 등록
ChartJS.register(
  CategoryScale, LinearScale, PointElement, LineElement,
  BarElement, ArcElement, Title, Tooltip, Legend
)

// 요약 기본값 (리포트 실패 시, 리포트 reportData와 같은 이름)
const FALLBACK_SUMMARY = {
  totalSimulations: 0,
  lastDti: null,
}

const CHART_OPTS = { responsive: true, plugins: { legend: { position: 'top' } } }

// 월 상환금 추이 (최근 12회, 과거 => 최근)
function buildPaymentTrendData(history) {
  if (!history?.length) return null
  const recent = [...history].slice(0, 12).reverse()
  return {
    labels: recent.map((_, i) => `${i + 1}회차`),
    datasets: [{
      label: '월 상환금 (원)',
      data: recent.map(s => Number(s.monthlyPayment) || 0),
      borderColor: '#3b82f6',
      backgroundColor: 'rgba(59, 130, 246, 0.1)',
      tension: 0.4,
    }],
  }
}

// 대출 금액 추이 (최근 12회)
function buildLoanAmountTrendData(history) {
  if (!history?.length) return null
  const recent = [...history].slice(0, 12).reverse()
  return {
    labels: recent.map((_, i) => `${i + 1}회차`),
    datasets: [{
      label: '대출 금액 (원)',
      data: recent.map(s => Number(s.loanAmount) || 0),
      borderColor: '#8b5cf6',
      backgroundColor: 'rgba(139, 92, 246, 0.1)',
      tension: 0.4,
    }],
  }
}

// DTI 추이 막대 (적정 초록 / 초과 빨강)
function buildDtiTrendData(history) {
  if (!history?.length) return null
  const recent = [...history].slice(0, 12).reverse()
  return {
    labels: recent.map((_, i) => `${i + 1}회차`),
    datasets: [{
      label: 'DTI (%)',
      data: recent.map(s => Number((s.dti * 100).toFixed(1)) || 0),
      backgroundColor: recent.map(s => dtiStatusView(s.dtiStatus).chartColor),
    }],
  }
}

// 판정별 계산 수 (받은 이력, 적정·초과·판단 불가)
function countByStatus(history) {
  const count = status => (history ?? []).filter(s => (s.dtiStatus ?? 'UNKNOWN') === status).length
  return { acceptable: count('ACCEPTABLE'), exceeded: count('HIGH'), unknown: count('UNKNOWN') }
}

// DTI 적정/초과/판단 불가 비율 (받은 이력)
function buildDtiStatusPieData(history) {
  if (!history?.length) return null
  const { acceptable, exceeded, unknown } = countByStatus(history)
  return {
    labels: ['적정', '초과', '판단 불가'],
    datasets: [{
      data: [acceptable, exceeded, unknown],
      backgroundColor: ['ACCEPTABLE', 'HIGH', 'UNKNOWN'].map(st => dtiStatusView(st).chartColor),
      borderWidth: 0,
    }],
  }
}

// 최근 계산 원금 vs 총 이자
function buildInterestPieData(history) {
  if (!history?.length) return null
  const latest = history[0]
  const principal = Number(latest.loanAmount) || 0
  const interest  = Number(latest.totalInterest) || 0
  if (!principal && !interest) return null
  return {
    labels: ['대출 원금', '총 이자'],
    datasets: [{
      data: [principal, interest],
      backgroundColor: ['#3b82f6', '#f59e0b'],
      borderWidth: 0,
    }],
  }
}


// 대시보드가 받는 최근 계산 수 (이력 API 최대값)
const HISTORY_SIZE = 100

// 대시보드 (요약 카드, 지역 분석, 추이 차트, 최근 계산)
export default function DashboardPage() {
  const [history, setHistory]           = useState(null)
  const [summary, setSummary]           = useState(null)
  const [loading, setLoading]           = useState(true)
  const [summaryError, setSummaryError] = useState(false)

  const [region, setRegion]               = useState('')
  const [regionData, setRegionData]       = useState(null)
  const [regionLoading, setRegionLoading] = useState(false)
  const [regionError, setRegionError]     = useState('')

  useEffect(() => {
    let cancelled = false
    const loadData = async () => {
      setLoading(true)
      try {
        // 최근 계산 최대 100건 (차트·비율 계산용)
        const res = await loanApi.getHistory({ size: HISTORY_SIZE })
        if (!cancelled) setHistory(res.data.data ?? [])
      } catch {
        if (!cancelled) setHistory([])
      }
      try {
        const res = await reportApi.getSummary()
        // 리포트 요약 값 (data.reportData)
        if (!cancelled) setSummary(res.data.data?.reportData ?? FALLBACK_SUMMARY)
      } catch {
        if (!cancelled) { setSummaryError(true); setSummary(FALLBACK_SUMMARY) }
      }
      if (!cancelled) setLoading(false)
    }
    loadData()
    return () => { cancelled = true }
  }, [])

  // 지역 분석 조회
  const handleRegionFilter = async () => {
    if (!region) return
    setRegionLoading(true)
    setRegionError('')
    try {
      const res = await analysisApi.getRegionAnalysis(region)
      setRegionData(res.data.data ?? res.data)
    } catch {
      setRegionError('지역 데이터를 불러올 수 없습니다.')
      setRegionData(null)
    } finally {
      setRegionLoading(false)
    }
  }

  const paymentChartData    = buildPaymentTrendData(history)
  const loanAmountChartData = buildLoanAmountTrendData(history)
  const dtiChartData        = buildDtiTrendData(history)
  const dtiStatusPieData    = buildDtiStatusPieData(history)
  const interestPieData     = buildInterestPieData(history)

  // 요약 카드 값 (리포트 값이 없으면 계산 이력으로 대체)
  const lastDtiRaw     = summary?.lastDti ?? (history?.length ? history[0].dti : null)
  const lastDtiDisplay = lastDtiRaw != null ? `${(lastDtiRaw * 100).toFixed(1)}%` : '—'
  const lastDtiStatus  = lastDtiRaw != null
    ? (lastDtiRaw <= DTI_LIMIT ? '적정 범위' : '높음 — 예산 검토 필요')
    : ''
  // 최근 월 상환금 (리포트 최근 계산, 없으면 이력 첫 건)
  const lastPayment = summary?.lastSimulation?.monthlyPayment ?? history?.[0]?.monthlyPayment
  // DTI 적정 비율 (받은 이력 중 판단 가능한 기록 기준)
  const { acceptable, exceeded } = countByStatus(history)
  const judged = acceptable + exceeded
  const acceptableRatio = judged > 0 ? `${Math.round((acceptable / judged) * 100)}%` : '—'
  // 받은 이력이 전체 계산 수보다 적으면 "최근 N회" 기준이라고 표시 (전체처럼 보이지 않게)
  const totalSims = summary?.totalSimulations ?? history?.length ?? 0
  const partial = (history?.length ?? 0) > 0 && totalSims > history.length
  const scope = partial ? `최근 ${history.length}회` : '전체'

  if (loading) return <LoadingState message="대시보드 로딩 중..." />

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">대시보드</h1>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
          나의 재무 현황 한눈에 보기
        </p>
      </div>

      {summaryError && (
        <div className="text-xs text-yellow-700 dark:text-yellow-400 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-700 rounded-lg px-4 py-2">
          요약 서비스를 이용할 수 없습니다 — 대출 이력 데이터를 기준으로 표시합니다.
        </div>
      )}

      {/* 요약 카드 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <SummaryCard
          title="총 시뮬레이션"
          value={String(totalSims)}
          icon="🏦"
        />
        <SummaryCard
          title="최근 DTI"
          value={lastDtiDisplay}
          icon="📊"
          subtitle={lastDtiStatus}
        />
        <SummaryCard
          title="최근 월 상환금"
          value={lastPayment != null ? `₩${Number(lastPayment).toLocaleString()}` : '—'}
          icon="💳"
        />
        <SummaryCard
          title="DTI 적정 비율"
          value={acceptableRatio}
          icon="✅"
          subtitle={judged > 0
            ? (partial ? `${scope} 기준, ${judged}회 중 적정 ${acceptable}회` : `전체 ${judged}회 중 적정 ${acceptable}회`)
            : ''}
        />
      </div>

      {/* 지역 분석 */}
      <div className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-xs">
        <h2 className="text-base font-semibold text-gray-900 dark:text-white mb-3">지역 분석</h2>
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">지역 선택</label>
            <select
              value={region}
              onChange={e => { setRegion(e.target.value); setRegionData(null); setRegionError('') }}
              className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white text-sm focus:outline-hidden focus:ring-2 focus:ring-primary-500"
            >
              <option value="">— 선택하세요 —</option>
              {REGIONS.map(r => <option key={r} value={r}>{r}</option>)}
            </select>
          </div>
          <button
            onClick={handleRegionFilter}
            disabled={!region || regionLoading}
            className="px-4 py-2 text-sm bg-primary-600 hover:bg-primary-700 text-white rounded-lg disabled:opacity-60 transition-colors"
          >
            {regionLoading ? '조회 중...' : '데이터 조회'}
          </button>
        </div>
        {regionError && <p className="mt-2 text-xs text-red-600 dark:text-red-400">{regionError}</p>}
        {regionData && (
          <div className="mt-4">
            <RegionSummary data={regionData} />
          </div>
        )}
      </div>

      {history && history.length > 0 ? (
        <>
          {/* 1행: 월 상환금 추이 + 대출 금액 추이 */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {paymentChartData && (
              <ChartCard title="월 상환금 추이">
                <Line data={paymentChartData} options={CHART_OPTS} />
              </ChartCard>
            )}
            {loanAmountChartData && (
              <ChartCard title="대출 금액 추이">
                <Line data={loanAmountChartData} options={CHART_OPTS} />
              </ChartCard>
            )}
          </div>

          {/* 2행: DTI 추이 + DTI 적정/초과 비율 */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {dtiChartData && (
              // 최근 12회 기준 (리포트는 최근 5회, #54), 100% 초과 막대도 보이게 suggestedMax (#52)
              <ChartCard title="DTI 추이 (최근 12회, 초록=적정 / 빨강=초과 / 회색=판단 불가)">
                <Bar data={dtiChartData}
                  options={{ ...CHART_OPTS,
                    scales: { y: { suggestedMax: 100, title: { display: true, text: 'DTI (%)' } } }
                  }} />
              </ChartCard>
            )}
            {dtiStatusPieData && (
              <ChartCard title={`DTI 적정 / 초과 비율 (${scope})`}>
                <div className="flex justify-center">
                  <div className="w-56 h-56">
                    <Doughnut data={dtiStatusPieData}
                      options={{ ...CHART_OPTS,
                        plugins: { legend: { position: 'bottom' } }
                      }} />
                  </div>
                </div>
                <p className="text-xs text-center text-gray-400 mt-2">
                  {partial ? `최근 ${history.length}회 시뮬레이션 기준 (전체 ${totalSims}회)` : `전체 ${history.length}회 시뮬레이션 기준`}
                </p>
              </ChartCard>
            )}
          </div>

          {/* 3행: 최근 시뮬레이션 원금 vs 이자 */}
          {interestPieData && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <ChartCard title="최근 시뮬레이션 — 원금 vs 총 이자">
                <div className="flex justify-center">
                  <div className="w-56 h-56">
                    <Doughnut data={interestPieData}
                      options={{ ...CHART_OPTS,
                        plugins: { legend: { position: 'bottom' } }
                      }} />
                  </div>
                </div>
                <div className="flex justify-center gap-6 mt-3 text-sm">
                  <div className="text-center">
                    <p className="text-xs text-gray-500 dark:text-gray-400">대출 원금</p>
                    <p className="font-semibold text-blue-600 dark:text-blue-400">
                      ₩{Number(history[0].loanAmount).toLocaleString()}
                    </p>
                  </div>
                  <div className="text-center">
                    <p className="text-xs text-gray-500 dark:text-gray-400">총 이자</p>
                    <p className="font-semibold text-amber-600 dark:text-amber-400">
                      ₩{Number(history[0].totalInterest).toLocaleString()}
                    </p>
                  </div>
                </div>
              </ChartCard>

              {/* 최근 시뮬레이션 테이블을 이 행에 같이 */}
              <div className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-xs">
                <h2 className="text-base font-semibold text-gray-900 dark:text-white mb-4">최근 시뮬레이션</h2>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm text-left">
                    <thead>
                      <tr className="border-b border-gray-200 dark:border-gray-700">
                        {['대출 금액', '월 상환금', 'DTI', '상태'].map(h => (
                          <th key={h} className="pb-2 pr-4 font-medium text-gray-500 dark:text-gray-400">{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {history.slice(0, 5).map((s, idx) => (
                        <tr key={idx} className="border-b border-gray-100 dark:border-gray-800">
                          <td className="py-2 pr-4 text-gray-800 dark:text-gray-200">₩{Number(s.loanAmount).toLocaleString()}</td>
                          <td className="py-2 pr-4 text-gray-800 dark:text-gray-200">₩{Number(s.monthlyPayment).toLocaleString()}</td>
                          <td className="py-2 pr-4 text-gray-800 dark:text-gray-200">
                            {/* DTI가 없으면 NaN% 대신 — */}
                            {s.dti != null ? `${(s.dti * 100).toFixed(1)}%` : '—'}
                            {/* 전환 전 기록은 기존 대출 이자가 빠진 값이라 표시 */}
                            {isNewLoanOnly(s) && (
                              <span className="block text-xs text-gray-400 dark:text-gray-500">{NEW_LOAN_ONLY_NOTE}</span>
                            )}
                          </td>
                          <td className="py-2 pr-4"><DtiBadge status={s.dtiStatus} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}
        </>
      ) : (
        <div className="bg-white dark:bg-gray-800 rounded-xl p-8 text-center border border-gray-100 dark:border-gray-700">
          <p className="text-gray-500 dark:text-gray-400 text-sm">
            아직 대출 시뮬레이션이 없습니다. 대출 시뮬레이터를 사용해보세요.
          </p>
        </div>
      )}
    </div>
  )
}
