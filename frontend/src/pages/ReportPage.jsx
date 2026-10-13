import React, { useState, useEffect } from 'react'
import { Bar, Doughnut } from 'react-chartjs-2'
import {
  Chart as ChartJS, CategoryScale, LinearScale,
  BarElement, ArcElement, Title, Tooltip, Legend,
} from 'chart.js'
import { reportApi } from '../api/reportApi'
import LoadingState from '../components/LoadingState'
import ErrorState from '../components/ErrorState'
import ChartCard from '../components/ChartCard'

// 막대·도넛 차트에 필요한 chart.js 부품 등록
ChartJS.register(CategoryScale, LinearScale, BarElement, ArcElement, Title, Tooltip, Legend)

// 제목이 있는 흰색 구역
function Section({ title, children }) {
  return (
    <div className="bg-white dark:bg-gray-800 rounded-xl p-5 border border-gray-100 dark:border-gray-700 shadow-sm">
      <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-4">{title}</h3>
      {children}
    </div>
  )
}

// 이름 - 값 한 줄 (highlight면 강조색)
function DataRow({ label, value, highlight }) {
  return (
    <div className="flex justify-between items-center py-2 border-b border-gray-100 dark:border-gray-700 last:border-0">
      <span className="text-sm text-gray-600 dark:text-gray-400">{label}</span>
      <span className={`text-sm font-medium ${highlight ? 'text-primary-600 dark:text-primary-400' : 'text-gray-900 dark:text-white'}`}>
        {value}
      </span>
    </div>
  )
}

