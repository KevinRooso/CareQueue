import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { basicAuthorization, sessionStore } from '../auth/sessionStore'
import { api as fakeApi, problem } from '../test/handlers'
import { server } from '../test/server'
import { api, ApiError } from './client'

const nurse = { username: 'nurse', roles: ['CLINICIAN'] }

describe('api client', () => {
  it('sends the stored Basic credentials', async () => {
    let authorization: string | null = null
    server.use(
      http.get(fakeApi('/clinics'), ({ request }) => {
        authorization = request.headers.get('Authorization')
        return HttpResponse.json([])
      }),
    )
    sessionStore.start({ user: nurse, authorization: basicAuthorization('nurse', 'nurse-test-only') })

    await api.getClinics()

    expect(authorization).toBe(`Basic ${btoa('nurse:nurse-test-only')}`)
  })

  it('turns problem+json into an ApiError with field errors and the dead letter id', async () => {
    server.use(
      http.post(fakeApi('/referrals/import'), () =>
        problem(422, 'Invalid referral message', 'Schema validation failed', {
          errors: [{ field: 'clinicCode', message: 'must not be blank' }],
          deadLetterId: 12,
        }),
      ),
    )

    const error = await api.importReferral('{}', 'application/json').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 422,
      title: 'Invalid referral message',
      detail: 'Schema validation failed',
      deadLetterId: 12,
    })
    expect((error as ApiError).fieldError('clinicCode')).toBe('must not be blank')
  })

  it('falls back to a generic title when the error body is not JSON', async () => {
    server.use(
      http.get(fakeApi('/clinics'), () => new HttpResponse('upstream down', { status: 502 })),
    )

    const error = await api.getClinics().catch((e: unknown) => e)

    expect(error).toMatchObject({ status: 502, errors: [] })
    expect((error as ApiError).title).toMatch(/502|Bad Gateway/)
  })

  it('ends the session on a 401 to a request that carried stored credentials', async () => {
    sessionStore.start({ user: nurse, authorization: basicAuthorization('nurse', 'wrong') })

    await expect(api.getClinics()).rejects.toMatchObject({ status: 401 })

    expect(sessionStore.getState()).toEqual({ session: null, expired: true })
  })

  it('does not touch the session when a login attempt is rejected', async () => {
    const error = await api
      .verifyCredentials(basicAuthorization('nurse', 'wrong'))
      .catch((e: unknown) => e)

    expect(error).toMatchObject({ status: 401 })
    expect(sessionStore.getState()).toEqual({ session: null, expired: false })
  })
})
