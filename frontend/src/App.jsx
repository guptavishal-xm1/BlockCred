import { useEffect, useMemo, useState } from 'react'
import { api, authSession } from './lib/api'

function todayDate() {
  return new Date().toISOString().slice(0, 10)
}

function createIssuePayload() {
  return {
    credentialId: `CRED-${Math.floor(Date.now() / 1000)}`,
    universityId: 'UNI-001',
    studentId: 'STU-001',
    program: 'Computer Science',
    degree: 'B.Tech',
    issueDate: todayDate(),
    nonce: crypto.randomUUID(),
    version: '1.0',
  }
}

function formatDate(value) {
  if (!value) {
    return 'N/A'
  }
  const parsed = Date.parse(value)
  if (Number.isNaN(parsed)) {
    return value
  }
  return new Date(parsed).toLocaleString()
}

function hasIssuerRole(user) {
  const roles = user?.roles || []
  return roles.includes('ISSUER') || roles.includes('ADMIN')
}

export default function App() {
  const [authChecked, setAuthChecked] = useState(false)
  const [user, setUser] = useState(null)
  const [issuePayload, setIssuePayload] = useState(createIssuePayload)
  const [revokeCredentialId, setRevokeCredentialId] = useState('')
  const [shareCredentialId, setShareCredentialId] = useState('')
  const [shareResult, setShareResult] = useState(null)
  const [lastIssued, setLastIssued] = useState(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')
  const [activity, setActivity] = useState([])

  useEffect(() => {
    async function bootstrap() {
      try {
        const me = await api.authMe()
        setUser(me)
        setAuthChecked(true)
      } catch {
        authSession.clear()
        window.location.replace('/login')
      }
    }
    bootstrap()
  }, [])

  const canUseOps = useMemo(() => user?.roles?.includes('ADMIN'), [user])

  function pushActivity(action, details) {
    setActivity((prev) => [
      { action, details, at: new Date().toISOString() },
      ...prev,
    ].slice(0, 10))
  }

  function updateIssueField(field, value) {
    setIssuePayload((prev) => ({ ...prev, [field]: value }))
  }

  async function handleIssue(event) {
    event.preventDefault()
    setMessage('')
    setError('')
    setBusy('issue')
    try {
      const response = await api.issueCredential(issuePayload)
      setLastIssued(response)
      setRevokeCredentialId(response.credentialId)
      setShareCredentialId(response.credentialId)
      setIssuePayload(createIssuePayload())
      setShareResult(null)
      setMessage(`Credential ${response.credentialId} issued and queued.`)
      pushActivity('ISSUED', `${response.credentialId} (${response.hash})`)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  async function handleRevoke() {
    setMessage('')
    setError('')
    setBusy('revoke')
    try {
      const response = await api.revokeCredential(revokeCredentialId)
      setMessage(`Revocation requested for ${response.credentialId}.`)
      pushActivity('REVOKE_REQUESTED', response.credentialId)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  async function handleShareLink() {
    setMessage('')
    setError('')
    setBusy('share')
    try {
      const response = await api.shareLink(shareCredentialId)
      setShareResult(response)
      setMessage(`Share link generated for ${shareCredentialId}.`)
      pushActivity('SHARE_LINK', shareCredentialId)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  async function handleLogout() {
    await authSession.logout()
    window.location.replace('/login')
  }

  if (!authChecked) {
    return (
      <div className="min-h-screen bg-slate-50 p-8">
        <div className="mx-auto max-w-3xl rounded-2xl border border-slate-200 bg-white p-6">
          <p className="text-sm text-slate-700">Loading issuer console...</p>
        </div>
      </div>
    )
  }

  if (!hasIssuerRole(user)) {
    return (
      <div className="min-h-screen bg-slate-50 p-8">
        <div className="mx-auto max-w-3xl rounded-2xl border border-slate-200 bg-white p-6">
          <p className="text-sm text-slate-700">Issuer access is required for this page.</p>
          <button
            type="button"
            onClick={handleLogout}
            className="mt-3 rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-100"
          >
            Sign out
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-slate-50 px-4 py-8 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-5xl space-y-5">
        <header className="rounded-2xl border border-slate-200 bg-white p-6">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">BlockCred</p>
          <h1 className="mt-2 text-2xl font-bold text-slate-900">Issuer Console</h1>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-slate-600">
            <span>{user.displayName}</span>
            <span className="rounded-full border border-slate-300 px-2 py-0.5 text-xs">{user.roles.join(', ')}</span>
            {canUseOps ? (
              <a href="/ops" className="rounded-lg border border-slate-300 px-2 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-100">
                Open Ops
              </a>
            ) : null}
            <button
              type="button"
              onClick={handleLogout}
              className="rounded-lg border border-slate-300 px-2 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-100"
            >
              Sign out
            </button>
          </div>
        </header>

        {error ? <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</div> : null}
        {message ? <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{message}</div> : null}

        <section className="grid gap-5 lg:grid-cols-2">
          <form onSubmit={handleIssue} className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="text-lg font-semibold text-slate-900">Issue Credential</h2>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              {Object.entries(issuePayload).map(([field, value]) => (
                <label key={field} className="text-sm text-slate-700">
                  <span className="font-medium">{field}</span>
                  <input
                    type={field === 'issueDate' ? 'date' : 'text'}
                    value={value}
                    onChange={(event) => updateIssueField(field, event.target.value)}
                    className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                    required
                  />
                </label>
              ))}
            </div>
            <div className="mt-4 flex gap-2">
              <button
                type="submit"
                disabled={busy === 'issue'}
                className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
              >
                {busy === 'issue' ? 'Issuing...' : 'Issue'}
              </button>
              <button
                type="button"
                onClick={() => setIssuePayload(createIssuePayload())}
                className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
              >
                Reset
              </button>
            </div>
          </form>

          <div className="space-y-5">
            <section className="rounded-2xl border border-slate-200 bg-white p-5">
              <h2 className="text-lg font-semibold text-slate-900">Revoke Credential</h2>
              <input
                value={revokeCredentialId}
                onChange={(event) => setRevokeCredentialId(event.target.value)}
                className="mt-3 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                placeholder="Credential ID"
              />
              <button
                type="button"
                onClick={handleRevoke}
                disabled={busy === 'revoke' || !revokeCredentialId}
                className="mt-3 rounded-lg bg-rose-700 px-4 py-2 text-sm font-semibold text-white hover:bg-rose-600 disabled:opacity-60"
              >
                {busy === 'revoke' ? 'Submitting...' : 'Request Revoke'}
              </button>
            </section>

            <section className="rounded-2xl border border-slate-200 bg-white p-5">
              <h2 className="text-lg font-semibold text-slate-900">Share Verification Link</h2>
              <input
                value={shareCredentialId}
                onChange={(event) => setShareCredentialId(event.target.value)}
                className="mt-3 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                placeholder="Credential ID"
              />
              <button
                type="button"
                onClick={handleShareLink}
                disabled={busy === 'share' || !shareCredentialId}
                className="mt-3 rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-60"
              >
                {busy === 'share' ? 'Generating...' : 'Generate Link'}
              </button>
              {shareResult ? (
                <div className="mt-3 rounded-lg bg-slate-50 p-3 text-xs text-slate-700">
                  <p className="font-semibold">Verification URL</p>
                  <p className="mt-1 break-all font-mono">{shareResult.verifyUrl}</p>
                  <p className="mt-2">Token expires at: {formatDate(shareResult.tokenExpiresAt)}</p>
                </div>
              ) : null}
            </section>
          </div>
        </section>

        <section className="grid gap-5 lg:grid-cols-2">
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="text-lg font-semibold text-slate-900">Last Issued</h2>
            {lastIssued ? (
              <div className="mt-3 space-y-2 text-sm text-slate-700">
                <p>Credential ID: <span className="font-mono">{lastIssued.credentialId}</span></p>
                <p>Hash: <span className="break-all font-mono text-xs">{lastIssued.hash}</span></p>
                <p>Status: {lastIssued.status}</p>
              </div>
            ) : (
              <p className="mt-3 text-sm text-slate-500">No issue action yet.</p>
            )}
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="text-lg font-semibold text-slate-900">Activity</h2>
            {activity.length === 0 ? (
              <p className="mt-3 text-sm text-slate-500">No recent issuer actions.</p>
            ) : (
              <ul className="mt-3 space-y-2">
                {activity.map((item) => (
                  <li key={`${item.action}-${item.at}`} className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-700">
                    <p className="font-semibold">{item.action}</p>
                    <p>{item.details}</p>
                    <p className="text-slate-500">{formatDate(item.at)}</p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>
      </div>
    </div>
  )
}
