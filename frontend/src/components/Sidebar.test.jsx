import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/authApi', () => ({ authApi: { me: vi.fn() } }))

import { authApi } from '../api/authApi'
import Sidebar from './Sidebar'

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

// 사이드바 그린 뒤 메뉴 이름 목록
async function menuFor(meResult) {
  authApi.me.mockImplementation(meResult)
  await act(async () => { root.render(<MemoryRouter><Sidebar /></MemoryRouter>) })
  await act(async () => { await Promise.resolve() })
  return [...container.querySelectorAll('a')].map(a => a.textContent)
}

describe('사이드바 메뉴', () => {
  // 관리자 메뉴는 서버가 알려 준 역할이 ADMIN일 때만
  it('[TC-WEB-220] 관리자 메뉴는 /auth/me 역할이 ADMIN일 때만 보이고, USER이거나 조회에 실패하면 숨김 (#10)', async () => {
    expect(await menuFor(() => Promise.resolve({ data: { data: { role: 'USER' } } }))).not.toContain('관리자')
    act(() => root.unmount())
    root = createRoot(container)
    expect(await menuFor(() => Promise.reject(new Error('network')))).not.toContain('관리자')
    act(() => root.unmount())
    root = createRoot(container)
    const admin = await menuFor(() => Promise.resolve({ data: { data: { role: 'ADMIN' } } }))
    expect(admin).toContain('관리자')
    expect(admin).toContain('대시보드')
  })
})
