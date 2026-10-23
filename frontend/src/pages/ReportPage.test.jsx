import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
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

describe('리포트 DTI 추이', () => {
  it('[TC-WEB-207] DTI 추이 표에서 전환 전 기록(NEW_LOAN_ONLY)에만 "기존 대출 이자 미포함" 표시', async () => {
    reportApi.getSummary.mockResolvedValue({ data: { data: { reportData: {
      totalSimulations: 2,
      lastSimulation: { simulationId: 2, loanAmount: 300000000, monthlyPayment: 1520055.93, totalInterest: 247220134.8,
        dti: 0.505, dtiStatus: 'ACCEPTABLE', dtiBasis: 'WITH_EXISTING_INTEREST', createdAt: '2026-10-23T10:00:00' },
      dtiTrend: [
        { simulationId: 2, monthlyPayment: 1520055.93, dti: 0.505, dtiStatus: 'ACCEPTABLE', dtiBasis: 'WITH_EXISTING_INTEREST', createdAt: '2026-10-23T10:00:00' },
        { simulationId: 1, monthlyPayment: 1520055.93, dti: 0.38, dtiStatus: 'ACCEPTABLE', dtiBasis: 'NEW_LOAN_ONLY', createdAt: '2026-10-01T10:00:00' },
      ],
    } } } })

    await act(async () => { root.render(<ReportPage />) })
    await act(async () => { await Promise.resolve() })

    const rows = [...container.querySelectorAll('tbody tr')]
    expect(rows).toHaveLength(2)
    expect(rows[0].textContent).not.toContain('기존 대출 이자 미포함')
    expect(rows[1].textContent).toContain('기존 대출 이자 미포함')
    expect(container.textContent).toContain('DTI 60% 기준')
  })
})
