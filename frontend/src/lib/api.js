const rawBase = import.meta.env.VITE_API_BASE_URL || '/api'
const API_BASE = rawBase.endsWith('/') ? rawBase.slice(0, -1) : rawBase
const ADMIN_TOKEN = import.meta.env.VITE_ADMIN_TOKEN || 'blockcred-admin-dev-token-change-me'
const ISSUER_TOKEN = import.meta.env.VITE_ISSUER_TOKEN || ''

function issuerHeaders() {
  if (!ISSUER_TOKEN) {
    return {}
  }
  return { 'X-Issuer-Token': ISSUER_TOKEN }
}

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    ...options,
  })

  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('application/json')
    ? await response.json()
    : await response.text()

  if (!response.ok) {
    const message =
      typeof payload === 'string'
        ? payload
        : payload?.error || payload?.message || 'Request failed'
    throw new Error(message)
  }

  return payload
}

export const api = {
  issueCredential(payload) {
    return request('/credentials', {
      method: 'POST',
      headers: issuerHeaders(),
      body: JSON.stringify({ payload }),
    })
  },

  revokeCredential(credentialId) {
    return request(`/credentials/${encodeURIComponent(credentialId)}/revoke`, {
      method: 'POST',
      headers: issuerHeaders(),
    })
  },

  verifyPayload(payload) {
    return request('/verify/payload', {
      method: 'POST',
      body: JSON.stringify({ payload }),
    })
  },

  verifyCredentialId(credentialId) {
    return request(`/verify?credentialId=${encodeURIComponent(credentialId)}`)
  },

  verifyHash(hash) {
    return request(`/verify/hash/${encodeURIComponent(hash)}`)
  },

  reconcileCredential(credentialId) {
    return request(`/ops/reconcile/${encodeURIComponent(credentialId)}`, {
      method: 'POST',
      headers: { 'X-Admin-Token': ADMIN_TOKEN },
    })
  },

  opsCredentialState(credentialId) {
    return request(`/ops/credentials/${encodeURIComponent(credentialId)}/state`, {
      headers: { 'X-Admin-Token': ADMIN_TOKEN },
    })
  },

  opsSummary() {
    return request('/ops/summary', {
      headers: { 'X-Admin-Token': ADMIN_TOKEN },
    })
  },

  opsAnomalies(limit = 20) {
    return request(`/ops/anomalies?limit=${encodeURIComponent(limit)}`, {
      headers: { 'X-Admin-Token': ADMIN_TOKEN },
    })
  },

  walletStatus() {
    return request('/ops/wallet/status', {
      headers: { 'X-Admin-Token': ADMIN_TOKEN },
    })
  },

  walletDisable(reason) {
    return request('/ops/wallet/disable', {
      method: 'POST',
      headers: { 'X-Admin-Token': ADMIN_TOKEN },
      body: JSON.stringify({ reason }),
    })
  },

  walletEnable() {
    return request('/ops/wallet/enable', {
      method: 'POST',
      headers: { 'X-Admin-Token': ADMIN_TOKEN },
    })
  },

  publicVerifyToken(token, options = {}) {
    return request(`/public/verify?t=${encodeURIComponent(token)}`, {
      ...options,
    })
  },
}
