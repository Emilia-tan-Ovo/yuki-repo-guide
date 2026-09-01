import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ReleaseSection from '../src/features/guide/components/ReleaseSection.vue'

describe('ReleaseSection', () => {
  it('shows stable and prerelease downloads with traceable evidence and a safety warning', () => {
    const wrapper = mount(ReleaseSection, {
      props: {
        releases: {
          status: 'AVAILABLE',
          latestStable: {
            name: 'Yuki 2.0',
            tagName: 'v2.0.0',
            publishedAt: '2026-08-31T12:00:00Z',
            assets: [{
              name: 'yuki.zip',
              sizeBytes: 2048,
              downloadUrl: 'https://github.com/octo/example/releases/download/v2.0.0/yuki.zip',
              evidenceId: 'github-release-asset-51',
            }],
            reportedAssetCount: 1,
            excludedAssetCount: 0,
            assetsTruncated: false,
            warnings: [],
            evidenceId: 'github-release-41',
          },
          latestPrerelease: {
            name: null,
            tagName: 'v2.1.0-beta',
            publishedAt: '2026-09-01T12:00:00Z',
            assets: [],
            reportedAssetCount: 0,
            excludedAssetCount: 0,
            assetsTruncated: false,
            warnings: ['PRERELEASE'],
            evidenceId: 'github-release-42',
          },
          failure: null,
        },
        evidence: {
          'github-release-41': {
            type: 'RELEASE', source: 'GitHub Releases REST API', languages: [],
            releaseId: 41,
            releaseUrl: 'https://github.com/octo/example/releases/tag/v2.0.0',
          },
          'github-release-asset-51': {
            type: 'RELEASE_ASSET', source: 'GitHub Releases REST API', languages: [],
            releaseEvidenceId: 'github-release-41', assetId: 51, sizeBytes: 2048,
          },
          'github-release-42': {
            type: 'RELEASE', source: 'GitHub Releases REST API', languages: [],
            releaseId: 42,
          },
        },
        retrying: false,
        retryDisabled: false,
        retryMessage: '',
        errorMessage: '',
      },
    })

    expect(wrapper.text()).toContain('最新正式版')
    expect(wrapper.text()).toContain('Yuki 2.0')
    expect(wrapper.text()).toContain('最新预览版')
    expect(wrapper.text()).toContain('v2.1.0-beta')
    expect(wrapper.text()).toContain('2.0 KB')
    expect(wrapper.text()).toContain('未经 RepoGuide 安全认证')
    expect(wrapper.text()).toContain('GitHub Asset ID：51')
    expect(wrapper.get('a[href*="yuki.zip"]').attributes()).toMatchObject({
      target: '_blank',
      rel: 'noopener noreferrer',
    })
  })

  it('retries only retryable failures', async () => {
    const wrapper = mount(ReleaseSection, {
      props: {
        releases: {
          status: 'FAILED',
          latestStable: null,
          latestPrerelease: null,
          failure: { code: 'GITHUB_TIMEOUT', retryable: true, retryAfterSeconds: null },
        },
        evidence: {},
        retrying: false,
        retryDisabled: false,
        retryMessage: '请点击重试',
        errorMessage: '连接 GitHub 超时，请稍后重试。',
      },
    })

    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)

    await wrapper.setProps({
      releases: {
        status: 'FAILED',
        latestStable: null,
        latestPrerelease: null,
        failure: {
          code: 'RELEASE_HISTORY_UNSUPPORTED',
          retryable: false,
          retryAfterSeconds: null,
        },
      },
    })
    expect(wrapper.find('button').exists()).toBe(false)
  })
})
