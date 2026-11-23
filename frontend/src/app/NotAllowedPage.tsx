import { Link } from 'react-router'
import { PageHeader } from '../components/PageHeader'

/** Shown when a signed-in user opens a page their role cannot use. */
export function NotAllowedPage() {
  return (
    <>
      <PageHeader title="Not allowed" />
      <p>
        This page is for clinic managers (Admin role). Your account can triage referrals and
        view waiting lists. Go back to the <Link to="/waiting-list">waiting list</Link>.
      </p>
    </>
  )
}
