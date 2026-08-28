<script setup lang="ts">
import { computed } from 'vue'
import type { Evidence, LanguageSection, ReadmeSection, RepositorySummary } from '../guideTypes'
import OnlineExperienceSection from './OnlineExperienceSection.vue'

const props = defineProps<{
  repository: RepositorySummary
  readme: ReadmeSection
  languages: LanguageSection
  evidence: Record<string, Evidence>
  languageRetrying: boolean
  retryDisabled: boolean
  retryMessage: string
  languageErrorMessage: string
  readmeRetrying: boolean
  readmeRetryDisabled: boolean
  readmeRetryMessage: string
  readmeErrorMessage: string
}>()

defineEmits<{
  retryLanguages: []
  retryReadme: []
}>()

const repositoryEvidence = computed(() => props.evidence[props.repository.evidenceId])
const languageEvidence = computed(() => {
  const evidenceId = props.languages.evidenceId
  return evidenceId ? props.evidence[evidenceId] : undefined
})

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function formatPercentage(value: number): string {
  return Number.isInteger(value) ? `${value}%` : `${value.toFixed(1)}%`
}

function barWidth(value: number): string {
  return `${Math.min(100, Math.max(0, value))}%`
}
</script>

<template>
  <article class="repository-card" aria-labelledby="repository-name">
    <div class="card-heading">
      <div class="github-mark" aria-hidden="true">
        <svg viewBox="0 0 24 24">
          <path
            fill="currentColor"
            d="M12 .7a11.5 11.5 0 0 0-3.64 22.41c.58.11.79-.25.79-.56v-2.23c-3.22.7-3.9-1.37-3.9-1.37-.52-1.34-1.28-1.69-1.28-1.69-1.05-.72.08-.71.08-.71 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.57-.29-5.27-1.29-5.27-5.69 0-1.26.45-2.28 1.19-3.09-.12-.29-.52-1.47.11-3.05 0 0 .97-.31 3.16 1.18a10.98 10.98 0 0 1 5.75 0c2.19-1.49 3.15-1.18 3.15-1.18.63 1.58.23 2.76.11 3.05.74.81 1.19 1.83 1.19 3.09 0 4.41-2.71 5.39-5.29 5.68.42.36.79 1.06.79 2.14v3.17c0 .31.21.68.8.56A11.5 11.5 0 0 0 12 .7Z"
          />
        </svg>
      </div>
      <div>
        <p class="eyebrow">GitHub 仓库</p>
        <h2 id="repository-name">{{ repository.name }}</h2>
      </div>
      <span class="status-dot">已确认</span>
    </div>

    <p class="description">
      {{ repository.description || 'GitHub 未提供仓库描述。' }}
    </p>

    <dl class="fact-list">
      <div>
        <dt>所有者</dt>
        <dd>{{ repository.owner }}</dd>
      </div>
      <div>
        <dt>Stars</dt>
        <dd>{{ repository.stars.toLocaleString('zh-CN') }}</dd>
      </div>
      <div>
        <dt>创建时间</dt>
        <dd>{{ formatDate(repository.createdAt) }}</dd>
      </div>
      <div>
        <dt>最近代码更新</dt>
        <dd>{{ repository.pushedAt ? formatDate(repository.pushedAt) : '暂无代码更新记录' }}</dd>
      </div>
    </dl>

    <div class="repository-links">
      <span>来源：GitHub</span>
      <div>
        <a :href="repository.canonicalUrl" target="_blank" rel="noopener noreferrer">
          查看原仓库 <span aria-hidden="true">↗</span>
        </a>
        <a
          v-if="repository.projectWebsiteUrl"
          :href="repository.projectWebsiteUrl"
          target="_blank"
          rel="noopener noreferrer"
        >
          项目主页（元数据，未验证） <span aria-hidden="true">↗</span>
        </a>
      </div>
    </div>

    <details v-if="repositoryEvidence" class="evidence-panel">
      <summary>查看仓库证据</summary>
      <div class="evidence-content">
        <p>来源：{{ repositoryEvidence.source }}</p>
        <p v-if="repositoryEvidence.recentCodeUpdate">
          原始字段：<code>{{ repositoryEvidence.recentCodeUpdate.field }}</code><br />
          原始值：<code>{{ repositoryEvidence.recentCodeUpdate.value }}</code>
        </p>
      </div>
    </details>

    <OnlineExperienceSection
      :readme="readme"
      :evidence="evidence"
      :retrying="readmeRetrying"
      :retry-disabled="readmeRetryDisabled"
      :retry-message="readmeRetryMessage"
      :error-message="readmeErrorMessage"
      @retry="$emit('retryReadme')"
    />

    <section class="language-section" aria-labelledby="language-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow">仓库事实</p>
          <h3 id="language-title">语言分布</h3>
        </div>
        <span v-if="languages.status === 'AVAILABLE'" class="section-status">来自 GitHub</span>
      </div>

      <div v-if="languages.status === 'AVAILABLE'" class="language-list">
        <div v-for="language in languages.items" :key="language.name" class="language-row">
          <div class="language-label">
            <strong>{{ language.name }}</strong>
            <span>{{ formatPercentage(language.percentage) }}</span>
          </div>
          <div class="language-track" aria-hidden="true">
            <span :style="{ width: barWidth(language.percentage) }"></span>
          </div>
        </div>
      </div>

      <p v-else-if="languages.status === 'NOT_PROVIDED'" class="language-state">
        GitHub 暂未提供这个仓库的语言统计。
      </p>

      <div v-else class="language-failure" role="status">
        <p>{{ languageErrorMessage || '语言区域加载失败，请重新尝试。' }}</p>
        <p class="retry-hint">{{ retryMessage }}</p>
        <button
          type="button"
          :disabled="retryDisabled"
          @click="$emit('retryLanguages')"
        >
          {{ languageRetrying ? '正在重试…' : '重试语言区域' }}
        </button>
      </div>

      <details v-if="languageEvidence" class="evidence-panel">
        <summary>查看语言证据</summary>
        <div class="evidence-content">
          <p>来源：{{ languageEvidence.source }}</p>
          <p>总字节数：{{ languageEvidence.totalBytes?.toLocaleString('zh-CN') }}</p>
          <ul>
            <li v-for="language in languageEvidence.languages" :key="language.name">
              {{ language.name }} 字节数：{{ language.bytes.toLocaleString('zh-CN') }}；
              占比：{{ formatPercentage(language.percentage) }}
            </li>
          </ul>
        </div>
      </details>
    </section>
  </article>
