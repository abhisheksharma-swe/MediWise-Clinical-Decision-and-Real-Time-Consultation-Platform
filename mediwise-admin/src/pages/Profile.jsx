import { useEffect, useState } from 'react'
import { getProfile, updateProfile, uploadProfileImage } from '../services/profileService'
import { changePassword } from '../services/authService'
import ToastContainer, { useToast } from '../components/common/Toast'
import ErrorState from '../components/common/ErrorState'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { useAuth } from '../context/AuthContext'

export default function Profile() {
  const { user } = useAuth()
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState('')
  const [saving, setSaving]   = useState(false)
  const [uploading, setUploading] = useState(false)

  const [fullName, setFullName]   = useState('')
  const [dob, setDob]             = useState('')
  const [bloodType, setBloodType] = useState('')
  const [gender, setGender]       = useState('')
  const [address, setAddress]     = useState('')
  const [emergencyContact, setEmergencyContact] = useState('')

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword]         = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [pwLoading, setPwLoading]             = useState(false)

  const { toasts, show: showToast, dismiss } = useToast()

  function asText(value) {
    if (value == null) return ''
    return typeof value === 'string' ? value : JSON.stringify(value)
  }

  async function fetchProfile() {
    setLoading(true)
    setError('')
    try {
      const res = await getProfile()
      const p = res?.data
      setProfile(p)
      setFullName(p?.fullName || '')
      setDob(p?.dob || '')
      setBloodType(p?.bloodType || '')
      setGender(p?.gender || '')
      setAddress(asText(p?.address))
      setEmergencyContact(asText(p?.emergencyContact))
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to load profile.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchProfile() }, [])

  async function handleSave(e) {
    e.preventDefault()
    setSaving(true)
    try {
      await updateProfile({
        fullName,
        dob: dob || null,
        bloodType,
        gender,
        address,
        emergencyContact,
      })
      showToast('Profile updated successfully.', 'success')
      fetchProfile()
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to update profile.', 'error')
    } finally {
      setSaving(false)
    }
  }

  async function handleImageChange(e) {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true)
    try {
      await uploadProfileImage(file)
      showToast('Profile image uploaded.', 'success')
      fetchProfile()
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to upload image.', 'error')
    } finally {
      setUploading(false)
      e.target.value = ''
    }
  }

  async function handleChangePassword(e) {
    e.preventDefault()
    if (!currentPassword || !newPassword) {
      showToast('Please fill in both password fields.', 'warning')
      return
    }
    if (newPassword !== confirmPassword) {
      showToast('New password and confirmation do not match.', 'warning')
      return
    }
    setPwLoading(true)
    try {
      await changePassword({ currentPassword, newPassword })
      showToast('Password changed successfully.', 'success')
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to change password.', 'error')
    } finally {
      setPwLoading(false)
    }
  }

  if (error) return <ErrorState message={error} onRetry={fetchProfile} />
  if (loading) return <LoadingSpinner />

  return (
    <div className="page">
      <ToastContainer toasts={toasts} dismiss={dismiss} />

      <div className="page-header">
        <div>
          <h1 className="page-title">My Profile</h1>
          <p className="page-subtitle">Manage your administrator account details</p>
        </div>
      </div>

      <div className="card profile-card">
        <div className="profile-image-row">
          <div className="profile-avatar-lg">
            {profile?.profileImage ? (
              <img src={profile.profileImage} alt="Profile" />
            ) : (
              <span>{(fullName || user?.fullName || 'A')[0]?.toUpperCase()}</span>
            )}
          </div>
          <div>
            <label className="btn btn-outline btn-sm" htmlFor="profile-image-input" style={{ cursor: 'pointer' }}>
              {uploading ? 'Uploading...' : 'Change Photo'}
            </label>
            <input
              id="profile-image-input"
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={handleImageChange}
              disabled={uploading}
            />
          </div>
        </div>

        <form onSubmit={handleSave} className="profile-form">
          <div className="form-group">
            <label className="form-label" htmlFor="profile-fullname">Full Name</label>
            <input id="profile-fullname" className="form-input" value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="profile-dob">Date of Birth</label>
            <input id="profile-dob" type="date" className="form-input" value={dob || ''} onChange={(e) => setDob(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="profile-blood">Blood Type</label>
            <input id="profile-blood" className="form-input" placeholder="e.g. O+" value={bloodType} onChange={(e) => setBloodType(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="profile-gender">Gender</label>
            <input id="profile-gender" className="form-input" value={gender} onChange={(e) => setGender(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="profile-address">Address</label>
            <textarea id="profile-address" className="form-input" rows="2" value={address} onChange={(e) => setAddress(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="profile-emergency">Emergency Contact</label>
            <textarea id="profile-emergency" className="form-input" rows="2" value={emergencyContact} onChange={(e) => setEmergencyContact(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-primary" disabled={saving} id="save-profile-btn">
            {saving ? 'Saving...' : 'Save Changes'}
          </button>
        </form>
      </div>

      <div className="section-divider"><h3 className="section-title">Change Password</h3></div>
      <div className="card" style={{ padding: '1.5rem' }}>
        <form onSubmit={handleChangePassword} className="profile-form">
          <div className="form-group">
            <label className="form-label" htmlFor="current-password">Current Password</label>
            <input id="current-password" type="password" className="form-input" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} autoComplete="current-password" />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="profile-new-password">New Password</label>
            <input id="profile-new-password" type="password" className="form-input" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} autoComplete="new-password" />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="confirm-password">Confirm New Password</label>
            <input id="confirm-password" type="password" className="form-input" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} autoComplete="new-password" />
          </div>
          <button type="submit" className="btn btn-primary" disabled={pwLoading} id="change-password-btn">
            {pwLoading ? 'Updating...' : 'Change Password'}
          </button>
        </form>
      </div>
    </div>
  )
}
