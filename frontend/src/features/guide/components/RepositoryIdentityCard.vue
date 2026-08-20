<script setup lang="ts">
import type { RepositorySummary } from '../guideTypes'

defineProps<{
  repository: RepositorySummary
}>()
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
        <p class="eyebrow">地址已解析</p>
        <h2 id="repository-name">{{ repository.name }}</h2>
      </div>
      <span class="status-dot">格式有效</span>
    </div>

    <dl>
      <div>
        <dt>所有者</dt>
        <dd>{{ repository.owner }}</dd>
      </div>
      <div>
        <dt>标准地址</dt>
        <dd>
          <a :href="repository.canonicalUrl" target="_blank" rel="noreferrer">
            {{ repository.canonicalUrl }}
            <span aria-hidden="true">↗</span>
          </a>
        </dd>
      </div>
    </dl>

    <p class="next-note">
      规范化仓库引用已经生成；仓库是否存在及其公开内容将在后续 Ticket 中验证。
    </p>
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

.next-note {
  margin: 0;
  padding: 0.85rem 1rem;
  border-radius: 0.85rem;
  color: var(--color-text);
  background: var(--color-accent-wash);
  font-size: 0.86rem;
  line-height: 1.65;
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
}
</style>
