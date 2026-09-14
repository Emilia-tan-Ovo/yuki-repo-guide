<script setup lang="ts">
import { computed } from 'vue'
import type { ExplanationState } from '../explanationTypes'
import type { QuickStartResult } from '../quickStartTypes'
const props = defineProps<{ state: ExplanationState; result: QuickStartResult | null; disabled: boolean; retryMessage: string }>()
defineEmits<{ retry: []; regenerate: [] }>()
const labels = { COMPLETE: '完整', INCOMPLETE: '不完整', NOT_PROVIDED: '未提供' }
const sections = computed(() => props.result ? [
  { title: '环境要求', items: props.result.requirements },
  { title: '体验步骤', items: props.result.steps },
  { title: '必要配置', items: props.result.configuration },
  { title: '注意事项', items: props.result.cautions },
  { title: '缺少的信息', items: props.result.gaps },
].filter(section => section.items.length) : [])
const sourceUnavailable = computed(() => props.result?.code === 'QUICK_START_SOURCE_UNAVAILABLE')
</script>

<template>
  <section class="quick-start" aria-labelledby="quick-start-title" aria-live="polite" :aria-busy="state === 'loading'">
    <p class="label">AI 整理 · 基于 README 证据</p>
    <h2 id="quick-start-title">Quick Start <span v-if="result?.contentStatus">· {{ labels[result.contentStatus] }}</span></h2>
    <template v-if="state === 'available' && result">
      <p v-if="result.contentStatus === 'NOT_PROVIDED'">README 未提供可整理的体验步骤，请查看仓库原始说明。</p>
      <template v-else>
        <p class="hint">步骤完整程度依据来源判断，未经过实际运行或安全认证。命令与配置原样引用，请核对来源后自行决定是否使用。</p>
        <section v-for="section in sections" :key="section.title">
          <h3>{{ section.title }}</h3>
          <ol>
            <li v-for="(item, index) in section.items" :key="index">
              <p>{{ item.text }}</p>
              <pre v-for="block in item.blocks" :key="block.evidenceId"><code>{{ block.text }}</code></pre>
              <details>
                <summary>查看这项内容的证据</summary>
                <div v-for="id in item.evidenceIds" :key="id" class="evidence">
                  <p class="hint">{{ result.evidence[id]?.section }}</p>
                  <blockquote>{{ result.evidence[id]?.text }}</blockquote>
                  <a :href="result.evidence[id]?.sourceUrl" target="_blank" rel="noopener noreferrer">查看 README 来源 ↗</a>
                  <small>{{ result.evidence[id]?.path }} · {{ result.evidence[id]?.sha }}</small>
                </div>
              </details>
            </li>
          </ol>
        </section>
      </template>
    </template>
    <p v-else-if="state === 'loading'">正在整理 Quick Start，其他导览内容已可浏览。</p>
    <template v-else-if="state === 'expired' || state === 'in-progress'">
      <p>{{ state === 'expired' ? 'Quick Start 所需资料已过期或已更新，请重新生成导览。' : 'Quick Start 正在生成，请等待原请求；若原请求已中断，可重新生成导览。' }}</p>
      <button type="button" @click="$emit('regenerate')">重新生成导览</button>
    </template>
    <template v-else>
      <p v-if="sourceUnavailable">README 来源暂不可用，请先恢复 README，再重新生成导览。</p>
      <p v-else-if="result?.code === 'QUICK_START_INPUT_TRUNCATED'">操作资料超过当前取材范围，暂时无法整理 Quick Start，请查看 README。</p>
      <p v-else>Quick Start 暂不可用，其他导览内容仍然保留。</p>
      <button v-if="sourceUnavailable" type="button" @click="$emit('regenerate')">重新生成导览</button>
      <button v-else-if="result?.code !== 'QUICK_START_INPUT_TRUNCATED'" type="button" :disabled="disabled" @click="$emit('retry')">重试 Quick Start</button>
      <p v-if="retryMessage" class="hint">{{ retryMessage }}</p>
    </template>
  </section>
</template>

<style scoped>
.quick-start { margin-top: 1.25rem; padding: 1.5rem; border: 1px solid #d9cce9; border-radius: 1.25rem; background: #f6f0fa; color: #443653; overflow-wrap: anywhere; }
.label, .hint, small { color: #74677d; font-size: .82rem; line-height: 1.6; }
h2 { font-size: 1.2rem; } h3 { font-size: 1rem; margin-top: 1.5rem; }
li { margin-bottom: 1rem; } p { line-height: 1.7; }
pre { padding: 1rem; background: #fff; border: 1px solid #ded5e5; border-radius: .6rem; overflow-x: auto; white-space: pre; }
summary { cursor: pointer; font-size: .85rem; }
blockquote { white-space: pre-wrap; margin: .5rem 0; line-height: 1.6; }
small { display: block; } a { color: #674386; }
button { border: 1px solid #bdabcf; border-radius: .7rem; padding: .55rem .9rem; background: white; color: #53366d; cursor: pointer; }
button:disabled { opacity: .55; cursor: default; }
</style>
