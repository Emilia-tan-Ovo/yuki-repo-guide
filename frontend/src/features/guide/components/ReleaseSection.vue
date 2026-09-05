<script setup lang="ts">
import type { Evidence, ReleaseSection, ReleaseSummary } from '../guideTypes'

defineProps<{
  releases: ReleaseSection
  evidence: Record<string, Evidence>
  retrying: boolean
  retryDisabled: boolean
  retryMessage: string
  errorMessage: string
}>()

defineEmits<{
  retry: []
}>()

function releaseTitle(release: ReleaseSummary): string {
  return release.name?.trim() || release.tagName
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function formatBytes(bytes: number): string {
  if (bytes < 1_024) return `${bytes} B`
  if (bytes < 1_048_576) return `${(bytes / 1_024).toFixed(1)} KB`
  if (bytes < 1_073_741_824) return `${(bytes / 1_048_576).toFixed(1)} MB`
  return `${(bytes / 1_073_741_824).toFixed(1)} GB`
}
</script>

<template>
  <section class="release-section" aria-labelledby="release-title">
    <div class="section-heading">
      <div>
        <p class="eyebrow">GitHub Release</p>
        <h3 id="release-title">最新发布版本</h3>
      </div>
      <span v-if="releases.status === 'AVAILABLE'" class="section-status">来自 GitHub</span>
    </div>

    <div v-if="releases.status === 'AVAILABLE'" class="release-list">
      <article
        v-for="entry in [
          { label: '最新正式版', release: releases.latestStable },
          { label: '最新预览版', release: releases.latestPrerelease },
        ]"
        v-show="entry.release"
        :key="entry.label"
        class="release-card"
      >
        <template v-if="entry.release">
          <div class="release-heading">
            <div>
              <p class="channel-label">{{ entry.label }}</p>
              <h4>{{ releaseTitle(entry.release) }}</h4>
            </div>
            <code>{{ entry.release.tagName }}</code>
          </div>
          <p class="published-at">发布时间：{{ formatDate(entry.release.publishedAt) }}</p>

          <p v-if="entry.release.warnings.includes('PRERELEASE')" class="warning">
            这是预览版本，稳定性可能低于正式版。
          </p>
          <p v-if="entry.release.excludedAssetCount > 0" class="warning">
            {{ entry.release.excludedAssetCount }} 个格式异常或不安全的资源已排除。
          </p>
          <p v-if="entry.release.assetsTruncated" class="warning">
            此版本共有 {{ entry.release.reportedAssetCount }} 个资源，仅按名称展示前 50 个。
          </p>

          <ul v-if="entry.release.assets.length" class="asset-list">
            <li v-for="asset in entry.release.assets" :key="asset.evidenceId">
              <div>
                <a :href="asset.downloadUrl" target="_blank" rel="noopener noreferrer">
                  {{ asset.name }} <span aria-hidden="true">↗</span>
                </a>
                <span>{{ formatBytes(asset.sizeBytes) }}</span>
              </div>
              <details v-if="evidence[asset.evidenceId]" class="asset-evidence">
                <summary>资源证据</summary>
                <p>
                  来源：{{ evidence[asset.evidenceId].source }}；
                  GitHub Asset ID：<code>{{ evidence[asset.evidenceId].assetId }}</code>；
                  原始大小：{{ evidence[asset.evidenceId].sizeBytes?.toLocaleString('zh-CN') }} B
                </p>
                <p>
                  关联 Release 证据：<code>{{ evidence[asset.evidenceId].releaseEvidenceId }}</code>
                </p>
              </details>
            </li>
          </ul>
          <p v-else class="empty-assets">此版本没有可展示的下载资源。</p>

          <details v-if="evidence[entry.release.evidenceId]" class="release-evidence">
            <summary>查看 Release 证据</summary>
            <div>
              <p>来源：{{ evidence[entry.release.evidenceId].source }}</p>
              <p>Release ID：<code>{{ evidence[entry.release.evidenceId].releaseId }}</code></p>
              <a
                v-if="evidence[entry.release.evidenceId].releaseUrl"
                :href="evidence[entry.release.evidenceId].releaseUrl || undefined"
                target="_blank"
                rel="noopener noreferrer"
              >
                在 GitHub 查看此 Release <span aria-hidden="true">↗</span>
              </a>
            </div>
          </details>
        </template>
      </article>

      <p v-if="!releases.latestStable && !releases.latestPrerelease" class="release-state">
        GitHub 暂未提供可展示的正式版或预览版。
      </p>
      <p class="safety-note">下载文件来自仓库发布者，未经 RepoGuide 安全认证。</p>
    </div>

    <p v-else-if="releases.status === 'NOT_PROVIDED'" class="release-state">
      这个仓库还没有公开的 Release。
    </p>

    <div v-else class="release-failure" role="status">
      <p>{{ errorMessage || 'Release 区域加载失败，请重新尝试。' }}</p>
      <template v-if="releases.failure?.retryable">
        <p class="retry-hint">{{ retryMessage }}</p>
        <button type="button" :disabled="retryDisabled" @click="$emit('retry')">
          {{ retrying ? '正在重试…' : '重试 Release 区域' }}
        </button>
      </template>
    </div>
  </section>
