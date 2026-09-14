import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import QuickStartSection from '../src/features/guide/components/QuickStartSection.vue'

describe('Quick Start', () => {
  it('shows original commands as text and explains incomplete evidence without offering regeneration', () => {
    const wrapper = mount(QuickStartSection, { props: {
      state: 'available', disabled: false, retryMessage: '', result: {
        status: 'AVAILABLE', contentStatus: 'INCOMPLETE', requirements: [], configuration: [], cautions: [],
        steps: [{ text: '启动项目', evidenceIds: ['cmd'], blocks: [{ evidenceId: 'cmd', text: 'run <script>alert(1)</script>\n' }] }],
        gaps: [{ text: '缺少数据库配置说明', evidenceIds: ['config'], blocks: [] }],
        evidence: {
          cmd: { id: 'cmd', kind: 'COMMAND', sourceUrl: 'https://github.com/octo/app', path: 'README.md', sha: 'abc', section: 'Install', order: 1, text: 'run <script>alert(1)</script>\n' },
          config: { id: 'config', kind: 'TEXT', sourceUrl: 'https://github.com/octo/app', path: 'README.md', sha: 'abc', section: 'Install', order: 2, text: '先配置数据库' },
        }, code: null, retryAfterSeconds: null,
      },
    } })
    expect(wrapper.text()).toContain('不完整')
    expect(wrapper.text()).toContain('缺少数据库配置说明')
    expect(wrapper.find('pre').element.textContent).toBe('run <script>alert(1)</script>\n')
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.find('button').exists()).toBe(false)
    wrapper.unmount()
  })
})
