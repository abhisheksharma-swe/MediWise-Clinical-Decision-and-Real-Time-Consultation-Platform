import { useState } from 'react'
import { getAppointmentAnalytics, getRevenueAnalytics } from '../services/analyticsService'
import StatCard from '../components/common/StatCard'
import DataTable from '../components/common/DataTable'
import ToastContainer, { useToast } from '../components/common/Toast'
import { CalendarIcon, CheckCircleIcon, XCircleIcon, ClockIcon, DollarSignIcon } from '../components/common/Icons'

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function daysAgoIso(n) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d.toISOString().slice(0, 10)
}

function Breakdown({ title, data }) {
  const entries = Object.entries(data || {})
  const max = Math.max(1, ...entries.map(([, v]) => v))
  return (
    <div className="card breakdown-card">
      <div className="breakdown-card-title">{title}</div>
      {entries.length === 0 ? (
        <p className="text-muted" style={{ padding: '0 1.25rem 1.25rem' }}>No data for this range.</p>
      ) : (
        <div className="breakdown-list">
          {entries.map(([label, value]) => (
            <div className="breakdown-row" key={label}>
              <span className="breakdown-label">{label}</span>
              <div className="breakdown-bar-track">
                <div className="breakdown-bar-fill" style={{ width: `${(value / max) * 100}%` }} />
              </div>
              <span className="breakdown-value">{value}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default function AnalyticsDetail() {
  const [from, setFrom]         = useState(daysAgoIso(30))
  const [to, setTo]             = useState(todayIso())
  const [doctorId, setDoctorId] = useState('')
  const [appointmentData, setAppointmentData] = useState(null)
  const [revenueData, setRevenueData]         = useState(null)
  const [loadingAppt, setLoadingAppt]         = useState(false)
  const [loadingRevenue, setLoadingRevenue]   = useState(false)
  const { toasts, show: showToast, dismiss } = useToast()

  async function runAppointmentAnalytics() {
    if (!from || !to) {
      showToast('Please select both from and to dates.', 'warning')
      return
    }
    setLoadingAppt(true)
    try {
      const res = await getAppointmentAnalytics({ from, to, doctorId: doctorId || undefined })
      setAppointmentData(res?.data || null)
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to load appointment analytics.', 'error')
    } finally {
      setLoadingAppt(false)
    }
  }

  async function runRevenueAnalytics() {
    if (!from || !to) {
      showToast('Please select both from and to dates.', 'warning')
      return
    }
    setLoadingRevenue(true)
    try {
      const res = await getRevenueAnalytics({ from, to })
      setRevenueData(res?.data || null)
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to load revenue analytics.', 'error')
    } finally {
      setLoadingRevenue(false)
    }
  }

  const doctorColumns = [
    { key: 'doctorName', label: 'Doctor', render: (r) => r.doctorName || `#${r.doctorId?.slice(0, 6)}` },
    { key: 'appointmentCount', label: 'Appointments' },
    { key: 'revenue', label: 'Revenue', render: (r) => r.revenue != null ? `₹${Number(r.revenue).toLocaleString()}` : '—' },
    { key: 'averageRating', label: 'Avg Rating', render: (r) => r.averageRating != null ? Number(r.averageRating).toFixed(1) : '—' },
  ]

  return (
    <div className="page">
      <ToastContainer toasts={toasts} dismiss={dismiss} />

      <div className="page-header">
        <div>
          <h1 className="page-title">Analytics</h1>
          <p className="page-subtitle">Date-range appointment and revenue analytics across the platform</p>
        </div>
      </div>

      <div className="card" style={{ padding: '1.25rem' }}>
        <div className="filter-bar">
          <div className="form-group">
            <label className="form-label" htmlFor="analytics-from">From</label>
            <input id="analytics-from" type="date" className="form-input" value={from} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="analytics-to">To</label>
            <input id="analytics-to" type="date" className="form-input" value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
          <div className="form-group" style={{ flex: 1, minWidth: '220px' }}>
            <label className="form-label" htmlFor="analytics-doctor-id">Doctor ID (Optional, appointment analytics only)</label>
            <input
              id="analytics-doctor-id"
              type="text"
              className="form-input"
              placeholder="Filter appointment analytics by doctor UUID"
              value={doctorId}
              onChange={(e) => setDoctorId(e.target.value)}
            />
          </div>
          <div style={{ display: 'flex', gap: '0.5rem', alignSelf: 'flex-end' }}>
            <button className="btn btn-primary" onClick={runAppointmentAnalytics} disabled={loadingAppt} id="run-appointment-analytics">
              {loadingAppt ? 'Running...' : 'Run Appointment Analytics'}
            </button>
            <button className="btn btn-outline" onClick={runRevenueAnalytics} disabled={loadingRevenue} id="run-revenue-analytics">
              {loadingRevenue ? 'Running...' : 'Run Revenue Analytics'}
            </button>
          </div>
        </div>
      </div>

      {appointmentData && (
        <>
          <div className="section-divider">
            <h3 className="section-title">Appointment Analytics ({appointmentData.from} → {appointmentData.to})</h3>
          </div>
          <div className="stats-grid stats-grid--4">
            <StatCard title="Total Appointments" value={appointmentData.totalCount ?? 0} icon={<CalendarIcon size={20} />} color="blue" />
            <StatCard title="Completed" value={appointmentData.completedCount ?? 0} icon={<CheckCircleIcon size={20} />} color="teal" />
            <StatCard title="Cancelled" value={appointmentData.cancelledCount ?? 0} icon={<XCircleIcon size={20} />} color="red" />
            <StatCard title="No-Shows" value={appointmentData.noShowCount ?? 0} icon={<ClockIcon size={20} />} color="orange" />
          </div>
          <div className="stats-grid">
            <StatCard
              title="Total Revenue"
              value={appointmentData.totalRevenue != null ? `₹${Number(appointmentData.totalRevenue).toLocaleString()}` : '₹0'}
              icon={<DollarSignIcon size={20} />}
              color="gold"
            />
            <StatCard
              title="Avg. Consultation Fee"
              value={appointmentData.averageConsultationFee != null ? `₹${Number(appointmentData.averageConsultationFee).toLocaleString()}` : '—'}
              icon={<DollarSignIcon size={20} />}
              color="purple"
            />
          </div>

          <div className="breakdown-grid">
            <Breakdown title="Daily Counts" data={appointmentData.dailyCounts} />
            <Breakdown title="By Specialty" data={appointmentData.bySpecialty} />
            <Breakdown title="By Type" data={appointmentData.byType} />
          </div>

          <div className="section-divider"><h3 className="section-title">Top Doctors</h3></div>
          <div className="card">
            <DataTable columns={doctorColumns} data={appointmentData.topDoctors || []} loading={false} emptyMessage="No doctor activity in this range." />
          </div>
        </>
      )}

      {revenueData && (
        <>
          <div className="section-divider">
            <h3 className="section-title">Revenue Analytics ({revenueData.from} → {revenueData.to})</h3>
          </div>
          <div className="stats-grid">
            <StatCard
              title="Total Revenue"
              value={revenueData.totalRevenue != null ? `₹${Number(revenueData.totalRevenue).toLocaleString()}` : '₹0'}
              icon={<DollarSignIcon size={20} />}
              color="gold"
            />
            <StatCard
              title="Avg. Consultation Fee"
              value={revenueData.averageConsultationFee != null ? `₹${Number(revenueData.averageConsultationFee).toLocaleString()}` : '—'}
              icon={<DollarSignIcon size={20} />}
              color="purple"
            />
            <StatCard title="Total Appointments" value={revenueData.totalCount ?? 0} icon={<CalendarIcon size={20} />} color="blue" />
          </div>
          <div className="card">
            <DataTable columns={doctorColumns} data={revenueData.topDoctors || []} loading={false} emptyMessage="No doctor revenue in this range." />
          </div>
        </>
      )}
    </div>
  )
}