// DSR 판정 배지 (적정 / 초과)
function DsrBadge({ status }) {
  return (
    <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
      status === 'ACCEPTABLE'
        ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
        : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
    }`}>
      {status === 'ACCEPTABLE' ? '적정' : '초과'}
    </span>
  )
}

// 재무 리포트 화면 (요약, 차트, DSR 추이)
export default function ReportPage() {
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState(null)

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await reportApi.getSummary()
      setSummary(res.data.data ?? res.data)
    } catch (err) {
      setError(err.response?.data?.message || '리포트를 불러오는데 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  // 화면이 처음 열릴 때 한 번 불러오기
  useEffect(() => { load() }, [])

  if (loading) return <LoadingState message="리포트 로딩 중..." />
  if (error)   return <ErrorState message={error} onRetry={load} />

  const data      = summary?.reportData ?? {}
  const lastSim   = data.lastSimulation
  const dsrTrend  = data.dsrTrend ?? []
  const totalSims = data.totalSimulations ?? 0
  const hint      = data.eligibilityHint
  const noData    = !lastSim && totalSims === 0
  const lastRegion = lastSim?.region

  // 금액(₩), DSR(%), 날짜 형식
  const fmt     = (n, prefix = '₩') => n != null ? `${prefix}${Number(n).toLocaleString()}` : '—'
  const fmtDsr  = d => d != null ? `${(Number(d) * 100).toFixed(1)}%` : '—'
  const fmtDate = dt => dt ? new Date(dt).toLocaleDateString() : '—'

  // DSR 추이 바차트
  const dsrBarData = dsrTrend.length > 0 ? {
    labels: dsrTrend.map((_, i) => `${i + 1}회차`),
    datasets: [{
      label: 'DSR (%)',
      data: dsrTrend.map(s => Number((Number(s.dsr) * 100).toFixed(1))),
      backgroundColor: dsrTrend.map(s => s.dsrStatus === 'ACCEPTABLE' ? '#22c55e' : '#ef4444'),
      borderRadius: 4,
    }],
  } : null

  // 43% 기준선 플러그인용 annotation은 생략하고, 설명 텍스트로 대체
  // 최근 시뮬레이션 원금 vs 이자 도넛
  const interestPieData = lastSim ? (() => {
    const principal = Number(lastSim.loanAmount) || 0
    const interest  = Number(lastSim.totalInterest) || 0
    if (!principal && !interest) return null
    const total = principal + interest
    return {
      chartData: {
        labels: ['대출 원금', '총 이자'],
        datasets: [{
          data: [principal, interest],
          backgroundColor: ['#3b82f6', '#f59e0b'],
          borderWidth: 0,
        }],
      },
      principal,
      interest,
      total,
      interestRatio: total > 0 ? ((interest / total) * 100).toFixed(1) : 0,
    }
  })() : null

  // DSR 적정/초과 파이
  const dsrStatusPieData = dsrTrend.length > 0 ? (() => {
    const acceptable = dsrTrend.filter(s => s.dsrStatus === 'ACCEPTABLE').length
    const exceeded   = dsrTrend.length - acceptable
    return {
      labels: ['적정', '초과'],
      datasets: [{
        data: [acceptable, exceeded],
        backgroundColor: ['#22c55e', '#ef4444'],
        borderWidth: 0,
      }],
    }
  })() : null

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">재무 리포트</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            대출 시뮬레이션 결과 및 적격성 평가 요약
          </p>
        </div>
        <button onClick={load}
          className="px-4 py-2 text-sm text-gray-600 dark:text-gray-300 border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700">
          새로고침
        </button>
      </div>

      {noData ? (
        <div className="bg-white dark:bg-gray-800 rounded-xl p-10 text-center border border-gray-100 dark:border-gray-700">
          <p className="text-gray-500 dark:text-gray-400 text-sm mb-4">
            시뮬레이션 데이터가 없습니다. 대출 시뮬레이터를 사용해 리포트를 생성해보세요.
          </p>
          <a href="/loan" className="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm hover:bg-primary-700">
            대출 시뮬레이터로 이동
          </a>
        </div>
      ) : (
        <>
          {/* 개요 */}
          <Section title="개요">
            <DataRow label="총 시뮬레이션 횟수" value={String(totalSims)} highlight />
            {lastSim && (
              <>
                <DataRow label="최근 시뮬레이션 날짜" value={fmtDate(lastSim.createdAt)} />
                <DataRow label="최근 대출 금액"        value={fmt(lastSim.loanAmount)} />
                <DataRow label="최근 월 상환금"        value={fmt(lastSim.monthlyPayment)} />
                <DataRow label="최근 총 이자"          value={fmt(lastSim.totalInterest)} />
                {lastRegion && (
                  <DataRow label="지역" value={lastRegion.charAt(0).toUpperCase() + lastRegion.slice(1)} />
                )}
                <DataRow
                  label="최근 DSR"
                  value={
                    <span className="flex items-center gap-2">
                      {fmtDsr(lastSim.dsr)}
                      <DsrBadge status={lastSim.dsrStatus} />
                    </span>
                  }
                />
              </>
            )}
          </Section>

          {hint && (
            <div className="rounded-xl p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-700">
              <p className="text-sm text-blue-800 dark:text-blue-200">💡 {hint}</p>
            </div>
          )}

          {/* 차트 섹션 */}
          {(interestPieData || dsrStatusPieData) && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              {/* 원금 vs 이자 도넛 */}
              {interestPieData && (
                <ChartCard title="최근 시뮬레이션 — 원금 vs 총 이자">
                  <div className="flex justify-center">
                    <div className="w-48 h-48">
                      <Doughnut data={interestPieData.chartData}
                        options={{ plugins: { legend: { position: 'bottom' } }, cutout: '60%' }} />
                    </div>
                  </div>
                  <div className="mt-3 grid grid-cols-3 gap-2 text-center text-xs">
                    <div>
                      <p className="text-gray-500 dark:text-gray-400">원금</p>
                      <p className="font-semibold text-blue-600 dark:text-blue-400">
                        {fmt(interestPieData.principal)}
                      </p>
                    </div>
                    <div>
                      <p className="text-gray-500 dark:text-gray-400">총 이자</p>
                      <p className="font-semibold text-amber-600 dark:text-amber-400">
                        {fmt(interestPieData.interest)}
                      </p>
                    </div>
                    <div>
                      <p className="text-gray-500 dark:text-gray-400">이자 비율</p>
                      <p className="font-semibold text-gray-800 dark:text-gray-200">
                        {interestPieData.interestRatio}%
                      </p>
                    </div>
                  </div>
                </ChartCard>
              )}

              {/* DSR 적정/초과 파이 */}
              {dsrStatusPieData && (
                <ChartCard title="DSR 적정 / 초과 비율">
                  <div className="flex justify-center">
                    <div className="w-48 h-48">
                      <Doughnut data={dsrStatusPieData}
                        options={{ plugins: { legend: { position: 'bottom' } }, cutout: '60%' }} />
                    </div>
                  </div>
                  <p className="text-xs text-center text-gray-400 mt-2 dark:text-gray-500">
                    최근 {dsrTrend.length}회 시뮬레이션 기준 · DSR 43% 기준
                  </p>
                </ChartCard>
              )}
            </div>
          )}

          {/* DSR 추이 바차트 */}
          {dsrBarData && (
            <ChartCard title="DSR 추이 (초록=적정 / 빨강=초과, 기준선 43%)">
              <Bar data={dsrBarData}
                options={{
                  responsive: true,
                  plugins: { legend: { display: false } },
                  scales: {
                    y: {
                      max: 100,
                      title: { display: true, text: 'DSR (%)' },
                    },
                  },
                }} />
            </ChartCard>
          )}

          {/* DSR 추이 테이블 */}
          {dsrTrend.length > 0 && (
            <Section title="DSR 추이 상세 (최근 5회 시뮬레이션)">
              <div className="overflow-x-auto">
                <table className="w-full text-sm text-left">
                  <thead>
                    <tr className="border-b border-gray-200 dark:border-gray-700">
                      {['#', '월 상환금', 'DSR', '상태', '날짜'].map(h => (
                        <th key={h} className="pb-2 pr-4 font-medium text-gray-500 dark:text-gray-400">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {dsrTrend.map((s, i) => (
                      <tr key={i} className="border-b border-gray-100 dark:border-gray-800">
                        <td className="py-2 pr-4 text-gray-500 dark:text-gray-400">{s.simulationId}</td>
                        <td className="py-2 pr-4 text-gray-800 dark:text-gray-200">{fmt(s.monthlyPayment)}</td>
                        <td className="py-2 pr-4 text-gray-800 dark:text-gray-200">{fmtDsr(s.dsr)}</td>
                        <td className="py-2 pr-4"><DsrBadge status={s.dsrStatus} /></td>
                        <td className="py-2 pr-4 text-gray-500 dark:text-gray-400">{fmtDate(s.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Section>
          )}

          <p className="text-xs text-gray-400 dark:text-gray-500">
            생성 시각 {new Date().toLocaleString()} · 트레이스 ID: {summary?.traceId ?? '—'}
          </p>
        </>
      )}
    </div>
  )
}
