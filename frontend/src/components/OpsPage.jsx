import { useEffect, useMemo, useState } from 'react'
import { api, authSession } from '../lib/api'

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

export default function OpsPage() {
  const [authChecked, setAuthChecked] = useState(false)
  const [user, setUser] = useState(null)
  const [credentialId, setCredentialId] = useState('CRED-001')
  const [opsSummary, setOpsSummary] = useState(null)
  const [opsState, setOpsState] = useState(null)
  const [opsAnomalies, setOpsAnomalies] = useState([])
  const [securityStatus, setSecurityStatus] = useState(null)
  const [walletStatus, setWalletStatus] = useState(null)
  const [reconcileFeedback, setReconcileFeedback] = useState(null)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

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

  const isAdmin = useMemo(() => user?.roles?.includes('ADMIN'), [user])

  useEffect(() => {
    if (!authChecked || !isAdmin) {
      return
    }
    refreshPanels(credentialId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authChecked, isAdmin])

  async function refreshPanels(targetCredentialId) {
    setError('')
    try {
      const [summary, anomalies, security, wallet] = await Promise.all([
        api.opsSummary(),
        api.opsAnomalies(10),
        api.opsSecurityStatus(),
        api.walletStatus(),
      ])
      setOpsSummary(summary)
      setOpsAnomalies(Array.isArray(anomalies) ? anomalies : [])
      setSecurityStatus(security)
      setWalletStatus(wallet)

      if (targetCredentialId) {
        try {
          const state = await api.opsCredentialState(targetCredentialId)
          setOpsState(state)
        } catch (stateError) {
          if (stateError?.message?.includes('Credential not found')) {
            setOpsState(null)
          } else {
            throw stateError
          }
        }
      }
    } catch (err) {
      setError(err.message)
    }
  }

  async function handleReconcile() {
    setBusy('reconcile')
    setError('')
    setMessage('')
    try {
      const response = await api.reconcileCredential(credentialId)
      setReconcileFeedback(response)
      setMessage(`Reconcile result: ${response.result}`)
      await refreshPanels(credentialId)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  async function handleWalletDisable() {
    setBusy('walletDisable')
    setError('')
    setMessage('')
    try {
      await api.walletDisable('Operator initiated temporary disable')
      setMessage('Wallet disabled.')
      await refreshPanels(credentialId)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  async function handleWalletEnable() {
    setBusy('walletEnable')
    setError('')
    setMessage('')
    try {
      await api.walletEnable()
      setMessage('Wallet enabled.')
      await refreshPanels(credentialId)
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
        <div className="mx-auto max-w-5xl rounded-2xl border border-slate-200 bg-white p-6">
          <p className="text-sm text-slate-700">Loading operations console...</p>
        </div>
      </div>
    )
  }

  if (!isAdmin) {
    return (
      <div className="min-h-screen bg-slate-50 p-8">
        <div className="mx-auto max-w-5xl rounded-2xl border border-slate-200 bg-white p-6">
          <p className="text-sm text-slate-700">Admin access is required for operations.</p>
          <a href="/issuer" className="mt-3 inline-block rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-100">
            Open Issuer
          </a>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-slate-50 px-4 py-8 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl space-y-5">
        <header className="rounded-2xl border border-slate-200 bg-white p-6">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">BlockCred</p>
          <h1 className="mt-2 text-2xl font-bold text-slate-900">Operations Console</h1>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-slate-600">
            <span>{user.displayName}</span>
            <span className="rounded-full border border-slate-300 px-2 py-0.5 text-xs">{user.roles.join(', ')}</span>
            <a href="/issuer" className="rounded-lg border border-slate-300 px-2 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-100">
              Open Issuer
            </a>
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
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold text-slate-900">Ops Summary</h2>
              <button
                type="button"
                onClick={() => refreshPanels(credentialId)}
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-100"
              >
                Refresh
              </button>
            </div>
            {opsSummary ? (
              <div className="mt-3 space-y-1 text-sm text-slate-700">
                <p>Pending: {opsSummary.pendingCount}</p>
                <p>Retryable: {opsSummary.retryableCount}</p>
                <p>Final failed: {opsSummary.finalFailedCount}</p>
                <p>Worker heartbeat: {formatDate(opsSummary.lastWorkerRunAt)}</p>
                <p>Chain reachable: {opsSummary.chainReachable ? 'Yes' : 'No'}</p>
                <p>Recent anomalies: {opsSummary.recentAnomalyCount}</p>
                <p>
                  Alerts:{' '}
                  {(opsSummary.pendingAgeAlert || opsSummary.retryThresholdAlert || opsSummary.finalFailedAlert || opsSummary.revocationPropagationAlert)
                    ? 'Attention required'
                    : 'Normal'}
                </p>
              </div>
            ) : (
              <p className="mt-3 text-sm text-slate-500">No summary loaded.</p>
            )}
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="text-lg font-semibold text-slate-900">Security Status</h2>
            {securityStatus ? (
              <div className="mt-3 space-y-1 text-sm text-slate-700">
                <p>Locked accounts: {securityStatus.lockedAccountCount}</p>
                <p>Disabled accounts: {securityStatus.disabledAccountCount}</p>
                <p>Failed logins (24h): {securityStatus.failedLoginsLast24h}</p>
                <p>Active refresh sessions: {securityStatus.activeRefreshSessions}</p>
                <p>Legacy header auth: {securityStatus.legacyHeaderAuthEnabled ? 'Enabled' : 'Disabled'}</p>
                <p>Checked at: {formatDate(securityStatus.checkedAt)}</p>
              </div>
            ) : (
              <p className="mt-3 text-sm text-slate-500">No security status loaded.</p>
            )}
          </div>
        </section>

        <section className="grid gap-5 lg:grid-cols-2">
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="text-lg font-semibold text-slate-900">Credential Operations</h2>
            <input
              value={credentialId}
              onChange={(event) => setCredentialId(event.target.value)}
              className="mt-3 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              placeholder="Credential ID"
            />
            <div className="mt-3 flex flex-wrap gap-2">
              <button
                type="button"
                onClick={handleReconcile}
                disabled={busy === 'reconcile'}
                className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
              >
                {busy === 'reconcile' ? 'Reconciling...' : 'Reconcile'}
              </button>
              <button
                type="button"
                onClick={handleWalletDisable}
                disabled={busy === 'walletDisable' || walletStatus?.enabled === false}
                className="rounded-lg border border-rose-300 px-4 py-2 text-sm font-semibold text-rose-700 hover:bg-rose-50 disabled:opacity-60"
              >
                Disable Wallet
              </button>
              <button
                type="button"
                onClick={handleWalletEnable}
                disabled={busy === 'walletEnable' || walletStatus?.enabled === true}
                className="rounded-lg border border-emerald-300 px-4 py-2 text-sm font-semibold text-emerald-700 hover:bg-emerald-50 disabled:opacity-60"
              >
                Enable Wallet
              </button>
            </div>
            {reconcileFeedback ? (
              <div className="mt-3 rounded-lg bg-slate-50 p-3 text-xs text-slate-700">
                <p className="font-semibold">{reconcileFeedback.result}</p>
                <p>{reconcileFeedback.message}</p>
                <p>{reconcileFeedback.recommendedAction}</p>
                {typeof reconcileFeedback.cooldownRemainingSeconds === 'number' ? (
                  <p>Cooldown remaining: {reconcileFeedback.cooldownRemainingSeconds}s</p>
                ) : null}
              </div>
            ) : null}
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="text-lg font-semibold text-slate-900">Wallet</h2>
            {walletStatus ? (
              <div className="mt-3 space-y-1 text-sm text-slate-700">
                <p>Status: {walletStatus.enabled ? 'Enabled' : 'Disabled'}</p>
                <p>Key source: {walletStatus.keySource}</p>
                <p>Key present: {walletStatus.keyPresent ? 'Yes' : 'No'}</p>
                <p>Updated by: {walletStatus.updatedBy || 'N/A'}</p>
                <p>Updated at: {formatDate(walletStatus.updatedAt)}</p>
                <p>Reason: {walletStatus.disableReason || 'N/A'}</p>
              </div>
            ) : (
              <p className="mt-3 text-sm text-slate-500">Wallet status unavailable.</p>
            )}
          </div>
        </section>

        <section className="grid gap-5 lg:grid-cols-2">
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="text-lg font-semibold text-slate-900">Credential State</h2>
            {opsState ? (
              <div className="mt-3 space-y-1 text-sm text-slate-700">
                <p>Credential: {opsState.credentialId}</p>
                <p>Lifecycle: {opsState.lifecycleStatus}</p>
                <p>Hash: <span className="font-mono text-xs break-all">{opsState.hash}</span></p>
                <p>Last tx: <span className="font-mono text-xs break-all">{opsState.lastTxHash || 'N/A'}</span></p>
                <p>Updated at: {formatDate(opsState.updatedAt)}</p>
                {opsState.latestJob ? (
                  <p>
                    Latest job: {opsState.latestJob.jobType} / {opsState.latestJob.status} | Retry {opsState.latestJob.retryCount}
                  </p>
                ) : null}
              </div>
            ) : (
              <p className="mt-3 text-sm text-slate-500">No credential state loaded.</p>
            )}
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="text-lg font-semibold text-slate-900">Recent Anomalies</h2>
            {opsAnomalies.length === 0 ? (
              <p className="mt-3 text-sm text-slate-500">No anomalies in latest window.</p>
            ) : (
              <ul className="mt-3 space-y-2">
                {opsAnomalies.slice(0, 8).map((item) => (
                  <li key={item.id} className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-700">
                    <p className="font-semibold">{item.action} ({item.severity})</p>
                    <p>{item.credentialId}: {item.details}</p>
                    <p>{item.recommendedAction}</p>
                    <p className="text-slate-500">{formatDate(item.createdAt)}</p>
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
