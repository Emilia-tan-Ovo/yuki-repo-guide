import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import OnlineExperienceSection from '../src/features/guide/components/OnlineExperienceSection.vue'

const availableReadme = {
  status: 'AVAILABLE' as const,
  candidates: [
    {
      label: 'Try it online',
      url: 'https://demo.example.com',
      evidenceId: 'readme-online-experience-1',
      warnings: ['EXTERNAL_SITE_NOT_VERIFIED' as const],
    },
    {
      label: '在线体验',
      url: 'http://play.example.com',
      evidenceId: 'readme-online-experience-2',
      warnings: ['EXTERNAL_SITE_NOT_VERIFIED' as const, 'INSECURE_HTTP' as const],
    },
  ],
  truncated: true,
  failure: null,
}

describe('OnlineExperienceSection', () => {
  it('shows candidates as safe external links with README evidence and warnings', () => {
    const wrapper = mount(OnlineExperienceSection, {
      props: {
        readme: availableReadme,
        evidence: {
          'readme-online-experience-1': {
            type: 'README',
            source: 'GitHub README',
            languages: [],
            readmeUrl: 'https://github.com/octo/example/blob/main/README.md',
            path: 'README.md',
            sha: 'abc123',
            context: '<script>alert("never execute")</script> Try it online',
          },
        },
        retrying: false,
        retryDisabled: false,
        retryMessage: '',
        errorMessage: '',
      },
    })

    const links = wrapper.findAll('a.experience-link')
    expect(links).toHaveLength(2)
    expect(links[0].attributes()).toMatchObject({
      href: 'https://demo.example.com',
      target: '_blank',
      rel: 'noopener noreferrer',
    })
    expect(wrapper.text()).toContain('外部站点，未经 RepoGuide 安全认证')
    expect(wrapper.text()).toContain('HTTP 连接未加密')
    expect(wrapper.text()).toContain('仅展示前 20 个候选入口')
    expect(wrapper.html()).not.toContain('<script>')
    expect(wrapper.text()).toContain('<script>alert("never execute")</script>')
  })

  it('distinguishes a missing README from a failed README request', () => {
    const wrapper = mount(OnlineExperienceSection, {
      props: {
        readme: { status: 'NOT_PROVIDED', candidates: [], truncated: false, failure: null },
        evidence: {},
        retrying: false,
        retryDisabled: false,
        retryMessage: '',
        errorMessage: '',
      },
    })

    expect(wrapper.text()).toContain('仓库没有提供可读取的 README')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('emits a local retry only for retryable failures', async () => {
    const wrapper = mount(OnlineExperienceSection, {
      props: {
        readme: {
          status: 'FAILED',
          candidates: [],
          truncated: false,
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
      readme: {
        status: 'FAILED',
        candidates: [],
        truncated: false,
        failure: {
          code: 'README_CONTENT_UNSUPPORTED',
          retryable: false,
          retryAfterSeconds: null,
        },
      },
    })
    expect(wrapper.find('button').exists()).toBe(false)
    expect(wrapper.text()).toContain('当前 README 内容格式暂不受支持')
  })
})
