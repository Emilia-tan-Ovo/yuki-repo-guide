<script setup lang="ts">
import { computed } from 'vue'
import type { Evidence, ReadmeSection } from '../guideTypes'

const props = defineProps<{
  readme: ReadmeSection
  evidence: Record<string, Evidence>
  retrying: boolean
  retryDisabled: boolean
  retryMessage: string
  errorMessage: string
}>()

defineEmits<{
  retry: []
}>()

const failureMessage = computed(() => {
  if (props.readme.failure?.code === 'README_CONTENT_UNSUPPORTED') {
    return '当前 README 内容格式暂不受支持，无法识别在线体验入口。'
  }
  return props.errorMessage || 'README 区域加载失败，请重新尝试。'
})

function warningLabel(warning: string): string {
  return warning === 'INSECURE_HTTP'
    ? 'HTTP 连接未加密'
    : '外部站点，未经 RepoGuide 安全认证'
}
</script>

<template>
  <section class="experience-section" aria-labelledby="experience-title">
    <div class="section-heading">
      <div>
        <p class="eyebrow">README 线索</p>
        <h3 id="experience-title">在线体验</h3>
      </div>
      <span v-if="readme.status === 'AVAILABLE'" class="section-status">来自 README</span>
    </div>

    <div v-if="readme.status === 'AVAILABLE' && readme.candidates.length" class="experience-list">
      <article
        v-for="candidate in readme.candidates"
        :key="candidate.url"
        class="experience-item"
      >
        <a
          class="experience-link"
          :href="candidate.url"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ candidate.label }} <span aria-hidden="true">↗</span>
        </a>
        <div v-if="candidate.warnings.length" class="warning-list">
          <span v-for="warning in candidate.warnings" :key="warning">
            {{ warningLabel(warning) }}
          </span>
        </div>
        <details v-if="evidence[candidate.evidenceId]" class="evidence-panel">
          <summary>查看 README 证据</summary>
          <div class="evidence-content">
            <p>来源：{{ evidence[candidate.evidenceId].source }}</p>
            <p>文件：{{ evidence[candidate.evidenceId].path }}</p>
            <p>版本：<code>{{ evidence[candidate.evidenceId].sha }}</code></p>
            <p>原文上下文：{{ evidence[candidate.evidenceId].context }}</p>
            <a
              v-if="evidence[candidate.evidenceId].readmeUrl"
              :href="evidence[candidate.evidenceId].readmeUrl || undefined"
              target="_blank"
              rel="noopener noreferrer"
            >
              在 GitHub 查看 README <span aria-hidden="true">↗</span>
            </a>
          </div>
        </details>
      </article>
      <p v-if="readme.truncated" class="truncation-note">
        候选较多，仅展示前 20 个候选入口。
      </p>
    </div>

    <p v-else-if="readme.status === 'AVAILABLE'" class="readme-state">
      已读取 README，但没有找到明确标注的在线体验入口。
    </p>

    <p v-else-if="readme.status === 'NOT_PROVIDED'" class="readme-state">
      仓库没有提供可读取的 README，因此暂无在线体验线索。
    </p>

    <div v-else class="readme-failure" role="status">
      <p>{{ failureMessage }}</p>
      <template v-if="readme.failure?.retryable">
        <p class="retry-hint">{{ retryMessage }}</p>
        <button type="button" :disabled="retryDisabled" @click="$emit('retry')">
          {{ retrying ? '正在重试…' : '重试 README 区域' }}
        </button>
      </template>
    </div>
  </section>
</template>

<style scoped>
.experience-section {
  display: grid;
  gap: 1rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--color-border);
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.eyebrow {
  margin: 0 0 0.15rem;
  color: var(--color-accent);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h3 {
  margin: 0;
  color: var(--color-heading);
  font-size: 1.05rem;
}

.section-status {
  padding: 0.3rem 0.55rem;
  border-radius: 999px;
  color: var(--color-accent);
  background: var(--color-accent-wash);
  font-size: 0.72rem;
  font-weight: 700;
}

.experience-list,
.experience-item {
  display: grid;
  gap: 0.75rem;
}

.experience-item {
  padding: 0.9rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: 0.9rem;
  background: rgba(255, 255, 255, 0.66);
}

.experience-link {
  width: fit-content;
  max-width: 100%;
  overflow-wrap: anywhere;
  color: var(--color-accent);
  font-weight: 750;
  text-decoration: none;
}

.experience-link:hover {
  text-decoration: underline;
  text-underline-offset: 0.2em;
}

.warning-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.warning-list span {
  padding: 0.25rem 0.5rem;
  border-radius: 999px;
  color: #7d5b26;
  background: #fff4d9;
  font-size: 0.72rem;
  font-weight: 700;
}

.readme-state,
.readme-failure,
.truncation-note {
  margin: 0;
  padding: 0.9rem 1rem;
  border-radius: 0.85rem;
  background: var(--color-accent-wash);
  font-size: 0.86rem;
  line-height: 1.6;
}

.truncation-note {
  padding: 0;
  color: var(--color-text-muted);
  background: transparent;
  font-size: 0.76rem;
}

.readme-failure {
  color: #7d3443;
  background: #fff7f8;
}

.readme-failure p {
  margin: 0;
}

.retry-hint {
  color: #996070;
  font-size: 0.78rem;
}

.readme-failure button {
  min-height: 2.45rem;
  margin-top: 0.65rem;
  padding: 0.55rem 0.8rem;
  border: 0;
  border-radius: 0.7rem;
  color: white;
  background: var(--color-accent);
  font: inherit;
  font-size: 0.82rem;
  font-weight: 750;
  cursor: pointer;
}

.readme-failure button:disabled {
  cursor: wait;
  opacity: 0.6;
}

.evidence-panel {
  border: 1px solid var(--color-border);
  border-radius: 0.75rem;
  color: var(--color-text-muted);
  background: rgba(247, 242, 250, 0.55);
  font-size: 0.8rem;
}

.evidence-panel summary {
  padding: 0.65rem 0.8rem;
  color: var(--color-text);
  font-weight: 700;
  cursor: pointer;
}

.evidence-content {
  padding: 0 0.8rem 0.75rem;
  overflow-wrap: anywhere;
  line-height: 1.65;
}

.evidence-content p {
  margin: 0.35rem 0 0;
}

.evidence-content a {
  display: inline-block;
  margin-top: 0.4rem;
  color: var(--color-accent);
  text-decoration: none;
}

.evidence-content code {
  color: var(--color-heading);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}
</style>
