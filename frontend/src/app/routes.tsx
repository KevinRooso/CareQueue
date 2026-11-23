import { Navigate, type RouteObject } from 'react-router'
import { LoginPage } from '../auth/LoginPage'
import { RequireAuth } from '../auth/RequireAuth'
import { RequireRole } from '../auth/RequireRole'
import { DeadLettersPage } from '../features/dead-letters/DeadLettersPage'
import { ImportPage } from '../features/import/ImportPage'
import { ReferralDetailPage } from '../features/referral-detail/ReferralDetailPage'
import { ReferralsPage } from '../features/referrals/ReferralsPage'
import { WaitingListPage } from '../features/waiting-list/WaitingListPage'
import { AppShell } from './AppShell'
import { NotFoundPage } from './NotFoundPage'

/** One route table for the browser router (main.tsx) and the memory router (tests). */
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppShell />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/waiting-list" replace /> },
      { path: 'waiting-list', element: <WaitingListPage /> },
      { path: 'referrals', element: <ReferralsPage /> },
      { path: 'referrals/:id', element: <ReferralDetailPage /> },
      {
        path: 'import',
        element: (
          <RequireRole role="ADMIN">
            <ImportPage />
          </RequireRole>
        ),
      },
      {
        path: 'dead-letters',
        element: (
          <RequireRole role="ADMIN">
            <DeadLettersPage />
          </RequireRole>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
