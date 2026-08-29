import { afterEach, describe, expect, it, vi } from 'vitest'
import { GuideApiError, retryReadme } from '../src/features/guide/guideApi.ts'
import { applyReadmeRetry } from '../src/features/guide/readmeRetry.ts'
import type { GuideResponse, ReadmeRetryResponse } from '../src/features/guide/guideTypes.ts'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('README retry', () => {
  it('preserves the non-retryable 422 contract without retrying automatically', async () => {
    const requests: Array<{ input: RequestInfo | URL; init?: RequestInit }> = []
    const responses = [
      jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token' }),
      jsonResponse(
        {
          title: 'README 内容不受支持',
          status: 422,
          detail: 'README 内容格式暂不受支持，无法识别在线体验入口。',
          code: 'README_CONTENT_UNSUPPORTED',
          retryable: false,
        },
        422,
      ),
    ]
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      requests.push({ input, init })
      return responses.shift() as Response
    }))

    await expect(retryReadme('https://github.com/octo/example')).rejects.toMatchObject({
      name: 'GuideApiError',
      code: 'README_CONTENT_UNSUPPORTED',
      status: 422,
    } satisfies Partial<GuideApiError>)

    expect(requests).toHaveLength(2)
    expect(requests[1]?.input).toBe('/api/guides/readme/retry')
  })

  it('replaces only the README region and its evidence', () => {
    const repository = {
      owner: 'octo',
      name: 'example',
      description: null,
      canonicalUrl: 'https://github.com/octo/example',
      stars: 1,
      createdAt: '2026-01-01T00:00:00Z',
      pushedAt: null,
      projectWebsiteUrl: null,
      evidenceId: 'repository-metadata',
    }
    const languages = { status: 'AVAILABLE' as const, items: [], evidenceId: null }
    const guide = {
      repository,
      readme: {
        status: 'AVAILABLE',
        candidates: [{
          label: 'Old demo',
          evidenceId: 'old-readme',
          url: 'https://old.example.com',
          warnings: [],
        }],
        truncated: false,
        failure: null,
      },
      languages,
      evidence: {
        'repository-metadata': { type: 'REPOSITORY', source: 'GitHub', languages: [] },
        'repository-languages': {
          type: 'LANGUAGES',
          source: 'GitHub Languages REST API',
          languages: [],
        },
        'old-readme': { type: 'README', source: 'GitHub README', languages: [] },
      },
    } satisfies GuideResponse
    const retried = {
      readme: {
        status: 'AVAILABLE',
        candidates: [{
          label: 'New demo',
          evidenceId: 'new-readme',
          url: 'https://new.example.com',
          warnings: [],
        }],
        truncated: false,
        failure: null,
      },
      evidence: {
        'new-readme': { type: 'README', source: 'GitHub README', languages: [] },
      },
    } satisfies ReadmeRetryResponse

    const updated = applyReadmeRetry(guide, retried)

    expect(updated.repository).toBe(repository)
    expect(updated.languages).toBe(languages)
    expect(updated.readme).toBe(retried.readme)
    expect(Object.keys(updated.evidence).sort()).toEqual([
      'new-readme',
      'repository-languages',
      'repository-metadata',
    ])
  })
})

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
