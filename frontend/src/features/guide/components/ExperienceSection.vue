<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ExplanationState } from '../explanationTypes'
import type { ExperienceResult, ExperienceState } from '../experienceTypes'

const props = defineProps<{ result: ExperienceResult | null; state: ExperienceState; message: string; quickStartState?: ExplanationState }>()
defineEmits<{ retry: []; regenerate: [] }>()
const paths = computed(() => props.result ? [props.result.primary, ...props.result.alternatives].filter(path => path !== null) : [])
const changeMessage = ref('')
watch(() => props.result?.primary?.kind, (next, previous) => {
  if (!previous || next === previous) return
  changeMessage.value = next === 'STABLE_RELEASE'
    ? '根据你确认的运行环境，当前优先推荐下载正式版；其他可行方式仍保留在下方。'
    : '根据更新后的导览资料，已调整当前推荐；其他可用内容仍可继续查看。'
})
const titles = { ONLINE: '在线体验', STABLE_RELEASE: '下载正式版', PRERELEASE: '预发布下载', QUICK_START: '按 Quick Start 体验' }
const reasons: Record<string, string> = {
  RUNTIME_REQUIRED: '确认运行环境后，可以检查适合你的下载方案。',
  LINUX_FAMILY_REQUIRED: '下载方案还需要确认 Linux 发行版族。',
  README_UNAVAILABLE: 'README 来源暂不可用，在线体验尚无法判断。',
  RELEASES_UNAVAILABLE: 'Release 来源暂不可用，下载方案尚无法判断。',
  QUICK_START_PENDING: 'Quick Start 尚未取得结果，可先浏览已有内容。',
  QUICK_START_UNAVAILABLE: 'Quick Start 暂不可用，请查看下方区域的恢复提示。',
  QUICK_START_SOURCE_CHANGED: 'README 已更新，请重新生成导览以取得对应的 Quick Start。',
}
const warnings: Record<string, string> = {
  EXTERNAL_SITE_NOT_VERIFIED: '外部网站未经 RepoGuide 安全认证。',
  INSECURE_HTTP: '此链接使用未加密的 HTTP。',
  DOWNLOAD_NOT_VERIFIED: '下载文件未经 RepoGuide 安全认证，请自行核对来源。',
  MATCH_NOT_GUARANTEED: '文件名分析不保证实际兼容。',
  PRERELEASE: '这是预发布测试版本，请谨慎判断。',
  SOME_ASSETS_OMITTED: '部分资源未展示，请查看原始 Release。',
  POSSIBLY_APPLICABLE: '此资源仅可能适用，请自行确认兼容性。',
  COMMANDS_NOT_VERIFIED: '步骤来自 README，未经过实际运行或安全认证。',
  INCOMPLETE_QUICK_START: '步骤存在必要信息缺口，不能直接视为完整流程。',
}
function reasonMessage(reason: string) {
  if (reason === 'QUICK_START_PENDING') {
    if (props.quickStartState === 'unavailable') return 'Quick Start 请求未成功，请查看下方区域的重试提示。'
    if (props.quickStartState === 'expired') return 'Quick Start 所需资料已过期或已更新，请重新生成导览。'
  }
  return reasons[reason] ?? reason
}
function safeUrl(url: string) { return url === '#quick-start' || /^https?:\/\//i.test(url) ? url : undefined }
</script>

<template>
  <section class="experience" aria-labelledby="experience-path-title" :aria-busy="state === 'loading'">
    <p class="eyebrow">下一步 · 体验路径</p>
    <h2 id="experience-path-title">现在可以怎样体验</h2>
    <div role="status">
      <p v-if="state === 'loading'">正在更新推荐{{ result ? '，下方保留的是上一次结果。' : '…' }}</p>
      <template v-else-if="state === 'error' || state === 'expired'">
        <p>{{ message || '推荐暂时无法更新。' }}{{ result ? ' 下方保留的是上一次结果。' : '' }}</p>
        <button v-if="state === 'expired'" type="button" @click="$emit('regenerate')">重新生成导览</button>
        <button v-else type="button" @click="$emit('retry')">重试路径选择</button>
      </template>
      <template v-else-if="result">
        <p v-if="result.status === 'CANDIDATES_ONLY'">暂无可靠的主要体验方式，以下候选需要你进一步判断。</p>
        <p v-else-if="result.status === 'UNDETERMINED'">暂时还无法判断体验方式，请先完成下方提示的检查。</p>
        <p v-else-if="result.status === 'NO_PATH'">暂时没有体验方式。</p>
        <p v-if="changeMessage" class="hint">{{ changeMessage }}</p>
      </template>
    </div>
    <ul v-if="result?.unresolved.length" class="hint">
      <li v-for="reason in result.unresolved" :key="reason">{{ reasonMessage(reason) }}</li>
    </ul>
    <article v-for="path in paths" :key="path.id" :class="{ primary: path.id === result?.primary?.id }">
      <p class="badge">{{ state !== 'available' ? '上一次结果 · ' : '' }}{{ path.id === result?.primary?.id ? '主要体验路径' : path.reliable ? '备选方案' : '需谨慎判断的候选' }}</p>
      <h3>{{ titles[path.kind] }}</h3>
      <ul class="options">
        <li v-for="option in path.options" :key="option.url">
          <a :href="safeUrl(option.url)" :target="option.url.startsWith('#') ? undefined : '_blank'" rel="noopener noreferrer">{{ option.label }}</a>
          <ul v-if="option.warnings.length" class="warnings">
            <li v-for="warning in option.warnings" :key="warning">{{ warnings[warning] ?? warning }}</li>
          </ul>
          <details v-if="option.evidenceIds.length" :data-path="path.id">
            <summary>查看行动依据</summary>
            <div v-for="id in option.evidenceIds" :key="id" class="evidence">
              <template v-if="result?.evidence[id]">
                <p>{{ result.evidence[id]!.source }}</p>
                <blockquote>{{ result.evidence[id]!.detail }}</blockquote>
                <a :href="safeUrl(result.evidence[id]!.url)" target="_blank" rel="noopener noreferrer">查看原始来源 ↗</a>
                <small v-if="result.evidence[id]!.path">{{ result.evidence[id]!.path }} · {{ result.evidence[id]!.sha }}</small>
              </template>
            </div>
          </details>
        </li>
      </ul>
      <p v-if="path.optionCount && path.optionCount > path.options.length" class="hint">共 {{ path.optionCount }} 项，已展示前 {{ path.options.length }} 项；更多资源可在原始 Release 查看。</p>
    </article>
  </section>
</template>

<style scoped>
.experience { grid-column: 1 / -1; padding: 1.4rem; border: 1px solid var(--color-border); border-radius: 1.25rem; background: rgba(255,255,255,.85); overflow-wrap: anywhere; }
.eyebrow, .badge { color: var(--color-accent); font-size: .78rem; font-weight: 700; }
h2 { margin: .3rem 0 1rem; color: var(--color-heading); }
h3 { margin: .4rem 0; }
article { padding: 1rem; margin-top: 1rem; border: 1px solid var(--color-border); border-radius: .9rem; }
article.primary { border-color: var(--color-accent); background: #faf7ff; }
.options { padding-left: 1.2rem; }
.options > li { margin: .8rem 0; }
.hint, .warnings, small { color: var(--color-text-muted); font-size: .86rem; line-height: 1.65; }
.warnings { padding-left: 1.1rem; margin: .5rem 0; }
a { color: var(--color-accent); }
summary { cursor: pointer; font-size: .86rem; }
blockquote { margin: .5rem 0; white-space: pre-wrap; }
.evidence { padding: .6rem; margin-top: .5rem; border-left: 2px solid var(--color-border); }
small { display: block; }
button { padding: .5rem .8rem; border: 1px solid var(--color-border); border-radius: .5rem; background: white; color: var(--color-accent); cursor: pointer; }
</style>
