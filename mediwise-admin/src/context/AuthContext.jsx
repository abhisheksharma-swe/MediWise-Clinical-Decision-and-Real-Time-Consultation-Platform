import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { loginWithEmailPassword, logout as logoutService } from '../services/authService'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [token, setToken] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    try {
      const savedToken = localStorage.getItem('mediwise_admin_token')
      const savedUser = localStorage.getItem('mediwise_admin_user')
      if (savedToken && savedUser && savedUser !== 'undefined' && savedUser !== 'null') {
        const parsed = JSON.parse(savedUser)
        if (parsed && (parsed.role === 'ADMIN' || parsed.role === 'ROLE_ADMIN')) {
          setToken(savedToken)
          setUser(parsed)
        } else {
          localStorage.removeItem('mediwise_admin_token')
          localStorage.removeItem('mediwise_admin_user')
        }
      }
    } catch (e) {
      localStorage.removeItem('mediwise_admin_token')
      localStorage.removeItem('mediwise_admin_user')
    } finally {
      setLoading(false)
    }
  }, [])

  const login = useCallback(async (email, password) => {
    const response = await loginWithEmailPassword(email, password)
    const { accessToken, refreshToken, user: userData } = response.data

    if (userData.role !== 'ADMIN' && userData.role !== 'ROLE_ADMIN') {
      throw new Error('Access denied. Administrator privileges required.')
    }

    localStorage.setItem('mediwise_admin_token', accessToken)
    if (refreshToken) {
      localStorage.setItem('mediwise_admin_refresh_token', refreshToken)
    }
    localStorage.setItem('mediwise_admin_user', JSON.stringify(userData))
    setToken(accessToken)
    setUser(userData)
  }, [])

  const logout = useCallback(async () => {
    try {
      await logoutService()
    } catch (ignored) {}
    setToken(null)
    setUser(null)
  }, [])

  const isAuthenticated = !!token && !!user

  return (
    <AuthContext.Provider value={{ user, token, loading, isAuthenticated, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
