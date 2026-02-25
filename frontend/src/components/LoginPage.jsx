import { useEffect, useState } from 'react'
import { api, authSession } from '../lib/api'

export default function LoginPage() {
  const [usernameOrEmail, setUsernameOrEmail] = useState('admin')
  const [password, setPassword] = useState('AdminPass#2026')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    async function checkSession() {
      if (!authSession.get()) {
        return
      }
      try {
        const me = await api.authMe()
        window.location.replace(authSession.redirectPathForRoles(me.roles || []))
      } catch {
        authSession.clear()
      }
    }
    checkSession()
  }, [])

  async function handleSubmit(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      const session = await authSession.login(usernameOrEmail, password)
      const roles = session?.user?.roles || []
      window.location.replace(authSession.redirectPathForRoles(roles))
    } catch (err) {
      setError(err.message || 'Login failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="min-h-screen bg-slate-50 px-4 py-10 sm:px-6">
      <div className="mx-auto max-w-md space-y-5">
        <header className="rounded-2xl border border-slate-200 bg-white p-6">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">BlockCred</p>
          <h1 className="mt-2 text-2xl font-bold text-slate-900">Staff Login</h1>
          <p className="mt-1 text-sm text-slate-600">University issuer and operations access.</p>
        </header>

        <form onSubmit={handleSubmit} className="rounded-2xl border border-slate-200 bg-white p-6">
          <label className="block text-sm text-slate-700">
            Username or Email
            <input
              value={usernameOrEmail}
              onChange={(event) => setUsernameOrEmail(event.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              required
            />
          </label>

          <label className="mt-3 block text-sm text-slate-700">
            Password
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              required
            />
          </label>

          {error ? <p className="mt-3 text-sm text-rose-700">{error}</p> : null}

          <button
            type="submit"
            disabled={busy}
            className="mt-4 w-full rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {busy ? 'Signing in...' : 'Sign in'}
          </button>
        </form>

        <section className="rounded-2xl border border-slate-200 bg-white p-4 text-xs text-slate-600">
          <p className="font-semibold text-slate-700">Dev seed users</p>
          <p className="mt-1">`admin / AdminPass#2026` (ADMIN)</p>
          <p>`issuer / IssuerPass#2026` (ISSUER)</p>
        </section>
      </div>
    </div>
  )
}
