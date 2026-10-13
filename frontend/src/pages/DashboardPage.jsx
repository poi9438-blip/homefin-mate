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
import { reportApi } from '../api/reportApi'
import { analysisApi } from '../api/analysisApi'

// 꺾은선,막대,도넛 차트에 필요한 chart.js 부품 등록
ChartJS.register(
  CategoryScale, LinearScale, PointElement, LineElement,
  BarElement, ArcElement, Title, Tooltip, Legend
)

const REGIONS = ['Sydney', 'Melbourne', 'Brisbane', 'Perth', 'Adelaide', 'Darwin', 'Hobart',
                 '서울', '부산', '대구', '인천', '광주']

// 요약 기본값 (리포트 실패 시)
const FALLBACK_SUMMARY = {
  totalSimulations: 0,
  lastDsr: null,
  policyMatches: 0,
  riskLevel: 'UNKNOWN',
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

// DSR 추이 막대 (적정 초록 / 초과 빨강)
function buildDsrTrendData(history) {
  if (!history?.length) return null
  const recent = [...history].slice(0, 12).reverse()
  return {
    labels: recent.map((_, i) => `${i + 1}회차`),
    datasets: [{
      label: 'DSR (%)',
      data: recent.map(s => Number((s.dsr * 100).toFixed(1)) || 0),
      backgroundColor: recent.map(s =>
        s.dsrStatus === 'ACCEPTABLE' ? '#22c55e' : '#ef4444'
      ),
    }],
  }
}

// DSR 적정/초과 비율 (전체 이력)
function buildDsrStatusPieData(history) {
  if (!history?.length) return null
  const acceptable = history.filter(s => s.dsrStatus === 'ACCEPTABLE').length
  const exceeded   = history.length - acceptable
  if (acceptable === 0 && exceeded === 0) return null
  return {
    labels: ['적정', '초과'],
    datasets: [{
      data: [acceptable, exceeded],
      backgroundColor: ['#22c55e', '#ef4444'],
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
        const res = await loanApi.getHistory()
        if (!cancelled) setHistory(res.data.data ?? [])
      } catch {
        if (!cancelled) setHistory([])
      }
      try {
        const res = await reportApi.getSummary()
        if (!cancelled) setSummary(res.data.data ?? FALLBACK_SUMMARY)
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
  const dsrChartData        = buildDsrTrendData(history)
  const dsrStatusPieData    = buildDsrStatusPieData(history)
  const interestPieData     = buildInterestPieData(history)

  // 요약 카드 값 (리포트 값이 없으면 계산 이력으로 대체)
  const lastDsrRaw     = summary?.lastDsr ?? (history?.length ? history[0].dsr : null)
  const lastDsrDisplay = lastDsrRaw != null ? `${(lastDsrRaw * 100).toFixed(1)}%` : '—'
  const lastDsrStatus  = lastDsrRaw != null
    ? (lastDsrRaw <= 0.43 ? '적정 범위' : '높음 — 예산 검토 필요')
    : ''

  const riskLevel = summary?.riskLevel ?? 'UNKNOWN'
  const riskColor = riskLevel === 'LOW' ? '🟢' : riskLevel === 'HIGH' ? '🔴' : '🟡'

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
          value={String(summary?.totalSimulations ?? history?.length ?? 0)}
          icon="🏦"
        />
        <SummaryCard
          title="최근 DSR"
          value={lastDsrDisplay}
          icon="📊"
          subtitle={lastDsrStatus}
        />
        <SummaryCard
          title="적합 정책 수"
          value={String(summary?.policyMatches ?? 0)}
          icon="📋"
        />
        <SummaryCard
          title="리스크 수준"
          value={`${riskColor} ${riskLevel}`}
          icon="🛡"
          subtitle="최근 평가 기준"
        />
      </div>

      {/* 지역 분석 */}
      <div className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-sm">
        <h2 className="text-base font-semibold text-gray-900 dark:text-white mb-3">지역 분석</h2>
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">지역 선택</label>
            <select
              value={region}
              onChange={e => { setRegion(e.target.value); setRegionData(null); setRegionError('') }}
              className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              <option value="">— 선택하세요 —</option>
              {REGIONS.map(r => <option key={r} value={r.toLowerCase()}>{r}</option>)}
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
          <div className="mt-4 grid grid-cols-3 gap-4">
            <div className="text-center">
              <p className="text-xs text-gray-500 dark:text-gray-400">평균 가격</p>
              <p className="text-lg font-bold text-gray-900 dark:text-white">
                ₩{Number(regionData.averagePrice ?? regionData.average_price).toLocaleString()}
              </p>
            </div>
            <div className="text-center">
              <p className="text-xs text-gray-500 dark:text-gray-400">성장률</p>
              <p className="text-lg font-bold text-gray-900 dark:text-white">
                {(Number(regionData.growthRate ?? regionData.growth_rate) * 100).toFixed(1)}%
              </p>
            </div>
            <div className="text-center">
              <p className="text-xs text-gray-500 dark:text-gray-400">시장 리스크</p>
              <p className="text-lg font-bold text-gray-900 dark:text-white">
                {Number(regionData.riskScore ?? regionData.risk_score).toFixed(1)}
              </p>
            </div>
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

          {/* 2행: DSR 추이 + DSR 적정/초과 비율 */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {dsrChartData && (
              <ChartCard title="시뮬레이션별 DSR (초록=적정 / 빨강=초과)">
                <Bar data={dsrChartData}
                  options={{ ...CHART_OPTS,
                    scales: { y: { max: 100, title: { display: true, text: 'DSR (%)' } } }
                  }} />
              </ChartCard>
            )}
            {dsrStatusPieData && (
              <ChartCard title="DSR 적정 / 초과 비율">
                <div className="flex justify-center">
                  <div className="w-56 h-56">
                    <Doughnut data={dsrStatusPieData}
                      options={{ ...CHART_OPTS,
                        plugins: { legend: { position: 'bottom' } }
                      }} />
                  </div>
                </div>
                <p className="text-xs text-center text-gray-400 mt-2">
                  전체 {history.length}회 시뮬레이션 기준
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
              <div className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-sm">
                <h2 className="text-base font-semibold text-gray-900 dark:text-white mb-4">최근 시뮬레이션</h2>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm text-left">
                    <thead>
                      <tr className="border-b border-gray-200 dark:border-gray-700">
                        {['대출 금액', '월 상환금', 'DSR', '상태'].map(h => (
                          <th key={h} className="pb-2 pr-4 font-medium text-gray-500 dark:text-gray-400">{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {history.slice(0, 5).map((s, idx) => (
                        <tr key={idx} className="border-b border-gray-100 dark:border-gray-800">
                          <td className="py-2 pr-4 text-gray-800 dark:text-gray-200">₩{Number(s.loanAmount).toLocaleString()}</td>
                          <td className="py-2 pr-4 text-gray-800 dark:text-gray-200">₩{Number(s.monthlyPayment).toLocaleString()}</td>
                          <td className="py-2 pr-4 text-gray-800 dark:text-gray-200">{(s.dsr * 100).toFixed(1)}%</td>
                          <td className="py-2 pr-4">
                            <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                              s.dsrStatus === 'ACCEPTABLE'
                                ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
                                : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
                            }`}>
                              {s.dsrStatus === 'ACCEPTABLE' ? '적정' : '초과'}
                            </span>
                          </td>
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
