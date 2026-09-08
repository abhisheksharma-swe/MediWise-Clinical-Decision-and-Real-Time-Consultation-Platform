import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import AdminLayout from '../components/layout/AdminLayout'
import Login from '../pages/Login'
import Dashboard from '../pages/Dashboard'
import Doctors from '../pages/Doctors'
import DoctorVerification from '../pages/DoctorVerification'
import Patients from '../pages/Patients'
import Appointments from '../pages/Appointments'
import AssignDoctor from '../pages/AssignDoctor'
import AuditLogs from '../pages/AuditLogs'
import AnalyticsDetail from '../pages/AnalyticsDetail'
import Notifications from '../pages/Notifications'
import Profile from '../pages/Profile'
import Chat from '../pages/Chat'
import LoadingSpinner from '../components/common/LoadingSpinner'

function ProtectedRoute({ children }) {
  const { isAuthenticated, loading } = useAuth()
  if (loading) return <LoadingSpinner fullScreen />
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return children
}

function PublicRoute({ children }) {
  const { isAuthenticated, loading } = useAuth()
  if (loading) return <LoadingSpinner fullScreen />
  if (isAuthenticated) return <Navigate to="/" replace />
  return children
}

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<PublicRoute><Login /></PublicRoute>} />

      <Route path="/" element={<ProtectedRoute><AdminLayout /></ProtectedRoute>}>
        <Route index element={<Dashboard />} />
        <Route path="doctors" element={<Doctors />} />
        <Route path="doctors/verification" element={<DoctorVerification />} />
        <Route path="patients" element={<Patients />} />
        <Route path="appointments" element={<Appointments />} />
        <Route path="assign-doctor" element={<AssignDoctor />} />
        <Route path="audit-logs" element={<AuditLogs />} />
        <Route path="analytics" element={<AnalyticsDetail />} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="profile" element={<Profile />} />
        <Route path="chat/:roomId" element={<Chat />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
