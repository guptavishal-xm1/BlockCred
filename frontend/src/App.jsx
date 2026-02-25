import { useEffect, useMemo, useState } from 'react'
import { api } from './lib/api'

const STORAGE_KEY = 'blockcred.issuedCredentials'

const TABS = [
  { key: 'university', label: 'University' },
  { key: 'verifier', label: 'Employer Verifier' },
  { key: 'student', label: 'Student' },
]

const STATUS_THEME = {
  VALID: 'bg-emerald-100 text-emerald-800 border-emerald-200',
  REVOKED: 'bg-rose-100 text-rose-800 border-rose-200',
  NOT_FOUND: 'bg-slate-100 text-slate-700 border-slate-300',
  TAMPERED: 'bg-amber-100 text-amber-800 border-amber-200',
  PENDING_ANCHOR: 'bg-sky-100 text-sky-800 border-sky-200',
  CHAIN_UNAVAILABLE: 'bg-orange-100 text-orange-800 border-orange-200',
}

function todayDate() {
  return new Date().toISOString().slice(0, 10)
}

function createIssuePayload() {
  return {
    credentialId: 'CRED-001',
    universityId: 'UNI-001',
    studentId: 'STU-001',
    program: 'Computer Science',
    degree: 'B.Tech',
    issueDate: todayDate(),
    nonce: crypto.randomUUID(),
    version: '1.0',
  }
}

function formatTime(iso) {
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return iso
  }
}

function StatusPill({ status }) {
  const theme = STATUS_THEME[status] || 'bg-slate-100 text-slate-700 border-slate-300'
  return (
    <span className={`inline-flex rounded-full border px-3 py-1 text-xs font-semibold tracking-wide ${theme}`}>
      {status}
    </span>
  )
}

function JsonField({ label, value }) {
  return (
    <div>
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <p className="break-all font-mono text-sm text-slate-800">{value}</p>
    </div>
  )
}

function ResultCard({ result }) {
  if (!result) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white/70 p-5 shadow-sm backdrop-blur">
        <p className="text-sm text-slate-500">No verification result yet.</p>
      </div>
    )
  }

  return (
    <div className="space-y-4 rounded-2xl border border-slate-200 bg-white/90 p-5 shadow-sm backdrop-blur">
      <div className="flex flex-wrap items-center gap-3">
        <StatusPill status={result.verificationStatus} />
        <span className="text-xs uppercase tracking-wider text-slate-500">Checked {formatTime(result.checkedAt)}</span>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <JsonField label="Credential Hash" value={result.credentialHash} />
        <JsonField label="Transaction Hash" value={result.txHash || 'N/A'} />
        <JsonField label="Anchoring State" value={result.anchoringState} />
        <JsonField label="Issuer" value={result.issuer} />
      </div>
      <p className="rounded-xl bg-slate-50 px-3 py-2 text-sm text-slate-700">{result.explanation}</p>
    </div>
  )
}

