import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// API는 가짜로 바꾸고, 차트는 jsdom에서 그릴 수 없어 빈 컴포넌트로 대체
vi.mock('../api/reportApi', () => ({ reportApi: { getSummary: vi.fn() } }))
vi.mock('react-chartjs-2', () => ({ Bar: () => null, Doughnut: () => null }))

import { reportApi } from '../api/reportApi'
import ReportPage from './ReportPage'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

let container
let root

beforeEach(() => {
  vi.clearAllMocks()
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
})

// 리포트 화면 그리기 (라우터 안, /loan은 확인용 글자)
async function renderReport(reportData, extra = {}) {
  reportApi.getSummary.mockResolvedValue({ data: { data: { reportData, ...extra } } })
  await act(async () => {
    root.render(
      <MemoryRouter initialEntries={['/report']}>
        <Routes>
          <Route path="/report" element={<ReportPage />} />
          <Route path="/loan" element={<p>대출 화면</p>} />
        </Routes>
      </MemoryRouter>,
    )
  })
  await act(async () => { await Promise.resolve() })
}

describe('리포트 DTI 추이', () => {
    it('[TC-WEB-207] DTI 추이 표에서 전환 전 기록(NEW_LOAN_ONLY)에만 "기존 대출 이자 미포함" 표시', async () => {
    await renderReport({
      totalSimulations: 2,
      lastSimulation: { simulationId: 2, loanAmount: 300000000, monthlyPayment: 1520055.93, totalInterest: 247220134.8,
        dti: 0.505, dtiStatus: 'ACCEPTABLE', dtiBasis: 'WITH_EXISTING_INTEREST', createdAt: '2026-10-23T10:00:00Z' },
      dtiTrend: [
        { simulationId: 2, monthlyPayment: 1520055.93, dti: 0.505, dtiStatus: 'ACCEPTABLE', dtiBasis: 'WITH_EXISTING_INTEREST', createdAt: '2026-10-23T10:00:00Z' },
        { simulationId: 1, monthlyPayment: 1520055.93, dti: 0.38, dtiStatus: 'ACCEPTABLE', dtiBasis: 'NEW_LOAN_ONLY', createdAt: '2026-10-01T10:00:00Z' },
      ],
    })

    const rows = [...container.querySelectorAll('tbody tr')]
    expect(rows).toHaveLength(2)
    expect(rows[0].textContent).toContain('기존 대출 이자 미포함')
    expect(rows[1].textContent).not.toContain('기존 대출 이자 미포함')
    expect(container.textContent).toContain('DTI 60% 기준')
  })

  // 추이는 과거 → 최근, DTI 없음은 판단 불가, 생성 시각은 서버 값
  it('[TC-WEB-211] DTI 추이는 과거 → 최근 순, DTI 없는 기록은 "판단 불가" 배지, 생성 시각은 서버가 보낸 시각 (#52, #53)', async () => {
    const generatedAt = '2026-10-25T03:04:05Z'
    await renderReport({
      totalSimulations: 3,
      lastSimulation: { simulationId: 3, loanAmount: 100000000, monthlyPayment: 1012451, totalInterest: 21494120,
        dti: 0.7, dtiStatus: 'HIGH', createdAt: '2026-10-24T10:00:00Z' },
      dtiTrend: [
        { simulationId: 3, monthlyPayment: 1012451, dti: 0.7, dtiStatus: 'HIGH', createdAt: '2026-10-24T10:00:00Z' },
        { simulationId: 2, monthlyPayment: 1012451, dti: null, dtiStatus: 'UNKNOWN', createdAt: '2026-10-23T10:00:00Z' },
        { simulationId: 1, monthlyPayment: 1012451, dti: 0.3, dtiStatus: 'ACCEPTABLE', createdAt: '2026-10-22T10:00:00Z' },
      ],
    }, { generatedAt, traceId: 'hfm-report' })

    const rows = [...container.querySelectorAll('tbody tr')]
    expect(rows.map(r => r.querySelector('td').textContent)).toEqual(['1', '2', '3'])
    expect(rows[0].textContent).toContain('적정')
    expect(rows[1].textContent).toContain('판단 불가')
    expect(rows[1].textContent).not.toContain('초과')
    expect(rows[2].textContent).toContain('초과')
    expect(container.textContent).toContain(`생성 시각 ${new Date(generatedAt).toLocaleString()}`)
  })

  // 기록이 없을 때 이동 버튼은 화면 이동(Link)
  it('[TC-WEB-212] 기록이 없을 때 "대출 시뮬레이터로 이동"은 페이지를 새로 불러오지 않고 화면만 이동 (#53)', async () => {
    await renderReport({ totalSimulations: 0 })

    const link = [...container.querySelectorAll('a')].find(a => a.textContent.includes('대출 시뮬레이터로 이동'))
    await act(async () => { link.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 })) })

    expect(container.textContent).toContain('대출 화면')
  })
})
