import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  GuideApiError,
  GuideAuthenticationRequiredError,
  recommendReleases,
} from '../src/features/guide/guideApi'
import type { ConfirmedRuntime } from '../src/features/guide/guideTypes'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Release recommendation API', () => {
  it('sends only the canonical URL and user-confirmed runtime', async () => {
    const requests: Array<{ input: RequestInfo | URL; init?: RequestInit }> = []
    const responses = [
      jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token' }),
      jsonResponse({
        releases: {
          status: 'AVAILABLE',
          latestStable: null,
          latestPrerelease: null,
          failure: null,
          recommendation: {
            status: 'READY',
            runtime: { operatingSystem: 'WINDOWS', architecture: 'X64' },
            availableLinuxFamilies: [],
          },
        },
        evidence: {},
      }),
    ]
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      requests.push({ input, init })
      return responses.shift() as Response
    }))
    const runtime: ConfirmedRuntime = {
      operatingSystem: 'WINDOWS',
      architecture: 'X64',
    }

    const result = await recommendReleases('https://github.com/octo/example', runtime)

    expect(result.releases.recommendation.status).toBe('READY')
    expect(requests[1]?.input).toBe('/api/guides/releases/recommendation')
    expect(JSON.parse(requests[1]?.init?.body as string)).toEqual({
      canonicalUrl: 'https://github.com/octo/example',
      runtime,
    })
    expect(new Headers(requests[1]?.init?.headers).get('X-CSRF-TOKEN')).toBe('csrf-token')
  })

  it('surfaces an expired Session for the existing authentication recovery flow', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) =>
      input === '/api/auth/csrf'
        ? jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token' })
        : new Response(null, { status: 401 })))

    await expect(recommendReleases('https://github.com/octo/example', {
      operatingSystem: 'LINUX',
      architecture: 'ARM64',
    })).rejects.toBeInstanceOf(GuideAuthenticationRequiredError)
  })

  it('preserves Retry-After from recommendation failures', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) =>
      input === '/api/auth/csrf'
        ? jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token' })
        : jsonResponse(
          { code: 'GITHUB_RATE_LIMITED', retryAfterSeconds: 45 },
          429,
          { 'Retry-After': '90' },
        )))

    await expect(recommendReleases('https://github.com/octo/example', {
      operatingSystem: 'WINDOWS',
      architecture: 'ARM64',
    })).rejects.toMatchObject({
      code: 'GITHUB_RATE_LIMITED',
      status: 429,
      retryAfterSeconds: 90,
    } satisfies Partial<GuideApiError>)
  })
})

function jsonResponse(
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}
