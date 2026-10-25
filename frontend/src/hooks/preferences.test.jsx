import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { useTheme } from './useTheme'
import { useLargeText } from './useLargeText'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

let container
let root

beforeEach(() => {
  localStorage.clear()
  document.documentElement.className = ''
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
})

// 현재 값을 보여 주는 컴포넌트 (App 역할)
function Viewer() {
  const { theme } = useTheme()
  const { largeText } = useLargeText()
  return <p id="viewer">{`${theme}/${largeText}`}</p>
}

// 바꾸는 버튼 컴포넌트 (ThemeToggle·LargeTextToggle 역할)
function Toggles() {
  const { toggleTheme } = useTheme()
  const { toggleLargeText } = useLargeText()
  return (
    <>
      <button id="theme" onClick={toggleTheme}>theme</button>
      <button id="large" onClick={toggleLargeText}>large</button>
    </>
  )
}

describe('테마·큰 글씨 설정', () => {
  // 다크로 저장된 상태에서 버튼으로 바꾸면 다른 컴포넌트·html 클래스·저장 값이 함께 바뀜
  it('[TC-WEB-218] 다크로 저장된 상태에서 라이트로 바꾸면 다른 컴포넌트와 html 클래스도 바로 라이트, 큰 글씨도 같음 (#67)', async () => {
    localStorage.setItem('hfm_theme', 'dark')
    await act(async () => { root.render(<><Viewer /><Toggles /></>) })
    expect(container.querySelector('#viewer').textContent).toBe('dark/false')
    expect(document.documentElement.classList.contains('dark')).toBe(true)

    await act(async () => { container.querySelector('#theme').click() })
    await act(async () => { container.querySelector('#large').click() })

    expect(container.querySelector('#viewer').textContent).toBe('light/true')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    expect(document.documentElement.classList.contains('large-text')).toBe(true)
    expect(localStorage.getItem('hfm_theme')).toBe('light')
    expect(localStorage.getItem('hfm_large_text')).toBe('true')
  })
})
