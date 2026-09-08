export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

export const DOCTOR_STATUSES = {
  PENDING: 'PENDING',
  VERIFIED: 'VERIFIED',
  REJECTED: 'REJECTED',
  SUSPENDED: 'SUSPENDED',
}

export const APPOINTMENT_STATUSES = {
  PENDING: 'PENDING',
  CONFIRMED: 'CONFIRMED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
}

export const USER_ROLES = {
  ADMIN: 'ADMIN',
  DOCTOR: 'DOCTOR',
  PATIENT: 'PATIENT',
}

export const ROUTES = {
  LOGIN: '/login',
  DASHBOARD: '/',
  DOCTORS: '/doctors',
  DOCTOR_VERIFICATION: '/doctors/verification',
  PATIENTS: '/patients',
  APPOINTMENTS: '/appointments',
  ASSIGN_DOCTOR: '/assign-doctor',
  AUDIT_LOGS: '/audit-logs',
  ANALYTICS: '/analytics',
  NOTIFICATIONS: '/notifications',
  PROFILE: '/profile',
  CHAT: '/chat/:roomId',
}
