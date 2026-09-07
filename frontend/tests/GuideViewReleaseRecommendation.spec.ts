import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  GuideApiError,
  GuideAuthenticationRequiredError,
  createGuide,
  recommendReleases,
} from '../src/features/guide/guideApi'
import GuideView from '../src/features/guide/GuideView.vue'
import type {
  GuideResponse,
  ReleaseRecommendationResponse,
} from '../src/features/guide/guideTypes'

vi.mock('../src/features/guide/guideApi', async importOriginal => {
  const actual = await importOriginal<typeof import('../src/features/guide/guideApi')>()
  return {
    ...actual,
    createGuide: vi.fn(),
    recommendReleases: vi.fn(),
    retryLanguages: vi.fn(),
    retryReadme: vi.fn(),
    retryReleases: vi.fn(),
  }
})

const mockedCreateGuide = vi.mocked(createGuide)
const mockedRecommendReleases = vi.mocked(recommendReleases)
const mountedWrappers: VueWrapper[] = []

afterEach(() => {
  mountedWrappers.splice(0).forEach(wrapper => wrapper.unmount())
  vi.clearAllMocks()
})

describe('GuideView Release recommendation', () => {
  it('hands an expired Session to authentication recovery and retries the same runtime', async () => {
    const initial = guide('example', 'https://github.com/octo/example')
    mockedCreateGuide.mockResolvedValue(initial)
    mockedRecommendReleases
      .mockRejectedValueOnce(new GuideAuthenticationRequiredError())
      .mockResolvedValueOnce(recommendation(initial, 'WINDOWS', 'X64'))
    const wrapper = mountView()

    await submitGuide(wrapper, initial.repository.canonicalUrl)
    await confirmWindowsX64(wrapper)

    const recovery = wrapper.emitted('authenticationRequired')?.[0]?.[0]
    expect(recovery).toBeTypeOf('function')
    await (recovery as () => Promise<void>)()
    await flushPromises()

    expect(mockedRecommendReleases).toHaveBeenCalledTimes(2)
    expect(mockedRecommendReleases).toHaveBeenLastCalledWith(
      initial.repository.canonicalUrl,
      { operatingSystem: 'WINDOWS', architecture: 'X64' },
    )
    expect(wrapper.text()).toContain('Windows · x64')
  })

  it('ignores a recommendation that arrives after a newer guide request', async () => {
    const first = guide('first', 'https://github.com/octo/first')
    const second = guide('second', 'https://github.com/octo/second')
    mockedCreateGuide.mockResolvedValueOnce(first).mockResolvedValueOnce(second)
    const pending = deferred<ReleaseRecommendationResponse>()
    mockedRecommendReleases.mockReturnValueOnce(pending.promise)
    const wrapper = mountView()

    await submitGuide(wrapper, first.repository.canonicalUrl)
    await confirmWindowsX64(wrapper)
    await submitGuide(wrapper, second.repository.canonicalUrl)
    pending.resolve(recommendation(first, 'WINDOWS', 'X64'))
    await flushPromises()

    expect(wrapper.text()).toContain('second')
    expect(wrapper.text()).not.toContain('Windows · x64')
  })

  it('preserves the current Release region and honors Retry-After after a local failure', async () => {
    const initial = guide('example', 'https://github.com/octo/example')
    initial.releases.latestStable = releaseWithAsset('existing-windows-x64.zip')
    mockedCreateGuide.mockResolvedValue(initial)
    mockedRecommendReleases.mockRejectedValue(new GuideApiError(
      'GitHub 暂时限制了请求，请稍后重试。',
      'GITHUB_RATE_LIMITED',
      429,
      90,
    ))
    const wrapper = mountView()

    await submitGuide(wrapper, initial.repository.canonicalUrl)
    await confirmWindowsX64(wrapper)

    expect(wrapper.text()).toContain('existing-windows-x64.zip')
    expect(wrapper.text()).toContain('GitHub 暂时限制了请求，请稍后重试。')
    expect(wrapper.text()).toContain('约 2 分钟后可重试')
    expect(wrapper.get('[data-testid="confirm-runtime"]').attributes('disabled')).toBeDefined()
  })
})

function mountView(): VueWrapper {
  const wrapper = mount(GuideView, { props: { loggingOut: false } })
  mountedWrappers.push(wrapper)
  return wrapper
}

async function submitGuide(wrapper: VueWrapper, canonicalUrl: string) {
  await wrapper.get('input[name="repositoryUrl"]').setValue(canonicalUrl)
  await wrapper.get('form.guide-form').trigger('submit')
  await flushPromises()
}

async function confirmWindowsX64(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="open-runtime-control"]').trigger('click')
  await flushPromises()
  await wrapper.get('select[name="operatingSystem"]').setValue('WINDOWS')
  await wrapper.get('select[name="architecture"]').setValue('X64')
  await wrapper.get('[data-testid="confirm-runtime"]').trigger('click')
  await flushPromises()
}

function guide(name: string, canonicalUrl: string): GuideResponse {
  return {
    repository: {
      owner: 'octo',
      name,
      description: null,
      canonicalUrl,
      stars: 1,
      createdAt: '2026-01-01T00:00:00Z',
      pushedAt: null,
      projectWebsiteUrl: null,
      evidenceId: 'repository-metadata',
    },
    readme: { status: 'NOT_PROVIDED', candidates: [], truncated: false, failure: null },
    languages: { status: 'NOT_PROVIDED', items: [], failure: null, evidenceId: null },
    releases: {
      status: 'AVAILABLE',
      latestStable: null,
      latestPrerelease: null,
      failure: null,
      recommendation: {
        status: 'NOT_REQUESTED',
        runtime: null,
        availableLinuxFamilies: [],
      },
    },
    evidence: {
      'repository-metadata': { type: 'REPOSITORY', source: 'GitHub', languages: [] },
    },
  }
}

function recommendation(
  original: GuideResponse,
  operatingSystem: 'WINDOWS',
  architecture: 'X64',
): ReleaseRecommendationResponse {
  return {
    releases: {
      ...original.releases,
      recommendation: {
        status: 'READY',
        runtime: { operatingSystem, architecture },
        availableLinuxFamilies: [],
      },
    },
    evidence: {},
  }
}

function releaseWithAsset(assetName: string) {
  return {
    name: 'Existing release',
    tagName: 'v1.0.0',
    publishedAt: '2026-01-01T00:00:00Z',
    assets: [{
      name: assetName,
      sizeBytes: 1024,
      downloadUrl: `https://github.com/octo/example/releases/download/v1.0.0/${assetName}`,
      evidenceId: 'release-asset-1',
      role: 'MANUAL_ARCHIVE' as const,
      assessment: null,
    }],
    matchingAssets: [],
    matchingAssetCount: 0,
    matchingAssetsTruncated: false,
    reportedAssetCount: 1,
    excludedAssetCount: 0,
    assetsTruncated: false,
    warnings: [],
    evidenceId: 'release-1',
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(resolver => {
    resolve = resolver
  })
  return { promise, resolve }
}
