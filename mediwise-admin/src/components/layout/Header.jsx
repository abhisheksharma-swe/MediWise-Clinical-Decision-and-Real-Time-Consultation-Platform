import { useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { useNavigate } from 'react-router-dom'
import { LogOutIcon, BellIcon } from '../common/Icons'
import { getNotifications } from '../../services/notificationService'

export default function Header() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [unreadCount, setUnreadCount] = useState(0)

  useEffect(() => {
    let cancelled = false
    async function fetchUnread() {
      try {
        const res = await getNotifications({ size: 50 })
        const list = res?.data?.content || []
        if (!cancelled) setUnreadCount(list.filter((n) => !n.read).length)
      } catch (err) {
        // Header badge is a convenience — ignore failures silently
      }
    }
    fetchUnread()
    return () => { cancelled = true }
  }, [])

  async function handleLogout() {
    await logout()
    navigate('/login')
  }

  return (
    <header className="admin-header">
      <div className="admin-header-left">
        <h2 className="admin-header-title">Admin Management Portal</h2>
      </div>
      <div className="admin-header-right">
        <button
          className="header-bell-btn"
          onClick={() => navigate('/notifications')}
          aria-label="Notifications"
          id="header-bell-btn"
        >
          <BellIcon size={20} />
          {unreadCount > 0 && <span className="header-bell-badge">{unreadCount > 9 ? '9+' : unreadCount}</span>}
        </button>

        <button
          className="admin-header-user admin-header-user--btn"
          onClick={() => navigate('/profile')}
          id="header-profile-btn"
        >
          <div className="admin-header-avatar">
            {user?.fullName?.[0]?.toUpperCase() || 'A'}
          </div>
          <div className="admin-header-user-info">
            <span className="admin-header-user-name">{user?.fullName || 'Administrator'}</span>
            <span className="admin-header-user-role">System Admin</span>
          </div>
        </button>

        <button
          className="btn btn-outline btn-sm logout-btn"
          onClick={handleLogout}
          id="logout-btn"
        >
          <LogOutIcon size={16} />
          <span>Logout</span>
        </button>
      </div>
    </header>
  )
}
