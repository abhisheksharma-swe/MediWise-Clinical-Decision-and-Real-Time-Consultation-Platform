import { useEffect, useState } from 'react'
import { getPatients, togglePatientStatus, updateUserRole } from '../services/patientService'
import { getPatientAiReports } from '../services/aiService'
import DataTable from '../components/common/DataTable'
import Badge from '../components/common/Badge'
import Modal from '../components/common/Modal'
import ToastContainer, { useToast } from '../components/common/Toast'
import ErrorState from '../components/common/ErrorState'
import { USER_ROLES } from '../utils/constants'
import { SearchIcon, CheckCircleIcon, XCircleIcon, ShieldCheckIcon, ZapIcon } from '../components/common/Icons'

const ROLE_OPTIONS = Object.values(USER_ROLES)

export default function Patients() {
  const [patients, setPatients] = useState([])
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState('')
  const [search, setSearch]     = useState('')
  const [selected, setSelected] = useState(null)
  const [actionType, setActionType] = useState('suspend') // 'suspend' | 'activate'
  const [statusLoading, setStatusLoading] = useState(false)

  // Role change
  const [roleTarget, setRoleTarget] = useState(null)
  const [newRole, setNewRole]       = useState(USER_ROLES.PATIENT)
  const [roleLoading, setRoleLoading] = useState(false)

  // AI clinical reports (read-only)
  const [aiTarget, setAiTarget]       = useState(null)
  const [aiReports, setAiReports]     = useState([])
  const [aiLoading, setAiLoading]     = useState(false)
  const [aiError, setAiError]         = useState('')

  const { toasts, show: showToast, dismiss } = useToast()

  async function fetchPatients() {
    setLoading(true)
    setError('')
    try {
      const params = { size: 50 }
      if (search) params.search = search
      const res = await getPatients(params)
      setPatients(res?.data?.content || [])
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to load patient records.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchPatients() }, [search])

  async function handleToggleStatus() {
    if (!selected) return
    setStatusLoading(true)
    const newActive = actionType === 'activate'
    try {
      await togglePatientStatus(selected.id, newActive)
      showToast(
        `Patient account for ${selected.fullName || selected.email} ${newActive ? 'reactivated' : 'suspended'}.`,
        newActive ? 'success' : 'warning'
      )
      setSelected(null)
      fetchPatients()
    } catch (err) {
      showToast(err?.response?.data?.message || 'Status update failed.', 'error')
    } finally {
      setStatusLoading(false)
    }
  }

  function openRoleModal(patient) {
    setRoleTarget(patient)
    setNewRole(patient.role || USER_ROLES.PATIENT)
  }

  async function handleRoleChange() {
    if (!roleTarget) return
    setRoleLoading(true)
    try {
      await updateUserRole(roleTarget.id, newRole)
      showToast(`Role for ${roleTarget.fullName || roleTarget.email} updated to ${newRole}.`, 'success')
      setRoleTarget(null)
      fetchPatients()
    } catch (err) {
      showToast(err?.response?.data?.message || 'Role update failed.', 'error')
    } finally {
      setRoleLoading(false)
    }
  }

  async function openAiReports(patient) {
    setAiTarget(patient)
    setAiLoading(true)
    setAiError('')
    setAiReports([])
    try {
      const res = await getPatientAiReports(patient.id)
      setAiReports(res?.data || [])
    } catch (err) {
      setAiError(err?.response?.data?.message || 'Failed to load AI clinical reports for this patient.')
    } finally {
      setAiLoading(false)
    }
  }

  const columns = [
    {
      key: 'fullName',
      label: 'Patient Name',
      render: (r) => (
        <div>
          <div style={{ fontWeight: 600 }}>{r.fullName || 'Unspecified'}</div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>ID: {r.id?.slice(0, 8)}...</div>
        </div>
      ),
    },
    { key: 'email',       label: 'Email Address' },
    { key: 'phone',       label: 'Phone', render: (r) => r.phone || '—' },
    { key: 'createdAt',   label: 'Registration Date', render: (r) => r.createdAt ? new Date(r.createdAt).toLocaleDateString() : '—' },
    { key: 'active',      label: 'Account Status', render: (r) => <Badge status={r.active ? 'ACTIVE' : 'SUSPENDED'} /> },
    {
      key: 'actions',
      label: 'Access Control',
      render: (r) => (
        <div className="action-btns">
          {r.active ? (
            <button
              className="btn btn-danger btn-sm"
              onClick={() => { setSelected(r); setActionType('suspend') }}
              id={`suspend-patient-${r.id}`}
            >
              <XCircleIcon size={14} />
              <span>Suspend</span>
            </button>
          ) : (
            <button
              className="btn btn-success btn-sm"
              onClick={() => { setSelected(r); setActionType('activate') }}
              id={`activate-patient-${r.id}`}
            >
              <CheckCircleIcon size={14} />
              <span>Reactivate</span>
            </button>
          )}
          <button
            className="btn btn-outline btn-sm"
            onClick={() => openRoleModal(r)}
            id={`change-role-${r.id}`}
          >
            <ShieldCheckIcon size={14} />
            <span>Role</span>
          </button>
          <button
            className="btn btn-outline btn-sm"
            onClick={() => openAiReports(r)}
            id={`ai-reports-${r.id}`}
          >
            <ZapIcon size={14} />
            <span>AI Reports</span>
          </button>
        </div>
      ),
    },
  ]

  if (error) return <ErrorState message={error} onRetry={fetchPatients} />

  return (
    <div className="page">
      <ToastContainer toasts={toasts} dismiss={dismiss} />

      <div className="page-header">
        <div>
          <h1 className="page-title">Patient Management</h1>
          <p className="page-subtitle">Inspect registered patient profiles, contact details, and account standing</p>
        </div>
        <button className="btn btn-outline btn-sm" onClick={fetchPatients}>
          Refresh List
        </button>
      </div>

      <div className="filter-bar">
        <div className="search-input-wrapper">
          <SearchIcon size={16} className="search-icon" />
          <input
            id="patient-search"
            type="text"
            className="form-input search-input"
            placeholder="Search patient by name, email or phone..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      <div className="card">
        <DataTable
          columns={columns}
          data={patients}
          loading={loading}
          emptyMessage="No patient accounts registered matching your criteria."
        />
      </div>

      <Modal
        isOpen={!!selected}
        title={actionType === 'suspend' ? 'Suspend Patient Access' : 'Reactivate Patient Access'}
        onClose={() => setSelected(null)}
        onConfirm={handleToggleStatus}
        confirmText={actionType === 'suspend' ? 'Confirm Suspension' : 'Confirm Reactivation'}
        confirmVariant={actionType === 'suspend' ? 'danger' : 'success'}
        loading={statusLoading}
      >
        <p>
          Are you sure you want to {actionType === 'suspend' ? 'suspend' : 'reactivate'} the account for{' '}
          <strong>{selected?.fullName || selected?.email}</strong>?
        </p>
        <p className="text-muted" style={{ marginTop: '0.5rem' }}>
          {actionType === 'suspend'
            ? 'The patient will be prevented from booking appointments and logging into the platform until restored.'
            : 'The patient will immediately be restored full consultation and platform booking privileges.'}
        </p>
      </Modal>

      <Modal
        isOpen={!!roleTarget}
        title="Change User Role"
        onClose={() => setRoleTarget(null)}
        onConfirm={handleRoleChange}
        confirmText="Update Role"
        confirmVariant="primary"
        loading={roleLoading}
      >
        <p>
          Update platform role for <strong>{roleTarget?.fullName || roleTarget?.email}</strong>.
        </p>
        <div className="form-group" style={{ marginTop: '0.75rem' }}>
          <label className="form-label">New Role</label>
          <select
            id="role-select"
            className="form-input"
            value={newRole}
            onChange={(e) => setNewRole(e.target.value)}
          >
            {ROLE_OPTIONS.map((role) => (
              <option key={role} value={role}>{role}</option>
            ))}
          </select>
        </div>
        <p className="text-muted" style={{ marginTop: '0.5rem', fontSize: '0.8rem' }}>
          Changing a user's role immediately changes what parts of the platform they can access.
        </p>
      </Modal>

      <Modal
        isOpen={!!aiTarget}
        title={`AI Clinical Reports — ${aiTarget?.fullName || aiTarget?.email || ''}`}
        onClose={() => setAiTarget(null)}
      >
        {aiLoading ? (
          <div className="loading-inline"><div className="spinner" /></div>
        ) : aiError ? (
          <p className="text-muted">{aiError}</p>
        ) : aiReports.length === 0 ? (
          <p className="text-muted">No AI symptom-triage reports have been generated for this patient yet.</p>
        ) : (
          <div className="ai-report-list">
            {aiReports.map((report) => (
              <div key={report.id} className="ai-report-item">
                <div className="ai-report-item-header">
                  <span className={`badge ${report.urgencyScore >= 70 ? 'badge--danger' : report.urgencyScore >= 40 ? 'badge--warning' : 'badge--success'}`}>
                    Urgency {report.urgencyScore}
                  </span>
                  <span className="text-muted" style={{ fontSize: '0.75rem' }}>
                    {report.createdAt ? new Date(report.createdAt).toLocaleString() : '—'}
                  </span>
                </div>
                <div style={{ marginTop: '0.5rem' }}>
                  <strong>Suggested Specialty:</strong> {report.suggestedSpecialty || '—'}
                </div>
                <div><strong>Confidence:</strong> {report.confidence != null ? `${Math.round(report.confidence * 100)}%` : '—'}</div>
                <div style={{ marginTop: '0.35rem' }}><strong>Recommendation:</strong> {report.recommendation || '—'}</div>
                {report.riskFactors?.length > 0 && (
                  <div style={{ marginTop: '0.35rem' }}>
                    <strong>Risk Factors:</strong> {report.riskFactors.join(', ')}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </Modal>
    </div>
  )
}
