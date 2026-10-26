import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { RecommendationCard } from './PolicyRecommendation'

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

describe('추천 카드 피드백', () => {
  // 충족하지 못한 상품에만 '이렇게 하면 가능' 피드백을 보여 주고, 충족한 상품에는 보여 주지 않음
  it('[TC-POL-305] 미충족 상품에는 조건 피드백을 보여 주고, 충족 상품에는 보여 주지 않음', async () => {
    const policy = {
      recommendedProduct: '보금자리론',
      recommendationReason: '디딤돌대출은 주택 가격 조건이 맞지 않아, 조건을 모두 충족한 보금자리론을 추천합니다.',
      recommendedPolicies: [
        { name: '보금자리론', eligibility: 'ELIGIBLE', maxLoanAmount: 280000000, interestRate: '연 4.90~5.30%',
          conditions: [{ name: '주택 가격', result: 'MET', detail: '기준 6억 원 이하', hint: null }] },
        { name: '내집마련 디딤돌대출', eligibility: 'NOT_ELIGIBLE', maxLoanAmount: 200000000, interestRate: '연 2.85~4.15%',
          conditions: [{ name: '주택 가격', result: 'NOT_MET', detail: '기준 5억 원 초과', hint: '주택 가격 5억 원 이하 주택이면 충족' }] },
      ],
    }

    await act(async () => { root.render(<RecommendationCard policy={policy} />) })

    expect(container.textContent).toContain('주택 가격 5억 원 이하 주택이면 충족')
    expect(container.querySelectorAll('li li')).toHaveLength(1)
  })

  // 추천 상품의 입력 기준 기본금리와 기준 설명 표시
  it('[TC-POL-307] 추천 상품의 입력 기준 기본금리(연 2.65%)와 기준(소득·만기·우대 전)을 보여 줌', async () => {
    const policy = {
      recommendedProduct: '신생아 특례 디딤돌대출',
      recommendationReason: '조건을 충족한 3개 상품 중 ... 신생아 특례 디딤돌대출(연 2.65%)를 추천합니다.',
      recommendedPolicies: [
        { name: '신생아 특례 디딤돌대출', eligibility: 'ELIGIBLE', maxLoanAmount: 280000000,
          interestRate: '연 1.80~4.50%', baseRate: 2.65, baseRateBasis: '부부합산 연소득 4,800만 원 · 30년 만기 · 우대 전',
          conditions: [] },
      ],
    }

    await act(async () => { root.render(<RecommendationCard policy={policy} />) })

    expect(container.textContent).toContain('입력 기준 기본금리 연 2.65%')
    expect(container.textContent).toContain('부부합산 연소득 4,800만 원 · 30년 만기 · 우대 전')
  })
})