</template>

<style scoped>
.repository-card {
  display: grid;
  gap: 1.25rem;
  padding: 1.35rem;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.card-heading {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 0.85rem;
  align-items: center;
}

.github-mark {
  display: grid;
  width: 2.75rem;
  height: 2.75rem;
  place-items: center;
  border-radius: 0.9rem;
  color: #302b37;
  background: #f2eef6;
}

.github-mark svg {
  width: 1.5rem;
  height: 1.5rem;
}

.eyebrow {
  margin: 0 0 0.15rem;
  color: var(--color-accent);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h2 {
  overflow: hidden;
  margin: 0;
  color: var(--color-heading);
  font-size: clamp(1.2rem, 3vw, 1.55rem);
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

h3 {
  margin: 0;
  color: var(--color-heading);
  font-size: 1.05rem;
}

.status-dot {
  padding: 0.35rem 0.65rem;
  border-radius: 999px;
  color: #377358;
  background: #e9f6ef;
  font-size: 0.75rem;
  font-weight: 750;
}

dl {
  display: grid;
  gap: 0.85rem;
  margin: 0;
}

.description {
  margin: 0;
  color: var(--color-text);
  line-height: 1.65;
}

dl > div {
  display: grid;
  grid-template-columns: 5rem minmax(0, 1fr);
  gap: 0.75rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--color-border);
}

dt {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

dd {
  min-width: 0;
  margin: 0;
  color: var(--color-heading);
  font-weight: 650;
}

a {
  display: inline-block;
  max-width: 100%;
  overflow-wrap: anywhere;
  color: var(--color-accent);
  text-decoration: none;
}

a:hover {
  text-decoration: underline;
  text-underline-offset: 0.2em;
}

.repository-links,
.section-heading,
.language-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.repository-links {
  color: var(--color-text-muted);
  font-size: 0.84rem;
}

.repository-links > div {
  display: grid;
  gap: 0.35rem;
  justify-items: end;
  text-align: right;
}

.language-section {
  display: grid;
  gap: 1rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--color-border);
}

.section-status {
  padding: 0.3rem 0.55rem;
  border-radius: 999px;
  color: var(--color-accent);
  background: var(--color-accent-wash);
  font-size: 0.72rem;
  font-weight: 700;
}

.language-list {
  display: grid;
  gap: 0.85rem;
}

.language-row {
  display: grid;
  gap: 0.4rem;
}

.language-label {
  color: var(--color-heading);
  font-size: 0.86rem;
}

.language-label span {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.language-track {
  height: 0.45rem;
  overflow: hidden;
  border-radius: 999px;
  background: #eee8f0;
}

.language-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--color-accent), #b68ac5);
}

.language-state,
.language-failure {
  margin: 0;
  padding: 0.9rem 1rem;
  border-radius: 0.85rem;
  background: var(--color-accent-wash);
  font-size: 0.86rem;
  line-height: 1.6;
}

.language-failure {
  color: #7d3443;
  background: #fff7f8;
}

.language-failure p {
  margin: 0;
}

.retry-hint {
  color: #996070;
  font-size: 0.78rem;
}

.language-failure button {
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

.language-failure button:disabled {
  cursor: wait;
  opacity: 0.6;
}

.evidence-panel {
  border: 1px solid var(--color-border);
  border-radius: 0.85rem;
  color: var(--color-text-muted);
  background: rgba(247, 242, 250, 0.55);
  font-size: 0.8rem;
}

.evidence-panel summary {
  padding: 0.75rem 0.9rem;
  color: var(--color-text);
  font-weight: 700;
  cursor: pointer;
}

.evidence-content {
  padding: 0 0.9rem 0.8rem;
  overflow-wrap: anywhere;
  line-height: 1.65;
}

.evidence-content p,
.evidence-content ul {
  margin: 0.4rem 0 0;
}

.evidence-content ul {
  padding-left: 1.15rem;
}

.evidence-content code {
  color: var(--color-heading);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}

@media (max-width: 520px) {
  .status-dot {
    display: none;
  }

  .card-heading {
    grid-template-columns: auto minmax(0, 1fr);
  }

  dl > div {
    grid-template-columns: 1fr;
    gap: 0.25rem;
  }

  .repository-links {
    align-items: flex-start;
    flex-direction: column;
  }

  .repository-links > div {
    justify-items: start;
    text-align: left;
  }
}
</style>
