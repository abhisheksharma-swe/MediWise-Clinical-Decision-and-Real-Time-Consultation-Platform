import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { API_BASE_URL } from '../utils/constants'

/**
 * Thin wrapper around @stomp/stompjs + sockjs-client for the admin chat page.
 * Each call to createStompClient() returns a fresh, unactivated Client —
 * callers own the lifecycle (activate on mount, deactivate on unmount).
 */
export function createStompClient({ onConnect, onDisconnect, onError } = {}) {
  const client = new Client({
    webSocketFactory: () => new SockJS(`${API_BASE_URL || ''}/ws`),
    reconnectDelay: 5000,
    // Re-read the access token before every connect attempt (including
    // auto-reconnects), so a long-lived chat session picks up a token
    // refreshed elsewhere (e.g. by api.js's silent-refresh interceptor)
    // instead of reconnecting with a stale, expired one.
    beforeConnect: () => {
      client.connectHeaders = {
        Authorization: `Bearer ${localStorage.getItem('mediwise_admin_token')}`,
      }
    },
    onConnect: () => onConnect && onConnect(),
    onDisconnect: () => onDisconnect && onDisconnect(),
    onStompError: (frame) => onError && onError(frame),
    onWebSocketError: (event) => onError && onError(event),
  })

  return client
}

export function subscribeTopic(client, destination, callback) {
  if (!client || !client.connected) return null
  return client.subscribe(destination, (message) => {
    try {
      callback(JSON.parse(message.body))
    } catch (e) {
      callback(message.body)
    }
  })
}

/**
 * body is JSON-encoded by default (matches SendMessageRequest/TypingEvent).
 * Pass { raw: true } to send a bare string payload (used by /app/chat.read,
 * whose @Payload is a plain String, not a JSON object).
 */
export function publishMessage(client, destination, body, { raw = false } = {}) {
  if (!client || !client.connected) return
  if (raw) {
    client.publish({
      destination,
      body: String(body),
      headers: { 'content-type': 'text/plain' },
    })
  } else {
    client.publish({
      destination,
      body: JSON.stringify(body),
      headers: { 'content-type': 'application/json' },
    })
  }
}
