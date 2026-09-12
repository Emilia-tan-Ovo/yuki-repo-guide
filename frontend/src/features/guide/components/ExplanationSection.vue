<script setup lang="ts">
import type { ExplanationResult, ExplanationState } from '../explanationTypes'
defineProps<{
  state: ExplanationState
  result: ExplanationResult | null
  disabled: boolean
  retryMessage: string
}>()
defineEmits<{ retry: []; regenerate: [] }>()
</script>

<template>
  <section class="explanation" aria-labelledby="explanation-title" aria-live="polite" :aria-busy="state === 'loading'">
    <p class="label">AI 生成 · 基于仓库资料</p>
    <h2 id="explanation-title">一句话认识项目</h2>
    <template v-if="state === 'available' && result">
      <p class="introduction">{{ result.introduction }}</p>
      <details>
        <summary>查看介绍证据</summary>
        <p class="hint">引用可追溯，不代表生成内容已被核实。</p>
        <div v-for="id in result.evidenceIds" :key="id" class="evidence">
          <blockquote>{{ result.evidence[id]?.text }}</blockquote>
          <a :href="result.evidence[id]?.sourceUrl" target="_blank" rel="noopener noreferrer">查看来源 ↗</a>
          <small v-if="result.evidence[id]?.path">{{ result.evidence[id]?.path }} · {{ result.evidence[id]?.sha }}</small>
        </div>
      </details>
    </template>
    <p v-else-if="state === 'loading'">正在生成介绍，仓库事实已可浏览。</p>
    <p v-else-if="state === 'insufficient'">现有仓库资料不足以生成可靠介绍。</p>
    <template v-else-if="state === 'expired'">
      <p>介绍所需资料已过期或已更新，请重新生成导览。</p>
      <button type="button" @click="$emit('regenerate')">重新生成导览</button>
    </template>
    <template v-else-if="state === 'in-progress'">
      <p>介绍正在生成，请等待原请求完成；若原请求已中断，可重新生成导览。</p>
      <button type="button" @click="$emit('regenerate')">重新生成导览</button>
    </template>
    <template v-else>
      <p>介绍暂不可用，你仍可以浏览仓库事实。</p>
      <button type="button" :disabled="disabled" @click="$emit('retry')">重试介绍</button>
      <p v-if="retryMessage" class="hint">{{ retryMessage }}</p>
    </template>
  </section>
</template>

<style scoped>
.explanation { margin-top: 1.25rem; padding: 1.5rem; border: 1px solid #d9cce9; border-radius: 1.25rem; background: #f6f0fa; color: #443653; overflow-wrap: anywhere; }
.label { font-size: .78rem; color: #725987; letter-spacing: .04em; }
h2 { margin: .5rem 0 1rem; font-size: 1.2rem; }
.introduction { line-height: 1.8; font-size: 1.05rem; }
summary { cursor: pointer; }
.hint, small { color: #74677d; font-size: .82rem; }
.evidence { margin-top: 1rem; }
blockquote { margin: .5rem 0; white-space: pre-wrap; line-height: 1.6; }
small { display: block; margin-top: .4rem; }
a { color: #674386; }
button { border: 1px solid #bdabcf; border-radius: .7rem; padding: .55rem .9rem; background: white; color: #53366d; cursor: pointer; }
button:disabled { opacity: .55; cursor: default; }
</style>
