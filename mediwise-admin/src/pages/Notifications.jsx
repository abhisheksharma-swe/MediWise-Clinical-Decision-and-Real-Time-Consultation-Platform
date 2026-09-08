import { useEffect, useState } from 'react'
import { getNotifications, markNotificationRead, markAllNotificationsRead } from '../services/notificationService'
import DataTable from '../components/common/DataTable'
import ToastContainer, { useToast } from '../components/common/Toast'
import ErrorState from '../components/common/ErrorState'
import { CheckIcon, BellIcon } from '../components/common/Icons'

export default function Notifications() {
  const [notifications, setNotifications] = useState([])
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState('')
  const [markingId, setMarkingId] = useState(null)
  const [markingAll, setMarkingAll] = useState(false)
  const { toasts, show: showToast, dismiss } = useToast()

  async function fetchNotifications() {
    setLoading(true)
    setError('')
    try {
      const res = await getNotifications({ size: 50 })
      setNotifications(res?.data?.content || [])
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to load notifications.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchNotifications() }, [])

  async function handleMarkRead(n) {
    if (n.read) return
    setMarkingId(n.id)
    try {
      await markNotificationRead(n.id)
      setNotifications((prev) => prev.map((item) => (item.id === n.id ? { ...item, read: true } : item)))
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to mark notification as read.', 'error')
    } finally {
      setMarkingId(null)
    }
  }

  async function handleMarkAllRead() {
    setMarkingAll(true)
    try {
      await markAllNotificationsRead()
      setNotifications((prev) => prev.map((item) => ({ ...item, read: true })))
      showToast('All notifications marked as read.', 'success')
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to mark all notifications as read.', 'error')
    } finally {
      setMarkingAll(false)
    }
  }

  const unreadCount = notifications.filter((n) => !n.read).length

  const columns = [
    {
      key: 'title',
      label: 'Notification',
      render: (r) => (
        <div>
          <div style={{ fontWeight: r.read ? 500 : 700 }}>{r.title}</div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{r.body}</div>
        </div>
      ),
    },
    { key: 'type', label: 'Type', render: (r) => <span className="badge badge--blue-light">{r.type}</span> },
    { key: 'sentAt', label: 'Sent', render: (r) => r.sentAt ? new Date(r.sentAt).toLocaleString() : '—' },
    { key: 'read', label: 'Status', render: (r) => (
      <span className={r.read ? 'badge badge--gray-light' : 'badge badge--success-light'}>
        {r.read ? 'Read' : 'Unread'}
      </span>
    ) },
    {
      key: 'actions',
      label: 'Actions',
      render: (r) => (
        !r.read ? (
          <button
            className="btn btn-outline btn-sm"
            onClick={() => handleMarkRead(r)}
            disabled={markingId === r.id}
            id={`mark-read-${r.id}`}
          >
            <CheckIcon size={14} />
            <span>{markingId === r.id ? 'Marking...' : 'Mark Read'}</span>
          </button>
        ) : (
          <span className="text-muted" style={{ fontSize: '0.8rem' }}>—</span>
        )
      ),
    },
  ]

  if (error) return <ErrorState message={error} onRetry={fetchNotifications} />

  return (
    <div className="page">
      <ToastContainer toasts={toasts} dismiss={dismiss} />

      <div className="page-header">
        <div>
          <h1 className="page-title">Notifications</h1>
          <p className="page-subtitle">
            <BellIcon size={14} className="inline-icon" /> {unreadCount} unread notification{unreadCount === 1 ? '' : 's'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button className="btn btn-primary btn-sm" onClick={handleMarkAllRead} disabled={markingAll || unreadCount === 0} id="mark-all-read-btn">
            {markingAll ? 'Marking...' : 'Mark All Read'}
          </button>
          <button className="btn btn-outline btn-sm" onClick={fetchNotifications}>
            Refresh
          </button>
        </div>
      </div>

      <div className="card">
        <DataTable
          columns={columns}
          data={notifications}
          loading={loading}
          emptyMessage="No notifications yet."
        />
      </div>
    </div>
  )
}
