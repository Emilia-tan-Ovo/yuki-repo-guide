import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import GuideView from '../src/features/guide/GuideView.vue'
import RepositoryUrlForm from '../src/features/guide/components/RepositoryUrlForm.vue'
import RepositoryIdentityCard from '../src/features/guide/components/RepositoryIdentityCard.vue'
import ExperienceSection from '../src/features/guide/components/ExperienceSection.vue'
import { createGuide, requestExplanation, requestQuickStart, requestExperience, recommendReleases, retryReadme, retryReleases, GuideApiError, GuideAuthenticationRequiredError } from '../src/features/guide/guideApi'
import type { GuideResponse } from '../src/features/guide/guideTypes'
import type { QuickStartResult } from '../src/features/guide/quickStartTypes'
import type { ExperienceResult } from '../src/features/guide/experienceTypes'

vi.mock('../src/features/guide/guideApi', async importOriginal => ({
  ...await importOriginal<typeof import('../src/features/guide/guideApi')>(),
  createGuide: vi.fn(), requestExplanation: vi.fn(), requestQuickStart: vi.fn(), requestExperience: vi.fn(),
  recommendReleases: vi.fn(), retryReadme: vi.fn(), retryReleases: vi.fn(),
}))
let wrapper: VueWrapper
afterEach(() => { wrapper?.unmount(); vi.resetAllMocks() })
function guide(): GuideResponse {
  return { explanationInputId: 'guide-1', experience: { guideId: 'guide-1', readmeResultId: 'readme-1', releasesResultId: 'release-1', quickStartResultId: null },
    repository: { owner: 'octo', name: 'notes', description: '事实描述', canonicalUrl: 'https://github.com/octo/notes', stars: 0, createdAt: '2026-01-01T00:00:00Z', repositoryEvidenceId: 'repo' },
    readme: { status: 'AVAILABLE', candidates: [], truncated: false }, languages: { status: 'NOT_PROVIDED', items: [] },
    releases: { status: 'NOT_PROVIDED', recommendation: { status: 'NOT_REQUESTED', availableLinuxFamilies: [] } }, evidence: {} }
}
function quick(): QuickStartResult {
  return { resultId: 'quick-1', status: 'AVAILABLE', contentStatus: 'COMPLETE', requirements: [],
    steps: [{ text: '启动应用', evidenceIds: [], blocks: [{ evidenceId: 'cmd', text: 'java -jar app.jar' }] }],
    configuration: [], cautions: [], gaps: [], evidence: {}, code: null, retryAfterSeconds: null }
}
function path(kind: 'ONLINE' | 'QUICK_START' | 'STABLE_RELEASE'): ExperienceResult {
  return { status: 'PRIMARY_AVAILABLE', primary: { id: kind, kind, reliable: true,
    options: [{ label: kind, url: kind === 'QUICK_START' ? '#quick-start' : 'https://example.com', warnings: [], evidenceIds: [] }] }, alternatives: [], unresolved: [], evidence: {} }
}
async function start() {
  vi.mocked(createGuide).mockResolvedValue(guide())
  vi.mocked(requestExplanation).mockResolvedValue({ status: 'INSUFFICIENT_EVIDENCE', introduction: null, evidence: {}, evidenceIds: [], code: null, retryAfterSeconds: null })
  wrapper = mount(GuideView, { props: { loggingOut: false } })
  wrapper.findComponent(RepositoryUrlForm).vm.$emit('submit', 'https://github.com/octo/notes')
  await flushPromises()
}
it('uses region result identifiers and ignores an earlier selection after Quick Start arrives', async () => {
  let finishQuick!: (value: QuickStartResult) => void
  let finishOld!: (value: ExperienceResult) => void
  vi.mocked(requestQuickStart).mockReturnValue(new Promise(resolve => { finishQuick = resolve }))
  vi.mocked(requestExperience).mockReturnValueOnce(new Promise(resolve => { finishOld = resolve })).mockResolvedValue(path('QUICK_START'))
  await start()
  expect(wrapper.text()).toContain('事实描述')
  finishQuick(quick())
  await flushPromises()
  expect(requestExperience).toHaveBeenLastCalledWith({ ...guide().experience, quickStartResultId: 'quick-1', runtime: null })
  expect(wrapper.findComponent(ExperienceSection).text()).toContain('按 Quick Start 体验')
  finishOld(path('ONLINE'))
  await flushPromises()
  expect(wrapper.findComponent(ExperienceSection).text()).not.toContain('在线体验')
  expect(wrapper.get('#quick-start').text()).toContain('java -jar app.jar')
})

