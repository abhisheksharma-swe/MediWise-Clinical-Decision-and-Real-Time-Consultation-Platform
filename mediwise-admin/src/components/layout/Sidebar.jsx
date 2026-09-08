import { NavLink } from 'react-router-dom'
import { ROUTES } from '../../utils/constants'
import {
  HospitalIcon,
  ActivityIcon,
  DoctorIcon,
  ShieldCheckIcon,
  UsersIcon,
  CalendarIcon,
  LinkIcon,
  FileTextIcon,
  BarChartIcon,
  BellIcon,
} from '../common/Icons'

const NAV_ITEMS = [
  { label: 'Dashboard', path: ROUTES.DASHBOARD, Icon: ActivityIcon },
  { label: 'Doctors', path: ROUTES.DOCTORS, Icon: DoctorIcon },
  { label: 'Verification', path: ROUTES.DOCTOR_VERIFICATION, Icon: ShieldCheckIcon },
  { label: 'Patients', path: ROUTES.PATIENTS, Icon: UsersIcon },
  { label: 'Appointments', path: ROUTES.APPOINTMENTS, Icon: CalendarIcon },
  { label: 'Assign Doctor', path: ROUTES.ASSIGN_DOCTOR, Icon: LinkIcon },
  { label: 'Analytics', path: ROUTES.ANALYTICS, Icon: BarChartIcon },
  { label: 'Notifications', path: ROUTES.NOTIFICATIONS, Icon: BellIcon },
  { label: 'Audit Logs', path: ROUTES.AUDIT_LOGS, Icon: FileTextIcon },
]

export default function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <div className="sidebar-brand-icon">
          <HospitalIcon size={24} />
        </div>
        <div>
          <div className="sidebar-brand-name">MediWise</div>
          <div className="sidebar-brand-sub">Clinical Administration</div>
        </div>
      </div>

      <nav className="sidebar-nav">
        {NAV_ITEMS.map((item) => {
          const { Icon } = item
          return (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.path === '/'}
              className={({ isActive }) =>
                `sidebar-nav-item${isActive ? ' sidebar-nav-item--active' : ''}`
              }
            >
              <span className="sidebar-nav-icon">
                <Icon size={18} />
              </span>
              <span className="sidebar-nav-label">{item.label}</span>
            </NavLink>
          )
        })}
      </nav>

      <div className="sidebar-footer">
        <span className="sidebar-footer-text">MediWise v1.0.0</span>
      </div>
    </aside>
  )
}
