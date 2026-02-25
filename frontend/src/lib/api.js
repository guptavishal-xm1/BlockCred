const rawBase = import.meta.env.VITE_API_BASE_URL || '/api'
const API_BASE = rawBase.endsWith('/') ? rawBase.slice(0, -1) : rawBase
const SESSION_KEY = 'blockcred.auth.session'

function parseJson(value) {
  if (!value) {
    return null
  }
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function readSession() {
  return parseJson(window.localStorage.getItem(SESSION_KEY))
}

function writeSession(session) {
  if (!session) {
    window.localStorage.removeItem(SESSION_KEY)
    return
  }
  window.localStorage.setItem(SESSION_KEY, JSON.stringify(session))
}

async function refreshSession() {
  const current = readSession()
  const refreshToken = current?.refreshToken
  if (!refreshToken) {
    return null
  }

  const response = await fetch(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  if (!response.ok) {
    writeSession(null)
    return null
  }
  const payload = await response.json()
  writeSession(payload)
  return payload
}

async function request(path, options = {}, config = {}) {
  const { auth = true, retry = true } = config
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  }

  if (auth) {
    const session = readSession()
    if (session?.accessToken) {
      headers.Authorization = `Bearer ${session.accessToken}`
    }
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  })

  if (response.status === 401 && auth && retry) {
    const refreshed = await refreshSession()
    if (refreshed?.accessToken) {
      return request(path, options, { ...config, retry: false })
    }
  }

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

function redirectPathForRoles(roles = []) {
  if (roles.includes('ADMIN')) {
    return '/ops'
  }
  return '/issuer'
}

export const authSession = {
  get() {
    return readSession()
  },

  set(session) {
    writeSession(session)
  },

  clear() {
    writeSession(null)
  },

  async login(usernameOrEmail, password) {
    const session = await request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ usernameOrEmail, password }),
    }, { auth: false, retry: false })
    writeSession(session)
    return session
  },

  async logout() {
    const session = readSession()
    try {
      await request('/auth/logout', {
        method: 'POST',
        body: JSON.stringify({ refreshToken: session?.refreshToken || null }),
      }, { auth: true, retry: false })
    } catch {
      // Client-side logout should continue even if server token already expired.
    } finally {
      writeSession(null)
    }
  },

  redirectPathForRoles,
}

export const api = {
  authMe() {
    return request('/auth/me')
  },

  changePassword(currentPassword, newPassword) {
    return request('/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    })
  },

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

  shareLink(credentialId) {
    return request(`/credentials/${encodeURIComponent(credentialId)}/share-link`, {
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
    })
  },

  opsCredentialState(credentialId) {
    return request(`/ops/credentials/${encodeURIComponent(credentialId)}/state`)
  },

  opsSummary() {
    return request('/ops/summary')
  },

  opsAnomalies(limit = 20) {
    return request(`/ops/anomalies?limit=${encodeURIComponent(limit)}`)
  },

  opsSecurityStatus() {
    return request('/ops/security/status')
  },

  walletStatus() {
    return request('/ops/wallet/status')
  },

  walletDisable(reason) {
    return request('/ops/wallet/disable', {
      method: 'POST',
      body: JSON.stringify({ reason }),
    })
  },

  walletEnable() {
    return request('/ops/wallet/enable', {
      method: 'POST',
    })
  },

  publicVerifyToken(token, options = {}) {
    return request(`/public/verify?t=${encodeURIComponent(token)}`, {
      ...options,
    }, { auth: false, retry: false })
  },
}
