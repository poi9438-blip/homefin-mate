import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import RiskPanel from './RiskPanel'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

let container
let root

beforeEach(() => {
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
})

const EVALUATED = {
  status: 'EVALUATED', available: true, productCode: 'BOGEUMJARI', verdict: 'REVIEW', riskLevel: 'MEDIUM',
  riskScore: 7, criteriaVersion: '2026-10-26', recommendation: '검토 필요: 주택 보유',
  reasons: [
    { code: 'HOUSE_OWNERSHIP', name: '주택 보유', result: 'WARN', detail: '1주택은 처분 조건 확인 필요' },
    { code: 'DTI_LIMIT', name: 'DTI', result: 'PASS', detail: 'DTI 38.01% ≤ 60%' },
  ],
  notEvaluated: ['신용점수', '순자산'],
}

describe('리스크 평가 표시', () => {
  it('[TC-WEB-228] 판정함은 한국어 판정·상품·기준 버전·근거·판정하지 않은 기준, 판정 안 함은 서버 이유 (이용 불가 안내 아님)', async () => {
    await act(async () => {
      root.render(<RiskPanel risk={EVALUATED} />)
    })
    const text = container.textContent
    expect(text).toContain('검토 필요')
    expect(text).not.toContain('REVIEW')
    expect(text).toContain('보금자리론')
    expect(text).toContain('기준 버전 2026-10-26')
    expect(text).toContain('주택 보유확인 필요')
    expect(text).toContain('DTI충족')
    expect(text).toContain('1주택은 처분 조건 확인 필요')
    expect(text).toContain('판정하지 않은 기준: 신용점수, 순자산')
    expect(text).not.toContain('일시적으로 이용 불가')

    const reason = '맞벌이 여부를 입력하면 보금자리론 심사 기준으로 판정합니다.'
    await act(async () => {
      root.render(<RiskPanel risk={{ status: 'NOT_EVALUATED', available: false, riskLevel: 'UNKNOWN', recommendation: reason }} />)
    })
    expect(container.textContent).toContain('심사 기준 판정 안 함')
    expect(container.textContent).toContain(reason)
    expect(container.textContent).not.toContain('일시적으로 이용 불가')

    // 불렀다가 실패(UNAVAILABLE)는 예전과 같은 이용 불가 안내
    await act(async () => {
      root.render(<RiskPanel risk={{ status: 'UNAVAILABLE', available: false, recommendation: 'Risk evaluation service temporarily unavailable.' }} />)
    })
    expect(container.textContent).toContain('RiskWatch 서비스가 일시적으로 이용 불가합니다.')
    expect(container.textContent).not.toContain('temporarily')
  })
})
