import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/authApi', () => ({ authApi: { logout: vi.fn() } }))

import { authApi } from '../api/authApi'
import Header from './Header'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

let container
let root

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
})

//  헤더를 그리고 로그아웃 버튼 누르기
async function clickLogout() {
  await act(async () => {
    root.render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route path="/dashboard" element={<Header />} />
          <Route path="/login" element={<p>로그인 화면</p>} />
        </Routes>
      </MemoryRouter>,
    )
  })
  const button = [...container.querySelectorAll('button')].find(b => b.textContent.includes('로그아웃'))
  await act(async () => { button.click() })
  await act(async () => { await Promise.resolve() })
}

describe('헤더 로그아웃', () => {
  // 로그아웃은 서버 로그아웃 요청 → 로그인 상태·입력 임시 저장 삭제 → 로그인 화면, 서버가 실패해도 같음
  it('[TC-WEB-221] 로그아웃 버튼은 서버 로그아웃(쿠키 삭제)을 요청하고 로그인 상태를 지운 뒤 로그인 화면으로, 서버 요청이 실패해도 같음 (#78)', async () => {
    authApi.logout.mockResolvedValue({})
    localStorage.setItem('hfm_session_expires_at', String(Date.now() + 3600000))
    localStorage.setItem('hfm_draft_loan', '{}')

    await clickLogout()

    expect(authApi.logout).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem('hfm_session_expires_at')).toBeNull()
    expect(localStorage.getItem('hfm_draft_loan')).toBeNull()
    expect(container.textContent).toContain('로그인 화면')

    act(() => root.unmount())
    root = createRoot(container)
    authApi.logout.mockRejectedValue(new Error('network'))
    localStorage.setItem('hfm_session_expires_at', String(Date.now() + 3600000))

    await clickLogout()

    expect(localStorage.getItem('hfm_session_expires_at')).toBeNull()
    expect(container.textContent).toContain('로그인 화면')
  })
})
