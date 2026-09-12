import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import GuideView from '../src/features/guide/GuideView.vue'
import RepositoryUrlForm from '../src/features/guide/components/RepositoryUrlForm.vue'
import { createGuide, requestExplanation, retryReadme, GuideApiError, GuideAuthenticationRequiredError } from '../src/features/guide/guideApi'
import RepositoryIdentityCard from '../src/features/guide/components/RepositoryIdentityCard.vue'
import ExplanationSection from '../src/features/guide/components/ExplanationSection.vue'

vi.mock('../src/features/guide/guideApi', async original => ({
  ...await original<typeof import('../src/features/guide/guideApi')>(),
  createGuide: vi.fn(), requestExplanation: vi.fn(), retryReadme: vi.fn(),
}))
let wrapper: VueWrapper
afterEach(() => { wrapper?.unmount(); vi.clearAllMocks() })
const guide = (id: string) => ({
  explanationInputId: id,
  repository: { owner: 'octo', name: id, description: '事实描述', canonicalUrl: 'https://github.com/octo/' + id,
    stars: 1, createdAt: '2026-01-01T00:00:00Z', pushedAt: null, projectWebsiteUrl: null, evidenceId: 'repo' },
  readme: { status: 'AVAILABLE' as const, candidates: [], truncated: false },
  languages: { status: 'NOT_PROVIDED' as const, items: [] },
  releases: { status: 'NOT_PROVIDED' as const, stable: null, prerelease: null,
    recommendation: { status: 'NOT_REQUESTED' as const, runtime: null, availableLinuxFamilies: [] } },
  evidence: {},
})
const available = {
  status: 'AVAILABLE' as const, introduction: '一个本地笔记应用。', evidenceIds: ['intro'],
  evidence: { intro: { id: 'intro', text: '支持本地笔记', sourceUrl: 'https://github.com/octo/notes', path: 'README.md', sha: 'abc' } },
  code: null, retryAfterSeconds: null,
}
describe('渐进式介绍', () => {
  it('never starts explanation after unmounting during guide creation', async () => {
    let resolve!: (value: ReturnType<typeof guide>) => void
    vi.mocked(createGuide).mockReturnValueOnce(new Promise(r => { resolve = r }))
    wrapper = mount(GuideView, { props: { loggingOut: false } })
    wrapper.findComponent(RepositoryUrlForm).vm.$emit('submit', 'https://github.com/octo/first')
    wrapper.unmount()
    resolve(guide('first'))
    await flushPromises()
    expect(requestExplanation).not.toHaveBeenCalled()
  })
  async function start() {
    vi.mocked(createGuide).mockResolvedValue(guide('first'))
    wrapper = mount(GuideView, { props: { loggingOut: false } })
    wrapper.findComponent(RepositoryUrlForm).vm.$emit('submit', 'https://github.com/octo/first')
    await flushPromises()
  }
  it('keeps facts on failure and retries only the explanation', async () => {
    vi.mocked(requestExplanation).mockResolvedValueOnce({
      ...available, status: 'UNAVAILABLE', introduction: null, evidence: {}, evidenceIds: [],
      code: 'EXPLANATION_TIMEOUT',
    }).mockResolvedValueOnce(available)
    await start()
    expect(wrapper.text()).toContain('事实描述')
    expect(wrapper.text()).toContain('介绍暂不可用')
    await wrapper.findComponent(ExplanationSection).find('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(available.introduction)
    expect(createGuide).toHaveBeenCalledTimes(1)
    expect(requestExplanation).toHaveBeenCalledTimes(2)
  })
  it('does not offer regeneration for insufficient evidence', async () => {
    vi.mocked(requestExplanation).mockResolvedValue({
      ...available, status: 'INSUFFICIENT_EVIDENCE', introduction: null, evidence: {}, evidenceIds: [],
    })
    await start()
    expect(wrapper.text()).toContain('资料不足')
    expect(wrapper.findComponent(ExplanationSection).find('button').exists()).toBe(false)
  })
  it('ignores old explanations after switching repositories', async () => {
    let resolve!: (value: typeof available) => void
    vi.mocked(requestExplanation).mockReturnValueOnce(new Promise(r => { resolve = r }))
      .mockResolvedValueOnce({ ...available, introduction: '第二个项目。' })
    await start()
    vi.mocked(createGuide).mockResolvedValue(guide('second'))
    wrapper.findComponent(RepositoryUrlForm).vm.$emit('submit', 'https://github.com/octo/second')
    await flushPromises()
    resolve(available)
    await flushPromises()
    expect(wrapper.text()).toContain('第二个项目。')
    expect(wrapper.text()).not.toContain(available.introduction)
  })
  it('invalidates a pending explanation when README retry succeeds', async () => {
    let resolve!: (value: typeof available) => void
    vi.mocked(requestExplanation).mockReturnValueOnce(new Promise(r => { resolve = r }))
    await start()
    vi.mocked(retryReadme).mockResolvedValue({ readme: guide('first').readme, evidence: {} })
    wrapper.findComponent(RepositoryIdentityCard).vm.$emit('retry-readme')
    await flushPromises()
    resolve(available)
    await flushPromises()
    expect(wrapper.text()).toContain('重新生成导览')
    expect(wrapper.text()).not.toContain(available.introduction)
  })
  it('recovers authentication with the same snapshot and handles an expired snapshot', async () => {
    vi.mocked(requestExplanation).mockRejectedValueOnce(new GuideAuthenticationRequiredError())
      .mockRejectedValueOnce(new GuideApiError('expired', 'EXPLANATION_INPUT_EXPIRED', 410))
    await start()
    const retry = wrapper.emitted('authenticationRequired')?.[0]?.[0] as () => Promise<void>
    expect(retry).toBeTypeOf('function')
    await retry()
    await flushPromises()
    expect(requestExplanation).toHaveBeenNthCalledWith(2, 'first')
    expect(wrapper.text()).toContain('重新生成导览')
    expect(wrapper.text()).toContain('事实描述')
  })
  it('disables retries while a reliable rate limit is active', async () => {
    vi.mocked(requestExplanation).mockResolvedValue({
      ...available, status: 'UNAVAILABLE', introduction: null, code: 'EXPLANATION_RATE_LIMITED', retryAfterSeconds: 30,
    })
    await start()
    expect(wrapper.findComponent(ExplanationSection).find('button').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('秒后可重试')
  })
  it('shows facts before explanation and then displays referenced AI text', async () => {
    let resolve!: (value: typeof available) => void
    vi.mocked(createGuide).mockResolvedValue(guide('first'))
    vi.mocked(requestExplanation).mockReturnValue(new Promise(r => { resolve = r }))
    wrapper = mount(GuideView, { props: { loggingOut: false } })
    wrapper.findComponent(RepositoryUrlForm).vm.$emit('submit', 'https://github.com/octo/first')
    await flushPromises()
    expect(wrapper.text()).toContain('事实描述')
    expect(wrapper.text()).toContain('正在生成介绍')
    expect(requestExplanation).toHaveBeenCalledWith('first')
    resolve(available)
    await flushPromises()
    expect(wrapper.text()).toContain(available.introduction)
    expect(wrapper.text()).toContain('AI 生成')
    expect(wrapper.text()).toContain('支持本地笔记')
  })
})