function App() {
  const [activeTab, setActiveTab] = useState('verifier')
  const [issuePayload, setIssuePayload] = useState(createIssuePayload)
  const [revokeCredentialId, setRevokeCredentialId] = useState('CRED-001')
  const [reconcileCredentialId, setReconcileCredentialId] = useState('CRED-001')

  const [verifyPayloadText, setVerifyPayloadText] = useState(JSON.stringify(createIssuePayload(), null, 2))
  const [verifyCredentialId, setVerifyCredentialId] = useState('CRED-001')
  const [verifyHash, setVerifyHash] = useState('')

  const [verificationResult, setVerificationResult] = useState(null)
  const [issuedRecords, setIssuedRecords] = useState([])
  const [activityLog, setActivityLog] = useState([])
  const [busyAction, setBusyAction] = useState('')
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (!stored) {
      return
    }

    try {
      const parsed = JSON.parse(stored)
      if (Array.isArray(parsed)) {
        setIssuedRecords(parsed)
      }
    } catch {
      // Ignore corrupted storage and continue with empty state.
    }
  }, [])

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(issuedRecords))
  }, [issuedRecords])

  const latestRecord = useMemo(() => issuedRecords[0], [issuedRecords])

  function pushLog(action, details) {
    setActivityLog((prev) => [
      { action, details, at: new Date().toISOString() },
      ...prev,
    ].slice(0, 10))
  }

  function updateIssueField(field, value) {
    setIssuePayload((prev) => ({ ...prev, [field]: value }))
  }

  async function handleIssue(e) {
    e.preventDefault()
    setErrorMessage('')
    setBusyAction('issue')
    try {
      const response = await api.issueCredential(issuePayload)
      pushLog('ISSUE', `${response.credentialId} queued for anchoring`)
      setVerifyCredentialId(response.credentialId)
      setVerifyHash(response.hash)
      setRevokeCredentialId(response.credentialId)
      setReconcileCredentialId(response.credentialId)
      setIssuedRecords((prev) => {
        const next = [
          {
            credentialId: response.credentialId,
            hash: response.hash,
            status: response.status,
            issuedAt: new Date().toISOString(),
          },
          ...prev.filter((r) => r.credentialId !== response.credentialId),
        ]
        return next
      })
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setBusyAction('')
    }
  }

  async function handleRevoke() {
    setErrorMessage('')
    setBusyAction('revoke')
    try {
      const response = await api.revokeCredential(revokeCredentialId)
      pushLog('REVOKE', `${response.credentialId} marked for revocation`)
      setVerifyCredentialId(response.credentialId)
      setIssuedRecords((prev) => prev.map((r) => (
        r.credentialId === response.credentialId ? { ...r, status: response.status } : r
      )))
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setBusyAction('')
    }
  }

  async function handleReconcile() {
    setErrorMessage('')
    setBusyAction('reconcile')
    try {
      const response = await api.reconcileCredential(reconcileCredentialId)
      pushLog('RECONCILE', `${reconcileCredentialId} => ${response.result}`)
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setBusyAction('')
    }
  }

  async function handleVerifyPayload() {
    setErrorMessage('')
    setBusyAction('verifyPayload')
    try {
      const parsed = JSON.parse(verifyPayloadText)
      const response = await api.verifyPayload(parsed)
      setVerificationResult(response)
      pushLog('VERIFY_PAYLOAD', `${response.verificationStatus} for ${response.credentialHash}`)
      setVerifyHash(response.credentialHash)
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setBusyAction('')
    }
  }

  async function handleVerifyById() {
    setErrorMessage('')
    setBusyAction('verifyId')
    try {
      const response = await api.verifyCredentialId(verifyCredentialId)
      setVerificationResult(response)
      pushLog('VERIFY_ID', `${response.verificationStatus} for ${verifyCredentialId}`)
      setVerifyHash(response.credentialHash)
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setBusyAction('')
    }
  }

  async function handleVerifyByHash() {
    setErrorMessage('')
    setBusyAction('verifyHash')
    try {
      const response = await api.verifyHash(verifyHash)
      setVerificationResult(response)
      pushLog('VERIFY_HASH', `${response.verificationStatus} for hash lookup`)
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setBusyAction('')
    }
  }

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,_#e2e8f0,_#f8fafc_55%)] px-4 py-8 sm:px-6 lg:px-10">
      <div className="mx-auto max-w-6xl">
        <header className="mb-6 rounded-3xl border border-slate-200 bg-white/80 p-6 shadow-sm backdrop-blur">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">BlockCred</p>
          <div className="mt-2 flex flex-wrap items-end justify-between gap-4">
            <div>
              <h1 className="text-3xl font-extrabold tracking-tight text-slate-900">Academic Credential Verification Console</h1>
              <p className="mt-2 max-w-2xl text-sm text-slate-600">
                Backend-first proof system with deterministic hashing, asynchronous anchoring, and transparent verification status.
              </p>
            </div>
            {latestRecord ? (
              <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Latest Credential</p>
                <p className="font-mono text-sm text-slate-700">{latestRecord.credentialId}</p>
              </div>
            ) : null}
          </div>
        </header>

        <div className="mb-6 flex flex-wrap gap-2">
          {TABS.map((tab) => {
            const selected = activeTab === tab.key
            return (
              <button
                key={tab.key}
                type="button"
                onClick={() => setActiveTab(tab.key)}
                className={`rounded-full px-4 py-2 text-sm font-semibold transition ${
                  selected
                    ? 'bg-slate-900 text-white shadow'
                    : 'bg-white text-slate-600 hover:bg-slate-100'
                }`}
              >
                {tab.label}
              </button>
            )
          })}
        </div>

        {errorMessage ? (
          <div className="mb-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{errorMessage}</div>
        ) : null}

        <main className="grid gap-6 lg:grid-cols-[1.15fr_1fr]">
          <section className="rounded-3xl border border-slate-200 bg-white/85 p-5 shadow-sm backdrop-blur sm:p-6">
            {activeTab === 'university' ? (
              <div className="space-y-6">
                <h2 className="text-xl font-bold text-slate-900">University Issuer Operations</h2>

                <form className="grid gap-3 sm:grid-cols-2" onSubmit={handleIssue}>
                  {Object.keys(issuePayload).map((field) => (
                    <label key={field} className="text-sm font-medium text-slate-700">
                      {field}
                      <input
                        type={field === 'issueDate' ? 'date' : 'text'}
                        value={issuePayload[field]}
                        onChange={(e) => updateIssueField(field, e.target.value)}
                        className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm outline-none ring-slate-900/20 transition focus:ring"
                        required
                      />
                    </label>
                  ))}
                  <div className="sm:col-span-2 flex flex-wrap gap-2">
                    <button
                      type="submit"
                      disabled={busyAction === 'issue'}
                      className="rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                    >
                      {busyAction === 'issue' ? 'Issuing...' : 'Issue + Queue Anchor'}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        const next = createIssuePayload()
                        setIssuePayload(next)
                        setVerifyPayloadText(JSON.stringify(next, null, 2))
                      }}
                      className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
                    >
                      Regenerate Nonce
                    </button>
                  </div>
                </form>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                    <h3 className="mb-2 text-sm font-bold text-slate-800">Revoke Credential</h3>
                    <input
                      value={revokeCredentialId}
                      onChange={(e) => setRevokeCredentialId(e.target.value)}
                      className="mb-2 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm"
                      placeholder="Credential ID"
                    />
                    <button
                      type="button"
                      onClick={handleRevoke}
                      disabled={busyAction === 'revoke'}
                      className="w-full rounded-xl bg-rose-700 px-4 py-2 text-sm font-semibold text-white hover:bg-rose-600 disabled:opacity-60"
                    >
                      {busyAction === 'revoke' ? 'Revoking...' : 'Revoke'}
                    </button>
                  </div>

                  <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                    <h3 className="mb-2 text-sm font-bold text-slate-800">Manual Reconcile</h3>
                    <input
                      value={reconcileCredentialId}
                      onChange={(e) => setReconcileCredentialId(e.target.value)}
                      className="mb-2 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm"
                      placeholder="Credential ID"
                    />
                    <button
                      type="button"
                      onClick={handleReconcile}
                      disabled={busyAction === 'reconcile'}
                      className="w-full rounded-xl bg-slate-700 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-600 disabled:opacity-60"
                    >
                      {busyAction === 'reconcile' ? 'Reconciling...' : 'Reconcile'}
                    </button>
                  </div>
                </div>
              </div>
            ) : null}

            {activeTab === 'verifier' ? (
              <div className="space-y-6">
                <h2 className="text-xl font-bold text-slate-900">Employer Verification Desk</h2>

                <div className="space-y-3 rounded-2xl border border-slate-200 bg-slate-50 p-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <h3 className="text-sm font-bold text-slate-800">Verify by Payload</h3>
                    <button
                      type="button"
                      onClick={() => setVerifyPayloadText(JSON.stringify(issuePayload, null, 2))}
                      className="rounded-lg border border-slate-300 px-2 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-100"
                    >
                      Use Current Form
                    </button>
                  </div>
                  <textarea
                    value={verifyPayloadText}
                    onChange={(e) => setVerifyPayloadText(e.target.value)}
                    rows={10}
                    className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2 font-mono text-xs text-slate-700"
                  />
                  <button
                    type="button"
                    onClick={handleVerifyPayload}
                    disabled={busyAction === 'verifyPayload'}
                    className="rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                  >
                    {busyAction === 'verifyPayload' ? 'Verifying...' : 'Verify Payload'}
                  </button>
                </div>

                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                    <h3 className="mb-2 text-sm font-bold text-slate-800">Verify by Credential ID</h3>
                    <input
                      value={verifyCredentialId}
                      onChange={(e) => setVerifyCredentialId(e.target.value)}
                      className="mb-2 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm"
                      placeholder="Credential ID"
                    />
                    <button
                      type="button"
                      onClick={handleVerifyById}
                      disabled={busyAction === 'verifyId'}
                      className="w-full rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-60"
                    >
                      {busyAction === 'verifyId' ? 'Checking...' : 'Verify ID'}
                    </button>
                  </div>

                  <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                    <h3 className="mb-2 text-sm font-bold text-slate-800">Verify by Hash</h3>
                    <input
                      value={verifyHash}
                      onChange={(e) => setVerifyHash(e.target.value)}
                      className="mb-2 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm"
                      placeholder="Credential hash"
                    />
                    <button
                      type="button"
                      onClick={handleVerifyByHash}
                      disabled={busyAction === 'verifyHash'}
                      className="w-full rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-60"
                    >
                      {busyAction === 'verifyHash' ? 'Checking...' : 'Verify Hash'}
                    </button>
                  </div>
                </div>
              </div>
            ) : null}

            {activeTab === 'student' ? (
              <div className="space-y-4">
                <h2 className="text-xl font-bold text-slate-900">Student Credential Wallet</h2>
                {issuedRecords.length === 0 ? (
                  <p className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                    No credentials in local wallet yet. Issue one from the University tab.
                  </p>
                ) : (
                  <div className="space-y-3">
                    {issuedRecords.map((record) => (
                      <div key={record.credentialId} className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div>
                            <p className="font-semibold text-slate-800">{record.credentialId}</p>
                            <p className="font-mono text-xs text-slate-600">{record.hash}</p>
                          </div>
                          <StatusPill status={record.status} />
                        </div>
                        <div className="mt-3 flex flex-wrap gap-2">
                          <button
                            type="button"
                            onClick={() => {
                              setActiveTab('verifier')
                              setVerifyCredentialId(record.credentialId)
                            }}
                            className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-100"
                          >
                            Verify by ID
                          </button>
                          <button
                            type="button"
                            onClick={async () => {
                              try {
                                await navigator.clipboard.writeText(record.hash)
                                pushLog('COPY_HASH', `Copied hash for ${record.credentialId}`)
                              } catch {
                                setErrorMessage('Clipboard access failed')
                              }
                            }}
                            className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-100"
                          >
                            Copy Hash
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ) : null}
          </section>

          <aside className="space-y-6">
            <section className="rounded-3xl border border-slate-200 bg-white/85 p-5 shadow-sm backdrop-blur sm:p-6">
              <h2 className="mb-4 text-lg font-bold text-slate-900">Latest Verification</h2>
              <ResultCard result={verificationResult} />
            </section>

            <section className="rounded-3xl border border-slate-200 bg-white/85 p-5 shadow-sm backdrop-blur sm:p-6">
              <h2 className="mb-4 text-lg font-bold text-slate-900">Activity Log</h2>
              {activityLog.length === 0 ? (
                <p className="text-sm text-slate-500">No activity yet.</p>
              ) : (
                <ul className="space-y-3">
                  {activityLog.map((entry) => (
                    <li key={`${entry.action}-${entry.at}`} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2">
                      <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">{entry.action}</p>
                      <p className="text-sm text-slate-700">{entry.details}</p>
                      <p className="text-xs text-slate-500">{formatTime(entry.at)}</p>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </aside>
        </main>
      </div>
    </div>
  )
}

export default App
