import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { forgotPassword, resetPassword } from '../services/authService'
import { HospitalIcon, EyeIcon, EyeOffIcon, AlertCircleIcon, CheckCircleIcon } from '../components/common/Icons'

export default function Login() {
  const [email, setEmail]       = useState('')
  const [password, setPassword] = useState('')
  const [error, setError]       = useState('')
  const [loading, setLoading]   = useState(false)
  const [showPass, setShowPass] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  // Forgot / reset password (inline, two-step)
  const [showForgot, setShowForgot]         = useState(false)
  const [forgotStep, setForgotStep]         = useState(1) // 1 = request code, 2 = set new password
  const [forgotIdentifier, setForgotIdentifier] = useState('')
  const [resetToken, setResetToken]         = useState('')
  const [newPassword, setNewPassword]       = useState('')
  const [forgotLoading, setForgotLoading]   = useState(false)
  const [forgotError, setForgotError]       = useState('')
  const [forgotMessage, setForgotMessage]   = useState('')

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    if (!email || !password) {
      setError('Please enter your email and password.')
      return
    }
    setLoading(true)
    try {
      await login(email, password)
      navigate('/')
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Login failed. Please verify credentials.'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  function toggleForgot() {
    setShowForgot((v) => !v)
    setForgotStep(1)
    setForgotError('')
    setForgotMessage('')
    setForgotIdentifier('')
    setResetToken('')
    setNewPassword('')
  }

  async function handleForgotSubmit(e) {
    e.preventDefault()
    setForgotError('')
    setForgotMessage('')
    if (!forgotIdentifier) {
      setForgotError('Please enter your email or phone.')
      return
    }
    setForgotLoading(true)
    try {
      await forgotPassword({ emailOrPhone: forgotIdentifier })
      setForgotMessage('If an account exists, a reset code has been sent.')
      setForgotStep(2)
    } catch (err) {
      setForgotError(err?.response?.data?.message || err?.message || 'Request failed. Please try again.')
    } finally {
      setForgotLoading(false)
    }
  }

  async function handleResetSubmit(e) {
    e.preventDefault()
    setForgotError('')
    setForgotMessage('')
    if (!resetToken || !newPassword) {
      setForgotError('Please enter the reset code and a new password.')
      return
    }
    setForgotLoading(true)
    try {
      await resetPassword({ emailOrPhone: forgotIdentifier, token: resetToken, newPassword })
      setForgotMessage('Password reset successfully. You can now sign in.')
      setTimeout(() => toggleForgot(), 1500)
    } catch (err) {
      setForgotError(err?.response?.data?.message || err?.message || 'Reset failed. Please verify the code and try again.')
    } finally {
      setForgotLoading(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        {/* Brand */}
        <div className="login-brand">
          <div className="login-brand-icon">
            <HospitalIcon size={32} />
          </div>
          <h1 className="login-brand-name">MediWise</h1>
          <p className="login-brand-sub">Clinical Administration Portal</p>
        </div>

        {!showForgot ? (
          <>
            <h2 className="login-title">Administrator Sign In</h2>
            <p className="login-subtitle">Enter your authorized administrator credentials to continue</p>

            <form className="login-form" onSubmit={handleSubmit} noValidate>
              <div className="form-group">
                <label htmlFor="login-email" className="form-label">Admin Email / Phone</label>
                <input
                  id="login-email"
                  type="text"
                  className="form-input"
                  placeholder="e.g. abhishek@admin.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  autoComplete="username"
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="login-password" className="form-label">Password</label>
                <div className="input-with-icon">
                  <input
                    id="login-password"
                    type={showPass ? 'text' : 'password'}
                    className="form-input"
                    placeholder="Enter password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete="current-password"
                    required
                  />
                  <button
                    type="button"
                    className="input-icon-btn"
                    onClick={() => setShowPass((p) => !p)}
                    aria-label={showPass ? 'Hide password' : 'Show password'}
                  >
                    {showPass ? <EyeOffIcon size={18} /> : <EyeIcon size={18} />}
                  </button>
                </div>
              </div>

              {error && (
                <div className="alert alert--error" role="alert">
                  <AlertCircleIcon size={18} className="alert-icon" />
                  <span>{error}</span>
                </div>
              )}

              <button
                type="submit"
                className="btn btn-primary btn-full"
                disabled={loading}
                id="login-submit-btn"
              >
                {loading ? 'Authenticating...' : 'Sign In to Portal'}
              </button>
            </form>

            <button type="button" className="link-btn" onClick={toggleForgot} id="forgot-password-link">
              Forgot password?
            </button>
          </>
        ) : (
          <>
            <h2 className="login-title">Reset Administrator Password</h2>
            <p className="login-subtitle">
              {forgotStep === 1
                ? 'Enter your account email or phone to receive a reset code'
                : 'Enter the reset code you received and choose a new password'}
            </p>

            <form
              className="login-form"
              onSubmit={forgotStep === 1 ? handleForgotSubmit : handleResetSubmit}
              noValidate
            >
              {forgotStep === 1 ? (
                <div className="form-group">
                  <label htmlFor="forgot-identifier" className="form-label">Email / Phone</label>
                  <input
                    id="forgot-identifier"
                    type="text"
                    className="form-input"
                    placeholder="Registered email or phone"
                    value={forgotIdentifier}
                    onChange={(e) => setForgotIdentifier(e.target.value)}
                    autoComplete="username"
                    required
                  />
                </div>
              ) : (
                <>
                  <div className="form-group">
                    <label htmlFor="reset-token" className="form-label">Reset Code</label>
                    <input
                      id="reset-token"
                      type="text"
                      className="form-input"
                      placeholder="Code from the reset message"
                      value={resetToken}
                      onChange={(e) => setResetToken(e.target.value)}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label htmlFor="new-password" className="form-label">New Password</label>
                    <input
                      id="new-password"
                      type="password"
                      className="form-input"
                      placeholder="At least 6 characters"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      autoComplete="new-password"
                      required
                    />
                  </div>
                </>
              )}

              {forgotError && (
                <div className="alert alert--error" role="alert">
                  <AlertCircleIcon size={18} className="alert-icon" />
                  <span>{forgotError}</span>
                </div>
              )}

              {forgotMessage && (
                <div className="alert alert--success" role="status">
                  <CheckCircleIcon size={18} className="alert-icon" />
                  <span>{forgotMessage}</span>
                </div>
              )}

              <button
                type="submit"
                className="btn btn-primary btn-full"
                disabled={forgotLoading}
                id="forgot-submit-btn"
              >
                {forgotLoading ? 'Please wait...' : forgotStep === 1 ? 'Send Reset Code' : 'Reset Password'}
              </button>
            </form>

            <button type="button" className="link-btn" onClick={toggleForgot} id="back-to-login-link">
              Back to sign in
            </button>
          </>
        )}

        <div className="login-footer">
          <span>Protected Healthcare Information System</span>
          <span>MediWise Clinical Governance v1.0.0</span>
        </div>
      </div>
    </div>
  )
}
