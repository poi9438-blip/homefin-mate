import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import RegionSummary from './RegionSummary'

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

describe('지역 분석 표시', () => {
  // 예시 데이터는 "예시 데이터 · 실제 시세 아님" 표시와 단위 없는 숫자, 분석 불가는 이유만 표시
  it('[TC-WEB-208] 예시 데이터는 ₩ 없이 "예시 데이터" 표시, 분석 불가(없는 지역·연결 실패)는 숫자 없이 이유만', async () => {
    await act(async () => {
      root.render(<RegionSummary data={{ region: '부산', averagePrice: 540000, growthRate: 0.072, riskScore: 2.3, sample: true, available: true }} />)
    })
    expect(container.textContent).toContain('예시 데이터 · 실제 시세 아님')
    expect(container.textContent).toContain('540,000')
    expect(container.textContent).toContain('7.2%')
    expect(container.textContent).not.toContain('₩')

    await act(async () => {
      root.render(<RegionSummary data={{ region: '경기', available: false, unavailableReason: 'UNSUPPORTED_REGION' }} />)
    })
    expect(container.textContent).toBe('이 지역은 지역 분석 데이터가 없습니다.')

    await act(async () => {
      root.render(<RegionSummary data={{ region: '서울', available: false, unavailableReason: 'SERVICE_UNAVAILABLE' }} />)
    })
    expect(container.textContent).toContain('일시적으로 이용할 수 없습니다')
    expect(container.textContent).not.toContain('NaN')
  })
})
