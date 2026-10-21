import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// API는 가짜로 바꾸고(실제 서버 없이), 차트는 jsdom에서 그릴 수 없어 빈 컴포넌트로 대체
vi.mock('../api/analyzeApi', () => ({ analyzeApi: { analyze: vi.fn() } }))
vi.mock('../api/policyApi', () => ({ policyApi: { recommend: vi.fn() } }))
vi.mock('react-chartjs-2', () => ({ Doughnut: () => null }))

import { analyzeApi } from '../api/analyzeApi'
import { policyApi } from '../api/policyApi'
import LoanPage from './LoanPage'
import PolicyPage from './PolicyPage'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

// 서버 응답 예시 (디딤돌 추천)
const POLICY_RESULT = {
  recommendedProduct: '내집마련 디딤돌대출',
  recommendationReason: '두 상품 모두 조건을 충족합니다.',
  eligibleCount: 2,
  summary: '입력한 정보 기준으로 2개 상품 중 조건 충족 2개, 확인 필요 0개입니다.',
  recommendedPolicies: [
    { name: '내집마련 디딤돌대출', provider: '주택도시기금', eligibility: 'ELIGIBLE', conditions: [],
      additionalChecks: [], maxLoanAmount: 200000000, interestRate: '연 2.85~4.15%', sourceUrl: 'https://nhuf.molit.go.kr', checkedDate: '2026-10-16' },
    { name: '보금자리론', provider: '한국주택금융공사', eligibility: 'ELIGIBLE', conditions: [],
      additionalChecks: [], maxLoanAmount: 280000000, interestRate: '연 4.90~5.30%', sourceUrl: 'https://www.hf.go.kr', checkedDate: '2026-10-16' },
  ],
}

let container
let root

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
})

const flush = () => act(async () => { await Promise.resolve() })

// 정책 화면으로 넘어온 state를 화면에 찍어 확인하는 가짜 페이지
function StateProbe() {
  return <pre data-testid="state">{JSON.stringify(useLocation().state)}</pre>
}

describe('대출 화면 → 정책 화면 연결', () => {
  // 대출 화면 입력(정책 판정용 포함)이 통합 분석 요청에 들어가고, 추천 카드에서 정책 화면으로 값이 넘어가는지
  it('[TC-POL-303] 대출 화면의 정책 입력을 분석 요청에 넣고, 추천 카드의 자세히 보기로 입력값과 DSR을 정책 화면에 전달', async () => {
    localStorage.setItem('hfm_draft_loan', JSON.stringify({
      loanAmount: '200000000', annualInterestRate: '4.5', termMonths: '360', monthlyIncome: '4000000',
      existingDebt: '', region: '부산', propertyType: 'NONE',
      housePrice: '400000000', householdAnnualIncome: '48000000', firstHome: 'true', newlywed: 'false', childCount: '0',
    }))
    analyzeApi.analyze.mockResolvedValue({ data: { data: {
      loan: { monthlyPayment: 1013370.62, totalInterest: 164813423.2, totalPayment: 364813423.2, dsr: 0.2533, dsrStatus: 'ACCEPTABLE' },
      policy: POLICY_RESULT, risk: { available: false }, region: null,
    } } })

    await act(async () => {
      root.render(
        <MemoryRouter initialEntries={['/loan']}>
          <Routes>
            <Route path="/loan" element={<LoanPage />} />
            <Route path="/policy" element={<StateProbe />} />
          </Routes>
        </MemoryRouter>,
      )
    })
    await act(async () => { container.querySelector('form').requestSubmit() })
    await flush()

    expect(analyzeApi.analyze.mock.calls[0][0]).toMatchObject({
      housePrice: 400000000, householdAnnualIncome: 48000000, firstHome: true, newlywed: false, childCount: 0,
    })
    expect(container.textContent).toContain('내집마련 디딤돌대출')

    const detail = [...container.querySelectorAll('button')].find(b => b.textContent.includes('자세히 보기'))
    await act(async () => { detail.click() })

    const state = JSON.parse(container.querySelector('[data-testid="state"]').textContent)
    expect(state.prefill).toMatchObject({ dsr: '0.2533', housePrice: '400000000', firstHome: 'true', income: '4000000' })
  })

  // 정책 화면은 넘어온 값으로 바로 판정을 요청함 (다시 입력하지 않음)
  it('[TC-POL-304] 정책 화면은 대출 화면에서 넘어온 값(DSR 포함)으로 바로 판정을 요청하고 추천 결과를 보여 줌', async () => {
    policyApi.recommend.mockResolvedValue({ data: { data: POLICY_RESULT } })
    const prefill = {
      income: '4000000', existingDebt: '', loanAmount: '200000000', dsr: '0.2533', propertyType: 'NONE', region: '부산',
      housePrice: '400000000', householdAnnualIncome: '48000000', firstHome: 'true', newlywed: 'false', childCount: '0',
    }

    await act(async () => {
      root.render(
        <MemoryRouter initialEntries={[{ pathname: '/policy', state: { prefill } }]}>
          <Routes><Route path="/policy" element={<PolicyPage />} /></Routes>
        </MemoryRouter>,
      )
    })
    await flush()

    expect(policyApi.recommend).toHaveBeenCalledTimes(1)
    expect(policyApi.recommend.mock.calls[0][0]).toMatchObject({
      income: 4000000, loanAmount: 200000000, dsr: 0.2533, housePrice: 400000000,
      householdAnnualIncome: 48000000, firstHome: true, newlywed: false, childCount: 0, region: '부산',
    })
    expect(container.textContent).toContain('두 상품 모두 조건을 충족합니다.')
  })
  // 정책 판정 입력은 필수: 하나라도 비우면 요청을 보내지 않고 입력칸 아래에 안내를 보여 줌
  it('[TC-POL-306] 정책 판정 입력(생애최초·자녀 수)을 비우면 분석 요청을 보내지 않고 입력 안내를 보여 줌', async () => {
    localStorage.setItem('hfm_draft_loan', JSON.stringify({
      loanAmount: '200000000', annualInterestRate: '4.5', termMonths: '360', monthlyIncome: '4000000',
      existingDebt: '', region: '부산', propertyType: 'NONE',
      housePrice: '400000000', householdAnnualIncome: '48000000', firstHome: '', newlywed: 'false', childCount: '',
    }))

    await act(async () => {
      root.render(<MemoryRouter initialEntries={['/loan']}><LoanPage /></MemoryRouter>)
    })
    await act(async () => { container.querySelector('form').requestSubmit() })
    await flush()

    expect(analyzeApi.analyze).not.toHaveBeenCalled()
    expect(container.textContent).toContain('생애최초 여부를 선택해주세요.')
    expect(container.textContent).toContain('자녀 수를 입력해주세요.')
    expect(container.querySelector('select[name="firstHome"] option[value=""]').textContent).toBe('선택')
  })
})
