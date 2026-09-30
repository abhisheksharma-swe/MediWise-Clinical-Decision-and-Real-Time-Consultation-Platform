import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Login from './Login'
import { AuthProvider } from '../context/AuthContext'
import * as authService from '../services/authService'

function renderLogin() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <Login />
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('Login page', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('renders the sign-in form', () => {
    renderLogin()
    expect(screen.getByText('Administrator Sign In')).toBeInTheDocument()
    expect(screen.getByLabelText('Admin Email / Phone')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toBeInTheDocument()
  })

  it('shows a validation error when submitting with empty fields', async () => {
    renderLogin()
    fireEvent.click(screen.getByText('Sign In to Portal'))

    expect(await screen.findByText('Please enter your email and password.')).toBeInTheDocument()
  })

  it('shows the server error message when login is rejected (e.g. non-admin account)', async () => {
    vi.spyOn(authService, 'loginWithEmailPassword').mockRejectedValue(
      new Error('Access denied. Administrator privileges required.')
    )

    renderLogin()
    fireEvent.change(screen.getByLabelText('Admin Email / Phone'), { target: { value: 'patient@mediwise.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByText('Sign In to Portal'))

    expect(await screen.findByText('Access denied. Administrator privileges required.')).toBeInTheDocument()
  })

  it('toggles to the forgot-password flow and back', () => {
    renderLogin()
    fireEvent.click(screen.getByText('Forgot password?'))
    expect(screen.getByText('Reset Administrator Password')).toBeInTheDocument()

    fireEvent.click(screen.getByText('Back to sign in'))
    expect(screen.getByText('Administrator Sign In')).toBeInTheDocument()
  })

  it('calls forgotPassword with the entered identifier and advances to step 2 on success', async () => {
    vi.spyOn(authService, 'forgotPassword').mockResolvedValue({})

    renderLogin()
    fireEvent.click(screen.getByText('Forgot password?'))
    fireEvent.change(screen.getByLabelText('Email / Phone'), { target: { value: 'admin@mediwise.com' } })
    fireEvent.click(screen.getByText('Send Reset Code'))

    await waitFor(() => {
      expect(authService.forgotPassword).toHaveBeenCalledWith({ emailOrPhone: 'admin@mediwise.com' })
    })
    expect(await screen.findByText('Reset Code')).toBeInTheDocument()
  })
})
