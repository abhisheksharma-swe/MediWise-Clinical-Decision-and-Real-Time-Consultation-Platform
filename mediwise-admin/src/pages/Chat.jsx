import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getChatMessages } from '../services/chatService'
import { createStompClient, subscribeTopic, publishMessage } from '../services/stompClient'
import { useAuth } from '../context/AuthContext'
import ErrorState from '../components/common/ErrorState'
import { SendIcon } from '../components/common/Icons'

export default function Chat() {
  const { roomId } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [messages, setMessages] = useState([])
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState('')
  const [connected, setConnected] = useState(false)
  const [text, setText]         = useState('')
  const [otherTyping, setOtherTyping] = useState(false)
  const [readBy, setReadBy]     = useState(null)

  const clientRef = useRef(null)
  const typingTimeoutRef = useRef(null)
  const messagesEndRef = useRef(null)

  // REST history load
  useEffect(() => {
    let cancelled = false
    async function loadHistory() {
      setLoading(true)
      setError('')
      try {
        const res = await getChatMessages(roomId, { size: 100 })
        if (!cancelled) {
          const list = (res?.data?.content || [])
            .slice()
            .sort((a, b) => new Date(a.sentAt) - new Date(b.sentAt))
          setMessages(list)
        }
      } catch (err) {
        if (!cancelled) setError(err?.response?.data?.message || 'Failed to load chat history.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    loadHistory()
    return () => { cancelled = true }
  }, [roomId])

  // STOMP connection lifecycle
  useEffect(() => {
    const client = createStompClient({
      onConnect: () => {
        setConnected(true)
        subscribeTopic(client, `/topic/chat/${roomId}`, (msg) => {
          setMessages((prev) => [...prev, msg])
        })
        subscribeTopic(client, `/topic/chat/${roomId}/typing`, (evt) => {
          if (evt?.senderId && evt.senderId !== user?.id) {
            setOtherTyping(!!evt.typing)
          }
        })
        subscribeTopic(client, `/topic/chat/${roomId}/read`, (readerId) => {
          if (readerId && readerId !== user?.id) {
            setReadBy(readerId)
          }
        })
        // Mark this room's messages as read now that the admin has opened it
        publishMessage(client, '/app/chat.read', roomId, { raw: true })
      },
      onDisconnect: () => setConnected(false),
      onError: () => setConnected(false),
    })
    clientRef.current = client
    client.activate()

    return () => {
      client.deactivate()
      clientRef.current = null
    }
  }, [roomId, user?.id])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  function handleTextChange(e) {
    setText(e.target.value)
    const client = clientRef.current
    if (client && client.connected) {
      publishMessage(client, '/app/chat.typing', { roomId, typing: true })
      if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current)
      typingTimeoutRef.current = setTimeout(() => {
        publishMessage(client, '/app/chat.typing', { roomId, typing: false })
      }, 2000)
    }
  }

  function handleSend(e) {
    e.preventDefault()
    const trimmed = text.trim()
    if (!trimmed) return
    const client = clientRef.current
    if (!client || !client.connected) return
    publishMessage(client, '/app/chat.send', {
      roomId,
      content: trimmed,
      contentType: 'TEXT',
    })
    setText('')
  }

  if (error) return <ErrorState message={error} onRetry={() => window.location.reload()} />

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">Appointment Chat</h1>
          <p className="page-subtitle">
            Room: <code>{roomId}</code> — {connected ? 'Connected' : 'Connecting...'}
          </p>
        </div>
        <button className="btn btn-outline btn-sm" onClick={() => navigate('/appointments')}>
          Back to Appointments
        </button>
      </div>

      <div className="card chat-card">
        <div className="chat-messages">
          {loading ? (
            <div className="loading-inline"><div className="spinner" /></div>
          ) : messages.length === 0 ? (
            <div className="table-empty"><p>No messages yet. Start the conversation.</p></div>
          ) : (
            messages.map((m, i) => {
              const isOwn = m.senderId === user?.id
              return (
                <div key={m.id || i} className={`chat-message${isOwn ? ' chat-message--own' : ''}`}>
                  <div className="chat-message-meta">
                    <span className="chat-message-sender">{isOwn ? 'You' : (m.senderRole || 'User')}</span>
                    <span className="chat-message-time">{m.sentAt ? new Date(m.sentAt).toLocaleTimeString() : ''}</span>
                  </div>
                  <div className="chat-message-bubble">{m.content}</div>
                </div>
              )
            })
          )}
          <div ref={messagesEndRef} />
        </div>

        {otherTyping && <div className="chat-typing-indicator">The other participant is typing…</div>}
        {readBy && <div className="chat-read-indicator">Seen by participant</div>}

        <form className="chat-input-row" onSubmit={handleSend}>
          <input
            className="form-input"
            placeholder="Type a message..."
            value={text}
            onChange={handleTextChange}
            id="chat-input"
          />
          <button type="submit" className="btn btn-primary" disabled={!connected || !text.trim()} id="chat-send-btn">
            <SendIcon size={16} />
            <span>Send</span>
          </button>
        </form>
      </div>
    </div>
  )
}
