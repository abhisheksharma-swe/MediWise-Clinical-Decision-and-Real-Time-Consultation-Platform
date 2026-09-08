import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getAppointments, cancelAppointment, startAppointment, completeAppointment } from '../services/appointmentService'
import DataTable from '../components/common/DataTable'
import Badge from '../components/common/Badge'
import Modal from '../components/common/Modal'
import ToastContainer, { useToast } from '../components/common/Toast'
import ErrorState from '../components/common/ErrorState'
import { APPOINTMENT_STATUSES } from '../utils/constants'
import { XCircleIcon, ZapIcon, CheckCircleIcon, MessageCircleIcon } from '../components/common/Icons'

const STATUS_OPTIONS = ['', ...Object.values(APPOINTMENT_STATUSES)]

export default function Appointments() {
  const [appointments, setAppointments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState('')
  const [status, setStatus]   = useState('')
  const navigate = useNavigate()

  const [selected, setSelected]     = useState(null)
  const [modalType, setModalType]   = useState(null) // 'cancel' | 'complete'
  const [cancelReason, setCancelReason] = useState('')
  const [completeNotes, setCompleteNotes] = useState('')
  const [completeDiagnosis, setCompleteDiagnosis] = useState('')
  const [completePrescription, setCompletePrescription] = useState('')
  const [actionLoading, setActionLoading] = useState(false)
  const [startingId, setStartingId] = useState(null)
  const { toasts, show: showToast, dismiss } = useToast()

  async function fetchAppointments() {
    setLoading(true)
    setError('')
    try {
      const params = { size: 50 }
      if (status) params.status = status
      const res = await getAppointments(params)
      setAppointments(res?.data?.content || [])
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to load appointments.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchAppointments() }, [status])

  function openCancelModal(appt) {
    setSelected(appt)
    setModalType('cancel')
    setCancelReason('')
  }

  function openCompleteModal(appt) {
    setSelected(appt)
    setModalType('complete')
    setCompleteNotes('')
    setCompleteDiagnosis('')
    setCompletePrescription('')
  }

  function closeModal() {
    setSelected(null)
    setModalType(null)
  }

  async function handleCancelConfirm() {
    if (!selected) return
    setActionLoading(true)
    try {
      await cancelAppointment(selected.id, cancelReason || 'Cancelled by administrator')
      showToast('Appointment cancelled successfully.', 'success')
      closeModal()
      fetchAppointments()
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to cancel appointment.', 'error')
    } finally {
      setActionLoading(false)
    }
  }

  async function handleCompleteConfirm() {
    if (!selected) return
    if (!completeNotes.trim()) {
      showToast('Clinical notes are required to complete an appointment.', 'warning')
      return
    }
    setActionLoading(true)
    try {
      await completeAppointment(selected.id, {
        notes: completeNotes,
        diagnosis: completeDiagnosis,
        prescription: completePrescription,
      })
      showToast('Appointment marked as completed.', 'success')
      closeModal()
      fetchAppointments()
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to complete appointment.', 'error')
    } finally {
      setActionLoading(false)
    }
  }

  async function handleStart(appt) {
    setStartingId(appt.id)
    try {
      await startAppointment(appt.id)
      showToast('Consultation started.', 'success')
      fetchAppointments()
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to start consultation.', 'error')
    } finally {
      setStartingId(null)
    }
  }

  function openChat(appt) {
    navigate(`/chat/appointment_${appt.id}`)
  }

  const columns = [
    { key: 'id',            label: 'Reference ID', render: (r) => <code style={{ fontSize: '0.75rem', background: '#f8fafc', padding: '2px 6px', borderRadius: '4px' }}>{r.id?.slice(0, 8)}...</code> },
    { key: 'patientName',   label: 'Patient', render: (r) => <span style={{ fontWeight: 600 }}>{r.patientName || (r.patientId ? `Patient #${r.patientId.slice(0, 6)}` : '—')}</span> },
    { key: 'doctorName',    label: 'Assigned Doctor', render: (r) => r.doctorName ? `Dr. ${r.doctorName}` : (r.doctorId ? `Dr. #${r.doctorId.slice(0, 6)}` : 'Unassigned') },
    { key: 'scheduledDate', label: 'Consultation Date', render: (r) => r.scheduledDate || r.slotDate || '—' },
    { key: 'scheduledTime', label: 'Time Slot', render: (r) => r.scheduledTime || r.slotStartTime || '—' },
    {
      key: 'type',
      label: 'Channel',
      render: (r) => (
        <span className="badge badge--blue-light">
          {r.type || 'VIDEO_CALL'}
        </span>
      ),
    },
    { key: 'status',        label: 'Status', render: (r) => <Badge status={r.status} /> },
    {
      key: 'actions',
      label: 'Actions',
      render: (r) => (
        <div className="action-btns">
          {r.status === 'CONFIRMED' && (
            <button
              className="btn btn-primary btn-sm"
              onClick={() => handleStart(r)}
              disabled={startingId === r.id}
              id={`start-appt-${r.id}`}
            >
              <ZapIcon size={14} />
              <span>{startingId === r.id ? 'Starting...' : 'Start'}</span>
            </button>
          )}
          {r.status === 'IN_PROGRESS' && (
            <button
              className="btn btn-success btn-sm"
              onClick={() => openCompleteModal(r)}
              id={`complete-appt-${r.id}`}
            >
              <CheckCircleIcon size={14} />
              <span>Complete</span>
            </button>
          )}
          {['PENDING', 'CONFIRMED', 'IN_PROGRESS'].includes(r.status) && (
            <button
              className="btn btn-danger btn-sm"
              onClick={() => openCancelModal(r)}
              id={`cancel-appt-${r.id}`}
            >
              <XCircleIcon size={14} />
              <span>Cancel</span>
            </button>
          )}
          <button
            className="btn btn-outline btn-sm"
            onClick={() => openChat(r)}
            id={`view-chat-${r.id}`}
          >
            <MessageCircleIcon size={14} />
            <span>Chat</span>
          </button>
        </div>
      ),
    },
  ]

  if (error) return <ErrorState message={error} onRetry={fetchAppointments} />

  return (
    <div className="page">
      <ToastContainer toasts={toasts} dismiss={dismiss} />

      <div className="page-header">
        <div>
          <h1 className="page-title">Appointment Oversight</h1>
          <p className="page-subtitle">Monitor patient consultation bookings, schedules, and fulfillment statuses across the platform</p>
        </div>
        <button className="btn btn-outline btn-sm" onClick={fetchAppointments}>
          Refresh Appointments
        </button>
      </div>

      <div className="filter-bar">
        <select
          id="appointment-status-filter"
          className="form-input"
          style={{ maxWidth: '240px' }}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
        >
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>{s ? `Status: ${s}` : 'All Consultation Statuses'}</option>
          ))}
        </select>
      </div>

      <div className="card">
        <DataTable
          columns={columns}
          data={appointments}
          loading={loading}
          emptyMessage="No consultation bookings found for the selected filter."
        />
      </div>

      <Modal
        isOpen={modalType === 'cancel'}
        title="Cancel Appointment"
        onClose={closeModal}
        onConfirm={handleCancelConfirm}
        confirmText="Confirm Cancellation"
        confirmVariant="danger"
        loading={actionLoading}
      >
        <p>Are you sure you want to cancel this appointment?</p>
        <div className="form-group" style={{ marginTop: '0.75rem' }}>
          <label className="form-label">Cancellation Reason (Optional)</label>
          <textarea
            className="form-input"
            rows="3"
            placeholder="e.g. Patient requested reschedule..."
            value={cancelReason}
            onChange={(e) => setCancelReason(e.target.value)}
          />
        </div>
      </Modal>

      <Modal
        isOpen={modalType === 'complete'}
        title="Complete Consultation"
        onClose={closeModal}
        onConfirm={handleCompleteConfirm}
        confirmText="Mark Completed"
        confirmVariant="success"
        loading={actionLoading}
      >
        <div className="form-group">
          <label className="form-label">Clinical Notes (Required)</label>
          <textarea
            className="form-input"
            rows="3"
            placeholder="Summary of the consultation..."
            value={completeNotes}
            onChange={(e) => setCompleteNotes(e.target.value)}
            required
          />
        </div>
        <div className="form-group" style={{ marginTop: '0.75rem' }}>
          <label className="form-label">Diagnosis (Optional)</label>
          <textarea
            className="form-input"
            rows="2"
            value={completeDiagnosis}
            onChange={(e) => setCompleteDiagnosis(e.target.value)}
          />
        </div>
        <div className="form-group" style={{ marginTop: '0.75rem' }}>
          <label className="form-label">Prescription (Optional)</label>
          <textarea
            className="form-input"
            rows="2"
            value={completePrescription}
            onChange={(e) => setCompletePrescription(e.target.value)}
          />
        </div>
      </Modal>
    </div>
  )
}
