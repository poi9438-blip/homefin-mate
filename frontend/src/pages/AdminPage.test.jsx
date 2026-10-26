import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// 관리자 API는 가짜로 바꿈 (실제 서버 없이 호출 횟수·인자 확인)
vi.mock('../api/adminApi', () => ({
  adminApi: {
    getAuditLogs: vi.fn(),
    getLoginFailures: vi.fn(),
    searchLoginFailures: vi.fn(),
    getUserActivity: vi.fn(),
    getIntegrationFailures: vi.fn(),
  },
}))

import { adminApi } from '../api/adminApi'
import AdminPage from './AdminPage'

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

const flush = () => act(async () => { await Promise.resolve() })
const button = text => [...container.querySelectorAll('button')].find(b => b.textContent.trim() === text)
const click = el => act(async () => { el.dispatchEvent(new MouseEvent('click', { bubbles: true })) })

// 입력창에 글자 넣기
const type = (input, value) => act(async () => {
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value)
  input.dispatchEvent(new Event('input', { bubbles: true }))
})

describe('관리자 화면', () => {
  // 새로고침을 누르면 감사 기록을 다시 불러옴
  it('[TC-WEB-214] 새로고침 버튼을 누르면 현재 탭 기록을 다시 불러옴 (#47)', async () => {
    adminApi.getAuditLogs.mockResolvedValue({ data: { data: [{ id: 1, action: 'LOGIN_SUCCESS' }] } })

    await act(async () => { root.render(<AdminPage />) })
    await flush()
    expect(adminApi.getAuditLogs).toHaveBeenCalledTimes(1)

    await click(button('새로고침'))
    await flush()

    expect(adminApi.getAuditLogs).toHaveBeenCalledTimes(2)
  })

  // 로그인 실패 탭: IP 열 없음, 이메일 검색과 전체 보기
  it('[TC-WEB-215] 로그인 실패 탭에 IP 열이 없고, 이메일로 검색하면 검색 API 결과를, 전체 보기면 다시 최근 기록을 보여 줌 (#48, #89)', async () => {
    adminApi.getAuditLogs.mockResolvedValue({ data: { data: [] } })
    adminApi.getLoginFailures.mockResolvedValue({ data: { data: [{ id: 1, emailHash: 'hmac:all', reason: 'Incorrect password' }] } })
    adminApi.searchLoginFailures.mockResolvedValue({ data: { data: [{ id: 2, emailHash: 'hmac:kim', reason: 'Email not found' }] } })

    await act(async () => { root.render(<AdminPage />) })
    await flush()
    await click(button('로그인 실패'))
    await flush()

    const headers = [...container.querySelectorAll('th')].map(th => th.textContent)
    expect(headers).toContain('이메일 (해시)')
    expect(headers).not.toContain('IP')
    expect(container.textContent).toContain('hmac:all')

    await type(container.querySelector('input[type="email"]'), ' kim@test.com ')
    await act(async () => {
      container.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    })
    await flush()

    expect(adminApi.searchLoginFailures).toHaveBeenCalledWith('kim@test.com')
    expect(container.textContent).toContain('hmac:kim')
    expect(container.textContent).not.toContain('hmac:all')

    await click(button('전체 보기'))
    await flush()

    expect(adminApi.getLoginFailures).toHaveBeenCalledTimes(2)
    expect(container.textContent).toContain('hmac:all')
  })

  // 로그인 실패 원인은 한국어로, 모르는 값은 그대로
  it('[TC-WEB-227] 로그인 실패 원인(가입 안 됨·비밀번호 틀림·시도 초과)은 한국어로, 모르는 값은 서버 값 그대로 표시', async () => {
    adminApi.getAuditLogs.mockResolvedValue({ data: { data: [] } })
    adminApi.getLoginFailures.mockResolvedValue({ data: { data: [
      { id: 3, emailHash: 'hmac:a', reason: 'User not found' },
      { id: 2, emailHash: 'hmac:b', reason: 'Incorrect password' },
      { id: 1, emailHash: 'hmac:c', reason: 'Too many attempts' },
      { id: 0, emailHash: 'hmac:d', reason: 'Some new reason' },
    ] } })

    await act(async () => { root.render(<AdminPage />) })
    await flush()
    await click(button('로그인 실패'))
    await flush()

    const reasons = [...container.querySelectorAll('tbody tr')].map(r => r.children[2].textContent)
    expect(reasons).toEqual(['가입되지 않은 이메일', '비밀번호 틀림', '시도 횟수 초과로 차단', 'Some new reason'])
  })
})
