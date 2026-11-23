import { Link } from 'react-router'
import { PageHeader } from '../components/PageHeader'

export function NotFoundPage() {
  return (
    <>
      <PageHeader title="Page not found" />
      <p>
        There is no page at this address. Go to the <Link to="/waiting-list">waiting list</Link>{' '}
        or the <Link to="/referrals">referral inbox</Link>.
      </p>
    </>
  )
}
