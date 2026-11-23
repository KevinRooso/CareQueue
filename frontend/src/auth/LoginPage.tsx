import { useMutation } from '@tanstack/react-query'
import { useId, type FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router'
import { isApiError } from '../api/client'
import { Logo } from '../app/Logo'
import { ErrorBanner } from '../components/ErrorBanner'
import ui from '../components/ui.module.css'
import type { LoginLocationState } from './RequireAuth'
import { useAuth } from './useAuth'
import styles from './LoginPage.module.css'

interface Credentials {
  username: string
  password: string
}

export function LoginPage() {
  const { user, expired, login } = useAuth()
  const location = useLocation()
  const from = (location.state as LoginLocationState | null)?.from
  const errorId = useId()

  const loginMutation = useMutation({
    mutationFn: ({ username, password }: Credentials) => login(username, password),
  })

  // A successful login updates the session, this component re-renders, and we leave.
  if (user) return <Navigate to={from ?? '/waiting-list'} replace />

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    loginMutation.mutate({
      username: String(form.get('username') ?? '').trim(),
      password: String(form.get('password') ?? ''),
    })
  }

  const wrongPassword = isApiError(loginMutation.error, 401)

  return (
    <div className={styles.page}>
      <title>Log in – CareQueue</title>
      <main className={styles.frame}>
        <div className={styles.card}>
          <div className={styles.brand}>
            <Logo className={styles.logo} />
            <span className={styles.wordmark}>CareQueue</span>
          </div>
          <h1 className={styles.heading}>Log in</h1>
          <p className={styles.lede}>Referral triage and clinic waiting lists.</p>

          {expired && !loginMutation.error && (
            <p className={styles.notice} role="status">
              Your session has ended. Log in again to continue.
            </p>
          )}

          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={ui.field}>
              <label className={ui.label} htmlFor="login-username">
                Username
              </label>
              <input
                id="login-username"
                name="username"
                className={ui.input}
                autoComplete="username"
                required
                aria-invalid={wrongPassword || undefined}
                aria-describedby={wrongPassword ? errorId : undefined}
              />
            </div>
            <div className={ui.field}>
              <label className={ui.label} htmlFor="login-password">
                Password
              </label>
              <input
                id="login-password"
                name="password"
                type="password"
                className={ui.input}
                autoComplete="current-password"
                required
                aria-invalid={wrongPassword || undefined}
                aria-describedby={wrongPassword ? errorId : undefined}
              />
            </div>

            {wrongPassword && (
              <p id={errorId} className={ui.fieldError} role="alert">
                Wrong username or password. Check both and try again.
              </p>
            )}
            {loginMutation.error && !wrongPassword && <ErrorBanner error={loginMutation.error} />}

            <button type="submit" className={ui.primary} disabled={loginMutation.isPending}>
              {loginMutation.isPending ? 'Logging in…' : 'Log in'}
            </button>
          </form>

          <p className={styles.synthetic}>
            Synthetic data – demo. Demo users: <strong>nurse</strong> (Clinician) and{' '}
            <strong>manager</strong> (Admin); passwords come from the server&rsquo;s environment.
          </p>
        </div>
      </main>
    </div>
  )
}