it('registers runtime and region updates while keeping existing Quick Start content mounted', async () => {
  vi.mocked(requestQuickStart).mockResolvedValue(quick())
  vi.mocked(requestExperience).mockImplementation(async request => path(request.runtime ? 'STABLE_RELEASE' : 'QUICK_START'))
  const runtime = { operatingSystem: 'WINDOWS', architecture: 'X64' } as const
  vi.mocked(recommendReleases).mockResolvedValue({ resultId: 'release-2', evidence: {},
    releases: { ...guide().releases, recommendation: { status: 'READY', runtime, availableLinuxFamilies: [] } } })
  await start()
  const steps = wrapper.get('#quick-start').element
  wrapper.findComponent(RepositoryIdentityCard).vm.$emit('recommendReleases', runtime)
  await flushPromises()
  expect(recommendReleases).toHaveBeenCalledWith('https://github.com/octo/notes', runtime, 'guide-1')
  expect(requestExperience).toHaveBeenLastCalledWith({ ...guide().experience, releasesResultId: 'release-2', quickStartResultId: 'quick-1', runtime })
  expect(wrapper.get('#quick-start').element).toBe(steps)
  vi.mocked(retryReadme).mockResolvedValue({ resultId: 'readme-2', readme: guide().readme, evidence: {} })
  wrapper.findComponent(RepositoryIdentityCard).vm.$emit('retryReadme')
  await flushPromises()
  expect(requestExperience).toHaveBeenLastCalledWith({ ...guide().experience, readmeResultId: 'readme-2', releasesResultId: 'release-2', quickStartResultId: null, runtime })
  expect(wrapper.get('#quick-start').text()).toContain('已过期或已更新')
})

it('recovers authentication for only the current selection and keeps facts on expiry', async () => {
  vi.mocked(requestQuickStart).mockReturnValue(new Promise(() => {}))
  vi.mocked(requestExperience).mockRejectedValueOnce(new GuideAuthenticationRequiredError())
    .mockRejectedValueOnce(new GuideApiError('expired', 'EXPERIENCE_INPUT_EXPIRED', 410))
  await start()
  const resume = wrapper.emitted('authenticationRequired')?.[0]?.[0] as () => Promise<void>
  expect(resume).toBeTypeOf('function')
  await resume()
  await flushPromises()
  expect(wrapper.text()).toContain('事实描述')
  expect(wrapper.findComponent(ExperienceSection).text()).toContain('请重新生成导览')
  expect(wrapper.findComponent(ExperienceSection).text()).not.toContain('暂时没有体验方式')
  expect(createGuide).toHaveBeenCalledTimes(1)
  expect(requestQuickStart).toHaveBeenCalledTimes(1)
})

it('does not let a failed old request replace a successful new selection', async () => {
  let failOld!: (error: Error) => void
  let finishQuick!: (value: QuickStartResult) => void
  vi.mocked(requestQuickStart).mockReturnValue(new Promise(resolve => { finishQuick = resolve }))
  vi.mocked(requestExperience).mockReturnValueOnce(new Promise((_, reject) => { failOld = reject })).mockResolvedValue(path('QUICK_START'))
  await start()
  finishQuick(quick())
  await flushPromises()
  failOld(new GuideApiError('timeout'))
  await flushPromises()
  expect(wrapper.findComponent(ExperienceSection).text()).toContain('主要体验路径')
  expect(wrapper.findComponent(ExperienceSection).text()).not.toContain('推荐暂时无法更新')
})


it('restores all three concurrent requests through the last authentication callback', async () => {
  vi.mocked(createGuide).mockResolvedValue(guide())
  vi.mocked(requestExplanation).mockRejectedValueOnce(new GuideAuthenticationRequiredError()).mockResolvedValue({ status: 'INSUFFICIENT_EVIDENCE', introduction: null, evidence: {}, evidenceIds: [], code: null, retryAfterSeconds: null })
  vi.mocked(requestQuickStart).mockRejectedValueOnce(new GuideAuthenticationRequiredError()).mockResolvedValue(quick())
  vi.mocked(requestExperience).mockRejectedValue(new GuideAuthenticationRequiredError())
  wrapper = mount(GuideView, { props: { loggingOut: false } })
  wrapper.findComponent(RepositoryUrlForm).vm.$emit('submit', 'https://github.com/octo/notes')
  await flushPromises()
  vi.mocked(requestExperience).mockResolvedValue(path('QUICK_START'))
  const resume = wrapper.emitted('authenticationRequired')!.at(-1)![0] as () => Promise<void>
  await resume()
  await flushPromises()
  expect(requestExplanation).toHaveBeenCalledTimes(2)
  expect(requestQuickStart).toHaveBeenCalledTimes(2)
  expect(wrapper.get('#quick-start').text()).toContain('java -jar app.jar')
  expect(wrapper.findComponent(ExperienceSection).text()).toContain('主要体验路径')
})

