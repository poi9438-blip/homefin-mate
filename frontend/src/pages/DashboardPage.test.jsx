import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// API는 가짜로 바꾸고, 차트는 jsdom에서 그릴 수 없어 빈 컴포넌트로 대체
vi.mock('../api/loanApi', () => ({ loanApi: { getHistory: vi.fn() } }))
vi.mock('../api/reportApi', () => ({ reportApi: { getSummary: vi.fn() } }))
vi.mock('../api/analysisApi', () => ({ analysisApi: { getRegionAnalysis: vi.fn() } }))
vi.mock('react-chartjs-2', () => ({ Line: () => null, Bar: () => null, Doughnut: () => null }))

import { loanApi } from '../api/loanApi'
import { reportApi } from '../api/reportApi'
import DashboardPage from './DashboardPage'

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

// 요약 카드 제목으로 카드 값 찾기
const card = title =>
  [...container.querySelectorAll('div')].find(d => d.firstElementChild?.textContent === title && d.children.length >= 2)

describe('대시보드 요약 카드', () => {
  // 카드 값은 리포트 reportData와 계산 이력에서 (판단 불가 기록은 비율에서 뺌)
  it('[TC-WEB-213] 요약 카드는 리포트 reportData의 총 계산 수·최근 DTI·최근 월 상환금, 이력의 DTI 적정 비율을 보여 줌 (#49)', async () => {
    loanApi.getHistory.mockResolvedValue({ data: { data: [
      { loanAmount: 300000000, monthlyPayment: 1520056, totalInterest: 247220160, dti: 0.3801, dtiStatus: 'ACCEPTABLE' },
      { loanAmount: 300000000, monthlyPayment: 1520056, totalInterest: 247220160, dti: 0.6081, dtiStatus: 'HIGH' },
      { loanAmount: 100000000, monthlyPayment: 1012451, totalInterest: 21494120, dti: 0.25, dtiStatus: 'ACCEPTABLE' },
      { loanAmount: 100000000, monthlyPayment: 1012451, totalInterest: 21494120, dti: null, dtiStatus: 'UNKNOWN' },
    ] } })
    reportApi.getSummary.mockResolvedValue({ data: { data: { reportId: 7, reportData: {
      totalSimulations: 12,
      lastDti: 0.3801,
      lastSimulation: { monthlyPayment: 1520056 },
    } } } })

    await act(async () => { root.render(<DashboardPage />) })
    await act(async () => { await Promise.resolve() })

    expect(card('총 시뮬레이션').textContent).toContain('12')
    expect(card('최근 DTI').textContent).toContain('38.0%')
    expect(card('최근 월 상환금').textContent).toContain('₩1,520,056')
    expect(card('DTI 적정 비율').textContent).toContain('67%')
    expect(card('DTI 적정 비율').textContent).toContain('전체 3회 중 적정 2회')
    expect(container.textContent).not.toContain('적합 정책 수')
    expect(container.textContent).not.toContain('리스크 수준')
    const statuses = [...container.querySelectorAll('tbody tr')].map(r => r.lastElementChild.textContent)
    expect(statuses).toEqual(['적정', '초과', '적정', '판단 불가'])
  })
})
