import { Link, NavLink, Outlet } from 'react-router'
import { useAuth, useHasRole } from '../auth/useAuth'
import ui from '../components/ui.module.css'
import { Logo } from './Logo'
import styles from './AppShell.module.css'

interface NavItem {
  to: string
  label: string
  adminOnly?: boolean
}

const NAV_ITEMS: NavItem[] = [
  { to: '/waiting-list', label: 'Waiting list' },
  { to: '/referrals', label: 'Referral inbox' },
  { to: '/import', label: 'Import', adminOnly: true },
  { to: '/dead-letters', label: 'Dead letters', adminOnly: true },
]

/** Masthead + folder-tab navigation + one white sheet that holds the current page. */
export function AppShell() {
  const { user, logout } = useAuth()
  const isAdmin = useHasRole('ADMIN')
  // ADMIN-only items are hidden for clinicians; the routes and the server also refuse them.
  const items = NAV_ITEMS.filter((item) => !item.adminOnly || isAdmin)

  return (
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#main">
        Skip to main content
      </a>
      <header className={styles.masthead}>
        <Link to="/waiting-list" className={styles.brand}>
          <Logo className={styles.logo} />
          <span className={styles.wordmark}>CareQueue</span>
        </Link>
        <p className={styles.synthetic}>Synthetic data – demo</p>
        <div className={styles.user}>
          <p>
            <span className="visually-hidden">Signed in as </span>
            <strong className={styles.username}>{user?.username}</strong>{' '}
            <span className={styles.role}>{isAdmin ? 'Admin' : 'Clinician'}</span>
          </p>
          <button type="button" className={ui.secondary} onClick={logout}>
            Log out
          </button>
        </div>
      </header>

      <nav className={styles.tabs} aria-label="Main">
        <ul>
          {items.map((item) => (
            <li key={item.to}>
              <NavLink to={item.to} className={styles.tab}>
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <main id="main" className={styles.sheet} tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