it('shows a local Quick Start request failure instead of waiting and recalculates on retry', async () => {
  vi.mocked(requestQuickStart).mockRejectedValueOnce(new TypeError('network failure')).mockResolvedValue(quick())
  vi.mocked(requestExperience).mockImplementation(async request => request.quickStartResultId ? path('QUICK_START') : {
    status: 'UNDETERMINED', primary: null, alternatives: [], unresolved: ['QUICK_START_PENDING'], evidence: {},
  })
  await start()
  expect(wrapper.findComponent(ExperienceSection).text()).toContain('Quick Start 请求未成功')
  expect(wrapper.findComponent(ExperienceSection).text()).not.toContain('尚未取得结果')
  await wrapper.get('#quick-start button').trigger('click')
  await flushPromises()
  expect(wrapper.findComponent(ExperienceSection).text()).toContain('主要体验路径')
  expect(wrapper.findComponent(ExperienceSection).text()).not.toContain('请求未成功')
})

it('keeps online experience when a registered Release retry returns a failed region', async () => {
  vi.mocked(requestQuickStart).mockReturnValue(new Promise(() => {}))
  vi.mocked(requestExperience).mockResolvedValue({ ...path('ONLINE'), unresolved: ['RELEASES_UNAVAILABLE'] })
  vi.mocked(retryReleases).mockResolvedValue({ resultId: 'release-failed', evidence: {}, releases: {
    status: 'FAILED', latestStable: null, latestPrerelease: null,
    failure: { code: 'RELEASE_HISTORY_UNSUPPORTED', retryable: false, retryAfterSeconds: null },
    recommendation: { status: 'NOT_REQUESTED', availableLinuxFamilies: [] },
  } })
  await start()
  wrapper.findComponent(RepositoryIdentityCard).vm.$emit('retryReleases')
  await flushPromises()
  expect(requestExperience).toHaveBeenLastCalledWith({ ...guide().experience, releasesResultId: 'release-failed', runtime: null })
  expect(wrapper.findComponent(ExperienceSection).text()).toContain('在线体验')
  expect(wrapper.findComponent(ExperienceSection).text()).toContain('Release 来源暂不可用')
  expect(wrapper.findComponent(ExperienceSection).text()).not.toContain('请重新生成导览')
})


it('respects source cooldown when a recommendation returns a registered failure', async () => {
  vi.mocked(requestQuickStart).mockReturnValue(new Promise(() => {}))
  vi.mocked(requestExperience).mockResolvedValue(path('ONLINE'))
  vi.mocked(recommendReleases).mockResolvedValue({ resultId: 'release-limited', evidence: {}, releases: {
    status: 'FAILED', latestStable: null, latestPrerelease: null,
    failure: { code: 'GITHUB_RATE_LIMITED', retryable: true, retryAfterSeconds: 60 },
    recommendation: { status: 'NOT_REQUESTED', availableLinuxFamilies: [] },
  } })
  await start()
  wrapper.findComponent(RepositoryIdentityCard).vm.$emit('recommendReleases', { operatingSystem: 'WINDOWS', architecture: 'X64' })
  await flushPromises()
  expect(wrapper.findComponent(RepositoryIdentityCard).props('releaseRetryDisabled')).toBe(true)
  expect(wrapper.findComponent(RepositoryIdentityCard).props('releaseErrorMessage')).toContain('GitHub 暂时限制了请求')
  expect(wrapper.findComponent(RepositoryIdentityCard).props('releaseRetryMessage')).toContain('1 分钟')
  wrapper.findComponent(RepositoryIdentityCard).vm.$emit('retryReleases')
  await flushPromises()
  expect(retryReleases).not.toHaveBeenCalled()
  expect(wrapper.findComponent(ExperienceSection).text()).toContain('在线体验')
})
