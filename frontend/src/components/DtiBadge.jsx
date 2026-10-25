import React from 'react'
import { dtiStatusView } from '../utils/dti'

// DTI 판정 배지 (적정 / 초과 / 판단 불가, 대시보드·리포트 공통)
export default function DtiBadge({ status }) {
  const view = dtiStatusView(status)
  return (
    <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${view.badge}`}>
      {view.label}
    </span>
  )
}
