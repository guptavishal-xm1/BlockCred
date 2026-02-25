const rawBase = import.meta.env.VITE_API_BASE_URL || '/api'
const API_BASE = rawBase.endsWith('/') ? rawBase.slice(0, -1) : rawBase

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
      body: JSON.stringify({ payload }),
    })
  },

  revokeCredential(credentialId) {
    return request(`/credentials/${encodeURIComponent(credentialId)}/revoke`, {
      method: 'POST',
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
      headers: { 'X-Admin': 'true' },
    })
  },
}
