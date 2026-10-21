import React from 'react'

// 정책대출(디딤돌대출·보금자리론) 판정용 선택 입력칸 (비우면 해당 조건은 '확인 필요')
const inputClass = hasError =>
  `w-full px-4 py-2 border rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500 ${
    hasError ? 'border-red-500 dark:border-red-400' : 'border-gray-300 dark:border-gray-600'
  }`

function YesNoUnknown({ name, label, value, onChange }) {
  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{label}</label>
      <select name={name} value={value} onChange={onChange} className={inputClass(false)}>
        <option value="">모름</option>
        <option value="true">예</option>
        <option value="false">아니오</option>
      </select>
    </div>
  )
}

export default function PolicyInputs({ form, onChange, errors = {} }) {
  return (
    <fieldset className="flex flex-col gap-4 border-t border-gray-100 dark:border-gray-700 pt-4">
      <legend className="text-sm font-semibold text-gray-800 dark:text-gray-200">
        정책대출 판정 정보 <span className="text-gray-400 text-xs font-normal">(선택 — 비우면 '확인 필요')</span>
      </legend>

      {[
        { name: 'housePrice', label: '주택 가격 (원)', placeholder: '예: 400000000' },
        { name: 'householdAnnualIncome', label: '부부합산 연소득 (원)', placeholder: '예: 48000000 (미혼이면 본인 연소득)' },
      ].map(({ name, label, placeholder }) => (
        <div key={name}>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{label}</label>
          <input type="number" name={name} value={form[name]} onChange={onChange} placeholder={placeholder}
            className={inputClass(errors[name])} />
          {errors[name] && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors[name]}</p>}
        </div>
      ))}

      <YesNoUnknown name="firstHome" label="생애최초 주택 구입" value={form.firstHome} onChange={onChange} />
      <YesNoUnknown name="newlywed" label="신혼가구 (혼인 7년 이내)" value={form.newlywed} onChange={onChange} />

      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">미성년 자녀 수</label>
        <input type="number" name="childCount" value={form.childCount} onChange={onChange} placeholder="예: 0"
          min="0" step="1" className={inputClass(errors.childCount)} />
        {errors.childCount && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.childCount}</p>}
      </div>
    </fieldset>
  )
}
