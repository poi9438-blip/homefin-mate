import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { authApi } from '../api/authApi'

// 회원가입 화면

// 입력값 검증 (이메일 형식, 비밀번호 8자 이상, 소득 0 이상)
function validate(form) {
  const errors = {}
  if (!form.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email))
    errors.email = '올바른 이메일 주소를 입력해주세요.'
  if (!form.password || form.password.length < 8)
    errors.password = '비밀번호는 8자 이상이어야 합니다.'
  if (form.income && Number(form.income) < 0)
    errors.income = '소득은 0 이상이어야 합니다.'
  return errorsㅉ
}

export default function SignupPage() {
  const navigate = useNavigate()
  const [form, setForm]               = useState({ email: '', password: '', income: '' })
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError]             = useState('')
  const [loading, setLoading]         = useState(false)

  const handleChange = e => {
    setForm(prev => ({ ...prev, [e.target.name]: e.target.value }))
    if (fieldErrors[e.target.name]) {
      setFieldErrors(prev => ({ ...prev, [e.target.name]: '' }))
    }
  }

  // 가입 요청: 검증 => API 호출 => 성공하면 로그인 화면으로
  const handleSubmit = async e => {
    e.preventDefault()
    const errors = validate(form)
    if (Object.keys(errors).length) {
      setFieldErrors(errors)
      return
    }
    setError('')
    setFieldErrors({})
    setLoading(true)
    try {
      await authApi.signup(form)
      navigate('/login')
    } catch (err) {
      setError(err.response?.data?.message || '회원가입에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
      <div className="w-full max-w-md bg-white dark:bg-gray-800 rounded-2xl shadow-md p-8">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">계정 만들기</h1>
        <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">HomeFin Mate에 가입하세요</p>

        {error && (
          <div className="mb-4 p-3 bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-400 rounded-lg text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">이메일</label>
            <input type="email" name="email" value={form.email} onChange={handleChange}
              className={`w-full px-4 py-2 border rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500 ${fieldErrors.email ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'}`}
              placeholder="you@example.com" />
            {fieldErrors.email && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{fieldErrors.email}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">비밀번호</label>
            <input type="password" name="password" value={form.password} onChange={handleChange}
              className={`w-full px-4 py-2 border rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500 ${fieldErrors.password ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'}`}
              placeholder="최소 8자 이상" />
            {fieldErrors.password && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{fieldErrors.password}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              월 소득 <span className="text-gray-400 text-xs">(선택)</span>
            </label>
            <input type="number" name="income" value={form.income} onChange={handleChange}
              className={`w-full px-4 py-2 border rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500 ${fieldErrors.income ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'}`}
              placeholder="예: 3000000" />
            {fieldErrors.income && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{fieldErrors.income}</p>}
          </div>
          <button type="submit" disabled={loading}
            className="w-full py-2.5 bg-primary-600 hover:bg-primary-700 text-white font-semibold rounded-lg transition-colors disabled:opacity-60">
            {loading ? '계정 생성 중...' : '회원가입'}
          </button>
        </form>

        <p className="mt-4 text-sm text-center text-gray-500 dark:text-gray-400">
          이미 계정이 있으신가요?{' '}
          <Link to="/login" className="text-primary-600 hover:underline">로그인</Link>
        </p>
      </div>
    </div>
  )
}
