import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReleaseRecommendationPanel from '../src/features/guide/components/ReleaseRecommendationPanel.vue'
import { suggestRuntimeEnvironment } from '../src/features/guide/runtimeSuggestion'
import type { ReleaseSection } from '../src/features/guide/guideTypes'

vi.mock('../src/features/guide/runtimeSuggestion', () => ({
  suggestRuntimeEnvironment: vi.fn(),
}))

const mockedSuggestion = vi.mocked(suggestRuntimeEnvironment)

beforeEach(() => {
  mockedSuggestion.mockReset()
})

describe('ReleaseRecommendationPanel', () => {
  it('loads a browser suggestion only after opening and waits for explicit confirmation', async () => {
    mockedSuggestion.mockResolvedValue({
      operatingSystem: 'WINDOWS',
      architecture: 'X64',
      source: 'USER_AGENT_CLIENT_HINTS',
    })
    const wrapper = mount(ReleaseRecommendationPanel, {
      props: defaultProps(notRequested()),
    })

    expect(mockedSuggestion).not.toHaveBeenCalled()
    await wrapper.get('[data-testid="open-runtime-control"]').trigger('click')
    await flushPromises()

    expect(mockedSuggestion).toHaveBeenCalledOnce()
    expect((wrapper.get('select[name="operatingSystem"]').element as HTMLSelectElement).value)
      .toBe('WINDOWS')
    expect((wrapper.get('select[name="architecture"]').element as HTMLSelectElement).value)
      .toBe('X64')
    expect(wrapper.emitted('confirm')).toBeUndefined()

    await wrapper.get('[data-testid="confirm-runtime"]').trigger('click')
    expect(wrapper.emitted('confirm')).toEqual([[
      { operatingSystem: 'WINDOWS', architecture: 'X64' },
    ]])
  })

  it('asks only for a useful Linux package family and explains the choices', async () => {
    const releases = notRequested()
    releases.recommendation = {
      status: 'NEEDS_LINUX_FAMILY',
      runtime: { operatingSystem: 'LINUX', architecture: 'ARM64' },
      availableLinuxFamilies: ['DEB', 'ARCH', 'OTHER_OR_UNKNOWN'],
    }
    const wrapper = mount(ReleaseRecommendationPanel, {
      props: defaultProps(releases),
    })

    expect(wrapper.text()).toContain('还需要确认 Linux 资源族')
    expect(wrapper.text()).toContain('Debian、Ubuntu、Linux Mint')
    expect(wrapper.text()).toContain('Arch Linux、Manjaro')
    await wrapper.get('select[name="linuxPackageFamily"]').setValue('DEB')
    await wrapper.get('[data-testid="confirm-linux-family"]').trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([[
      {
        operatingSystem: 'LINUX',
        architecture: 'ARM64',
        linuxPackageFamily: 'DEB',
      },
    ]])
  })

  it('separates direct stable recommendations from warned prerelease alternatives', () => {
    const releases = readyWithMatches()
    const wrapper = mount(ReleaseRecommendationPanel, {
      props: {
        ...defaultProps(releases),
        evidence: {
          'asset-stable': {
            type: 'RELEASE_ASSET',
            source: 'GitHub Releases REST API',
            languages: [],
            assetId: 51,
          },
        },
      },
    })

    expect(wrapper.text()).toContain('直接推荐')
    expect(wrapper.text()).toContain('yuki-windows-x64-setup.exe')
    expect(wrapper.text()).toContain('预览版备选')
    expect(wrapper.text()).toContain('yuki-windows-x64-portable.exe')
    expect(wrapper.text()).toContain('该区域的资源可能不是“最新版”')
    expect(wrapper.text()).toContain('匹配不保证实际兼容')
    expect(wrapper.text()).toContain('不会自动下载或执行')
    expect(wrapper.text()).toContain('GitHub Asset ID：51')
    expect(wrapper.text()).toContain('文件名分析：标准安装资源')
    expect(wrapper.text()).toContain('用户确认环境：Windows · x64')
  })
})

function defaultProps(releases: ReleaseSection) {
  return {
    releases,
    evidence: {},
    busy: false,
    disabled: false,
    retryMessage: '',
    errorMessage: '',
  }
}

function notRequested(): ReleaseSection {
  return {
    status: 'AVAILABLE',
    latestStable: null,
    latestPrerelease: null,
    failure: null,
    recommendation: {
      status: 'NOT_REQUESTED',
      runtime: null,
      availableLinuxFamilies: [],
    },
  }
}

function readyWithMatches(): ReleaseSection {
  return {
    ...notRequested(),
    recommendation: {
      status: 'READY',
      runtime: { operatingSystem: 'WINDOWS', architecture: 'X64' },
      availableLinuxFamilies: [],
    },
    latestStable: release('stable', 'yuki-windows-x64-setup.exe', true),
    latestPrerelease: release('preview', 'yuki-windows-x64-portable.exe', false),
  }
}

function release(kind: string, name: string, directlyRecommended: boolean) {
  const asset = {
    name,
    sizeBytes: 2048,
    downloadUrl: `https://github.com/octo/example/releases/download/${kind}/${name}`,
    evidenceId: `asset-${kind}`,
    role: directlyRecommended ? 'STANDARD_INSTALLER' as const : 'PORTABLE' as const,
    assessment: {
      matchStatus: 'MATCHED' as const,
      directlyRecommended,
      detectedOperatingSystem: 'WINDOWS' as const,
      detectedArchitecture: 'X64' as const,
      detectedLinuxPackageFamily: null,
    },
  }
  return {
    name: kind,
    tagName: kind,
    publishedAt: '2026-09-01T00:00:00Z',
    assets: [asset],
    matchingAssets: [asset],
    matchingAssetCount: 1,
    matchingAssetsTruncated: false,
    reportedAssetCount: 1,
    excludedAssetCount: 0,
    assetsTruncated: false,
    warnings: kind === 'preview' ? ['PRERELEASE' as const] : [],
    evidenceId: `release-${kind}`,
  }
}
