import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ExperienceSection from '../src/features/guide/components/ExperienceSection.vue'
import type { ExperienceResult } from '../src/features/guide/experienceTypes'

const quick = { id: 'quick-start', kind: 'QUICK_START', reliable: true,
  options: [{ label: '查看 Quick Start', url: '#quick-start', evidenceIds: ['qs-1'], warnings: ['COMMANDS_NOT_VERIFIED'] }] } as const
function result(): ExperienceResult {
  return { status: 'PRIMARY_AVAILABLE', primary: { ...quick, options: quick.options.map(option => ({ ...option, evidenceIds: [...option.evidenceIds], warnings: [...option.warnings] })) }, alternatives: [],
    unresolved: ['RUNTIME_REQUIRED'], evidence: { 'qs-1': { source: 'README', url: 'https://github.com/octo/notes', detail: 'Run the app', path: 'README.md', sha: 'abc' } } }
}
describe('ExperienceSection', () => {
  it('preserves expanded evidence when the main path becomes an alternative', async () => {
    const initial = result()
    const wrapper = mount(ExperienceSection, { props: { result: initial, state: 'available', message: '' } })
    const evidence = wrapper.get('details').element as HTMLDetailsElement
    evidence.open = true
    await wrapper.setProps({ result: { ...initial, primary: { id: 'stable-recommended', kind: 'STABLE_RELEASE', reliable: true,
      options: [{ label: 'setup.exe', url: 'https://github.com/octo/notes/releases/download/v1/setup.exe', evidenceIds: [], warnings: ['DOWNLOAD_NOT_VERIFIED'] }] }, alternatives: [initial.primary!] } })
    expect(wrapper.get('details[data-path="quick-start"]').element).toBe(evidence)
    expect(evidence.open).toBe(true)
    expect(wrapper.text()).toContain('根据你确认的运行环境')
    expect(wrapper.get('a[href="#quick-start"]').text()).toContain('Quick Start')
    wrapper.unmount()
  })
  it('distinguishes a warned candidate from absence and from a failed request', async () => {
    const data = result()
    data.status = 'CANDIDATES_ONLY'
    data.alternatives = [{ ...data.primary!, reliable: false }]
    data.primary = null
    const wrapper = mount(ExperienceSection, { props: { result: data, state: 'available', message: '' } })
    expect(wrapper.text()).toContain('暂无可靠的主要体验方式')
    expect(wrapper.text()).not.toContain('暂时没有体验方式')
    await wrapper.setProps({ state: 'error', message: '推荐暂时无法更新' })
    expect(wrapper.text()).toContain('推荐暂时无法更新')
    expect(wrapper.text()).toContain('上一次')
    wrapper.unmount()
  })
})
