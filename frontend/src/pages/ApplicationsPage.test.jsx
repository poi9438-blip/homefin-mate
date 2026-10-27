import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/applicationApi', () => ({ applicationApi: { list: vi.fn(), cancel: vi.fn() } }))

import { applicationApi } from '../api/applicationApi'
import ApplicationsPage from './ApplicationsPage'

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

const row = (applicationId, status, extra = {}) => ({
  applicationId, productCode: 'BOGEUMJARI', available: true, status, requestedAmount: 300000000, termMonths: 360,
  receivedAt: '2026-10-27T01:02:03Z', decidedAt: null, rejectReason: null, ...extra,
})

describe('내 신청 화면', () => {
  it('[TC-WEB-230] 상태 한국어, 취소 버튼은 접수·심사 중만, 상태를 못 가져온 건은 "상태 확인 불가"', async () => {
    const items = [
      row('a-received', 'RECEIVED'), row('a-reviewing', 'REVIEWING'), row('a-approved', 'APPROVED'),
      row('a-rejected', 'REJECTED', { rejectReason: '소득 기준 초과' }), row('a-cancelled', 'CANCELLED'),
      row('a-expired', 'EXPIRED'),
      { applicationId: 'a-unknown', productCode: 'DIDIMDOL', available: false },
    ]
    applicationApi.list.mockResolvedValue({ data: { data: items } })
    await act(async () => { root.render(<ApplicationsPage />) })
    await act(async () => { await Promise.resolve() })

    const rows = [...container.querySelectorAll('tbody tr')]
    expect(rows).toHaveLength(7)
    const text = (i) => rows[i].textContent
    expect(text(0)).toContain('보금자리론')
    for (const [i, label] of [[0, '접수'], [1, '심사 중'], [2, '승인'], [3, '거절'], [4, '취소'], [5, '만료']]) {
      expect(text(i)).toContain(label)
    }
    expect(text(3)).toContain('소득 기준 초과')
    expect(text(0)).toContain('300,000,000원')
    // 취소 버튼: 접수·심사 중 두 줄만
    const cancelButtons = rows.map(r => [...r.querySelectorAll('button')].length)
    expect(cancelButtons).toEqual([1, 1, 0, 0, 0, 0, 0])
    expect(text(6)).toContain('내집마련 디딤돌대출')
    expect(text(6)).toContain('상태 확인 불가')
    expect(text(6)).not.toContain('undefined')
  })
})