</template>

<style scoped>
.release-section,
.release-list,
.release-card {
  display: grid;
  gap: 1rem;
}

.release-section {
  padding-top: 1.25rem;
  border-top: 1px solid var(--color-border);
}

.section-heading,
.release-heading,
.asset-list li > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.eyebrow,
.channel-label {
  margin: 0 0 0.15rem;
  color: var(--color-accent);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h3,
h4,
.published-at,
.warning,
.empty-assets,
.release-state,
.release-failure p,
.safety-note,
.release-evidence p,
.asset-evidence p {
  margin: 0;
}

h3 { color: var(--color-heading); font-size: 1.05rem; }
h4 { color: var(--color-heading); font-size: 1rem; }

.section-status,
.release-heading > code {
  padding: 0.3rem 0.55rem;
  border-radius: 999px;
  color: var(--color-accent);
  background: var(--color-accent-wash);
  font-size: 0.72rem;
  font-weight: 700;
}

.release-card {
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-radius: 0.9rem;
  background: rgba(255, 255, 255, 0.66);
}

.published-at,
.empty-assets,
.safety-note {
  color: var(--color-text-muted);
  font-size: 0.78rem;
}

.warning {
  padding: 0.55rem 0.7rem;
  border-radius: 0.65rem;
  color: #7d5b26;
  background: #fff4d9;
  font-size: 0.76rem;
}

.asset-list {
  display: grid;
  gap: 0.65rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.asset-list li {
  padding-top: 0.65rem;
  border-top: 1px solid var(--color-border);
  font-size: 0.82rem;
}

.asset-list a,
.release-evidence a {
  max-width: 75%;
  overflow-wrap: anywhere;
  color: var(--color-accent);
  font-weight: 700;
  text-decoration: none;
}

.asset-list span { color: var(--color-text-muted); }

.release-evidence,
.asset-evidence {
  color: var(--color-text-muted);
  font-size: 0.76rem;
}

.release-evidence summary,
.asset-evidence summary { cursor: pointer; font-weight: 700; }
.release-evidence div { padding-top: 0.45rem; line-height: 1.65; }
.asset-evidence { margin-top: 0.35rem; }

.release-state,
.release-failure {
  padding: 0.9rem 1rem;
  border-radius: 0.85rem;
  background: var(--color-accent-wash);
  font-size: 0.86rem;
  line-height: 1.6;
}

.release-failure { color: #7d3443; background: #fff7f8; }
.retry-hint { color: #996070; font-size: 0.78rem; }

.release-failure button {
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

.release-failure button:disabled { cursor: wait; opacity: 0.6; }

@media (max-width: 520px) {
  .release-heading,
  .asset-list li > div { align-items: flex-start; flex-direction: column; }
  .asset-list a { max-width: 100%; }
}
</style>
