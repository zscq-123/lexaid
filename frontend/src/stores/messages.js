import { defineStore } from 'pinia'
import { getMessageTicket, getUnreadTotal } from '../api/messages'
import { useUserStore } from './user'

let socket = null
let reconnectTimer = null
let pingTimer = null
let pollTimer = null
let attempts = 0
let connecting = false
let generation = 0
let manualClose = false
const listeners = new Set()

function emit(event) {
  for (const listener of [...listeners]) {
    try {
      listener(event)
    } catch {
      // A failed view subscription must not break the shared connection.
    }
  }
}

function socketUrl(ticket) {
  const explicit = window.lexaidConfig?.wsBaseUrl
  const raw = explicit || window.lexaidConfig?.apiBaseUrl || import.meta.env.VITE_API_BASE_URL || '/api'
  try {
    const url = new URL(raw, window.location.origin)
    const websocketProtocol = url.protocol === 'ws:' || url.protocol === 'wss:'
    if (!websocketProtocol && url.protocol !== 'http:' && url.protocol !== 'https:') return null
    if (!websocketProtocol) url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    if (!explicit) url.pathname = url.pathname.replace(/\/api\/?$/, '') + '/ws/messages'
    url.search = ''
    url.searchParams.set('ticket', ticket)
    return url.toString()
  } catch {
    return null
  }
}

export const useMessageStore = defineStore('messages', {
  state: () => ({
    unreadTotal: 0,
    connected: false,
    connecting: false
  }),
  getters: {
    hasUnread: (state) => state.unreadTotal > 0
  },
  actions: {
    subscribe(listener) {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    setUnread(total) {
      this.unreadTotal = Math.max(0, Number(total) || 0)
    },
    async refreshUnread() {
      if (!useUserStore().token) return
      try {
        const data = await getUnreadTotal()
        if (data && typeof data.total === 'number') this.setUnread(data.total)
      } catch {
        // The API layer reports actual failures; a transient count refresh is best effort.
      }
    },
    startPolling() {
      if (pollTimer) return
      this.refreshUnread()
      pollTimer = setInterval(() => {
        if (!useUserStore().token) {
          this.stopPolling()
          return
        }
        this.refreshUnread()
        emit({ type: 'POLL' })
      }, 5000)
    },
    stopPolling() {
      if (pollTimer) clearInterval(pollTimer)
      pollTimer = null
    },
    startPing() {
      this.stopPing()
      pingTimer = setInterval(() => {
        if (!socket) return
        if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: 'PING' }))
        else if (socket.readyState !== WebSocket.CONNECTING) socket.close()
      }, 25000)
    },
    stopPing() {
      if (pingTimer) clearInterval(pingTimer)
      pingTimer = null
    },
    scheduleReconnect() {
      if (reconnectTimer || manualClose || !useUserStore().token) return
      const delay = Math.min(30000, 1000 * 2 ** Math.min(attempts, 5)) + Math.random() * 400
      attempts += 1
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null
        this.connect()
      }, delay)
    },
    async connect() {
      const user = useUserStore()
      if (!user.token || socket || connecting) return
      const current = ++generation
      connecting = true
      this.connecting = true
      manualClose = false
      try {
        const data = await getMessageTicket()
        if (current !== generation || !useUserStore().token) {
          connecting = false
          this.connecting = false
          return
        }
        const url = socketUrl(data.ticket)
        if (!url) {
          connecting = false
          this.connecting = false
          this.startPolling()
          emit({ type: 'FALLBACK' })
          return
        }
        const ws = new WebSocket(url)
        socket = ws
        const openTimer = setTimeout(() => {
          if (ws.readyState !== WebSocket.OPEN) {
            try { ws.close() } catch { /* onclose performs cleanup. */ }
          }
        }, 10000)
        ws.onopen = () => {
          clearTimeout(openTimer)
          if (current !== generation) {
            ws.close()
            return
          }
          if (reconnectTimer) clearTimeout(reconnectTimer)
          reconnectTimer = null
          attempts = 0
          this.connected = true
          this.connecting = false
          connecting = false
          this.stopPolling()
          this.startPing()
          this.refreshUnread()
          emit({ type: 'CONNECTED' })
        }
        ws.onmessage = (event) => {
          let payload
          try {
            payload = JSON.parse(event.data)
          } catch {
            return
          }
          if (typeof payload.unreadTotal === 'number') this.setUnread(payload.unreadTotal)
          if (payload.type === 'PONG' || payload.type === 'READY') return
          emit(payload)
        }
        ws.onerror = () => {
          try {
            ws.close()
          } catch {
            // onclose performs cleanup and reconnection.
          }
        }
        ws.onclose = () => {
          clearTimeout(openTimer)
          if (socket === ws) socket = null
          this.connected = false
          this.connecting = false
          connecting = false
          this.stopPing()
          emit({ type: 'DISCONNECTED' })
          if (!manualClose && useUserStore().token) {
            this.startPolling()
            this.scheduleReconnect()
          }
        }
      } catch {
        socket = null
        connecting = false
        this.connected = false
        this.connecting = false
        if (manualClose || !useUserStore().token) return
        this.startPolling()
        this.scheduleReconnect()
      }
    },
    ensureConnected() {
      if (!useUserStore().token) return
      if (!socket && !connecting) this.connect()
      if (!this.connected && !pollTimer) this.startPolling()
    },
    disconnect() {
      manualClose = true
      generation += 1
      connecting = false
      this.connected = false
      this.connecting = false
      this.unreadTotal = 0
      this.stopPing()
      this.stopPolling()
      if (reconnectTimer) clearTimeout(reconnectTimer)
      reconnectTimer = null
      const current = socket
      socket = null
      if (current) {
        current.onclose = null
        current.close()
      }
    }
  }
})
