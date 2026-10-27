import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/applicationApi', () => ({ applicationApi: { apply: vi.fn() } }))

import { applicationApi } from '../api/applicationApi'
import ApplyPanel from './ApplyPanel'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

let container
let root

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
  vi.restoreAllMocks()
})

const POLICY = { recommendedProduct: '보금자리론', recommendedProductCode: 'BOGEUMJARI' }
const ANALYSIS = { loanAmount: 300000000, termMonths: 360, propertyType: 'NONE', dualIncome: false }

async function render(props) {
  await act(async () => {
    root.render(<MemoryRouter><ApplyPanel {...props} /></MemoryRouter>)
  })
}

const button = () => [...container.querySelectorAll('button')].find(b => b.textContent.includes('신청'))

async function click() {
  await act(async () => { button().click() })
  await act(async () => { await Promise.resolve() })
}

describe('대출 신청 버튼', () => {
  it('[TC-WEB-229] 추천 상품이 있을 때만 버튼, 실패 후 다시 누르면 같은 Idempotency-Key, 성공하면 신청 번호와 내 신청 링크', async () => {
    // 추천 상품 없음 → 아무것도 안 보임
    await render({ policy: { recommendedProduct: null, recommendedProductCode: null }, analysis: ANALYSIS })
    expect(container.textContent).toBe('')

    await render({ policy: POLICY, analysis: ANALYSIS, simulationId: 42 })
    expect(button().textContent).toBe('이 상품으로 신청')

    // 첫 번째: 서버 오류 → 안내, 버튼 그대로
    applicationApi.apply.mockRejectedValueOnce({ response: { status: 503, data: { message: 'Application service unavailable' } } })
    await click()
    expect(container.textContent).toContain('잠시 후')
    expect(button()).toBeTruthy()

    // 두 번째: 성공 → 신청 번호·링크, 버튼 없음
    applicationApi.apply.mockResolvedValueOnce({ data: { data: { applicationId: 'app-123', status: 'RECEIVED' } } })
    await click()
    expect(container.textContent).toContain('신청 번호 app-123')
    expect(container.querySelector('a[href="/applications"]').textContent).toBe('내 신청 보기')
    expect(button()).toBeUndefined()

    // 두 번 모두 같은 키, 본문은 상품 코드·계산 기록 id·분석 입력
    expect(applicationApi.apply).toHaveBeenCalledTimes(2)
    const [first, second] = applicationApi.apply.mock.calls
    expect(first[1]).toMatch(/^[0-9a-f-]{36}$/)
    expect(second[1]).toBe(first[1])
    expect(first[0]).toEqual({ productCode: 'BOGEUMJARI', simulationId: 42, analysis: ANALYSIS })
  })
})
