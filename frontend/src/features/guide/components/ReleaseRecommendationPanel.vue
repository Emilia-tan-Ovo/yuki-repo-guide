<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { suggestRuntimeEnvironment } from '../runtimeSuggestion'
import type {
  ConfirmedRuntime,
  Evidence,
  LinuxPackageFamily,
  ProcessorArchitecture,
  ReleaseAsset,
  ReleaseAssetRole,
  ReleaseSection,
  RuntimeOperatingSystem,
} from '../guideTypes'

const props = defineProps<{
  releases: ReleaseSection
  evidence: Record<string, Evidence>
  busy: boolean
  disabled: boolean
  retryMessage: string
  errorMessage: string
}>()

const emit = defineEmits<{
  confirm: [runtime: ConfirmedRuntime]
}>()

const controlOpen = ref(false)
const suggesting = ref(false)
const operatingSystem = ref<RuntimeOperatingSystem | ''>('')
const architecture = ref<ProcessorArchitecture | ''>('')
const linuxPackageFamily = ref<LinuxPackageFamily | ''>('')
const localMessage = ref('')
const suggestionMessage = ref('')

const directStableAssets = computed(() =>
  props.releases.latestStable?.matchingAssets.filter(
    asset => asset.assessment?.directlyRecommended,
  ) ?? [],
)
const otherStableAssets = computed(() =>
  props.releases.latestStable?.matchingAssets.filter(
    asset => !asset.assessment?.directlyRecommended,
  ) ?? [],
)
const prereleaseAssets = computed(() =>
  props.releases.latestPrerelease?.matchingAssets ?? [],
)
const hasMatches = computed(() =>
  directStableAssets.value.length > 0
  || otherStableAssets.value.length > 0
  || prereleaseAssets.value.length > 0,
)

watch(
  () => props.releases.recommendation,
  recommendation => {
    const runtime = recommendation.runtime
    if (runtime) {
      operatingSystem.value = runtime.operatingSystem
      architecture.value = runtime.architecture
      linuxPackageFamily.value = runtime.linuxPackageFamily ?? ''
    }
    if (recommendation.status === 'NEEDS_LINUX_FAMILY') {
      controlOpen.value = true
    }
    if (recommendation.status === 'READY') {
      controlOpen.value = false
    }
  },
  { immediate: true },
)

async function openRuntimeControl() {
  controlOpen.value = true
  localMessage.value = ''
  if (props.releases.recommendation.status !== 'NOT_REQUESTED') return

  suggesting.value = true
  try {
    const suggestion = await suggestRuntimeEnvironment()
    operatingSystem.value = suggestion.operatingSystem ?? ''
    architecture.value = suggestion.architecture ?? ''
    suggestionMessage.value = suggestion.source === 'NONE'
      ? '浏览器没有提供可靠建议，请手动选择。'
      : suggestion.architecture
        ? '已填入浏览器建议，请确认或修改后再继续。'
        : '浏览器只能辨认操作系统，请手动确认处理器架构。'
  } finally {
    suggesting.value = false
  }
}

function confirmRuntime() {
  if (!operatingSystem.value || !architecture.value) {
    localMessage.value = '请先确认操作系统和处理器架构。'
    return
  }
  localMessage.value = ''
  emit('confirm', {
    operatingSystem: operatingSystem.value,
    architecture: architecture.value,
  })
}

function confirmLinuxFamily() {
  const runtime = props.releases.recommendation.runtime
  if (!runtime || !linuxPackageFamily.value) {
    localMessage.value = '请选择一个 Linux 资源族。'
    return
  }
  localMessage.value = ''
  emit('confirm', {
    operatingSystem: runtime.operatingSystem,
    architecture: runtime.architecture,
    linuxPackageFamily: linuxPackageFamily.value,
  })
}

function continueWithoutRecommendation() {
  controlOpen.value = false
  operatingSystem.value = ''
  architecture.value = ''
  linuxPackageFamily.value = ''
  localMessage.value = '已保留原始资源列表，你可以稍后再确认环境。'
}

function runtimeLabel(runtime?: ConfirmedRuntime | null): string {
  if (!runtime) return ''
  const os = { WINDOWS: 'Windows', MACOS: 'macOS', LINUX: 'Linux' }[runtime.operatingSystem]
  const arch = runtime.architecture === 'X64' ? 'x64' : 'arm64'
  return `${os} · ${arch}`
}

function familyLabel(family: LinuxPackageFamily): string {
  return {
    DEB: 'DEB（Debian、Ubuntu、Linux Mint）',
    RPM: 'RPM（Fedora、RHEL、CentOS、openSUSE）',
    ARCH: 'Arch（Arch Linux、Manjaro）',
    ALPINE: 'Alpine（Alpine Linux）',
    OTHER_OR_UNKNOWN: '其他或不确定',
  }[family]
}

function roleLabel(role: ReleaseAssetRole): string {
  return {
    STANDARD_INSTALLER: '标准安装资源',
    PORTABLE: '便携资源',
    MANUAL_ARCHIVE: '手动压缩资源',
    AUXILIARY: '辅助资源',
    SOURCE: '源码资源',
    UNKNOWN: '用途无法确认',
  }[role]
}

function analysisLabel(asset: ReleaseAsset): string {
  const assessment = asset.assessment
  if (!assessment) return roleLabel(asset.role)
  const os = assessment.detectedOperatingSystem
    ? { WINDOWS: 'Windows', MACOS: 'macOS', LINUX: 'Linux' }[assessment.detectedOperatingSystem]
    : '操作系统未标明'
  const architecture = assessment.detectedArchitecture
    ? { X64: 'x64', ARM64: 'arm64' }[assessment.detectedArchitecture]
    : '架构未标明'
  const family = assessment.detectedLinuxPackageFamily
    ? `；资源族：${familyLabel(assessment.detectedLinuxPackageFamily)}`
    : ''
  const match = {
    MATCHED: '明确匹配',
    POSSIBLY_APPLICABLE: '可能适用',
    NOT_MATCHED: '不匹配',
    UNABLE_TO_CONFIRM: '无法确认',
  }[assessment.matchStatus]
  return `${roleLabel(asset.role)}；识别为 ${os}、${architecture}${family}；${match}`
}

function formatBytes(bytes: number): string {
  if (bytes < 1_024) return `${bytes} B`
  if (bytes < 1_048_576) return `${(bytes / 1_024).toFixed(1)} KB`
  if (bytes < 1_073_741_824) return `${(bytes / 1_048_576).toFixed(1)} MB`
  return `${(bytes / 1_073_741_824).toFixed(1)} GB`
}

function assetKey(asset: ReleaseAsset): string {
  return asset.evidenceId
}
</script>

<template>
  <section class="recommendation-panel" aria-labelledby="recommendation-title">
    <div class="recommendation-heading">
      <div>
        <p class="eyebrow">保守推荐</p>
        <h4 id="recommendation-title">找到适合你环境的下载资源</h4>
      </div>
      <span v-if="releases.recommendation.runtime" class="runtime-badge">
        {{ runtimeLabel(releases.recommendation.runtime) }}
      </span>
    </div>

    <template v-if="releases.recommendation.status === 'NOT_REQUESTED'">
      <p class="intro-copy">
        确认运行环境后，我们会按文件名做保守匹配；浏览器建议只用于预填，不会替你确认。
      </p>
      <button
        v-if="!controlOpen"
        data-testid="open-runtime-control"
        class="primary-button"
        type="button"
        :disabled="busy"
        @click="openRuntimeControl"
      >
        按我的环境筛选
      </button>
    </template>

    <form
      v-if="controlOpen && releases.recommendation.status !== 'NEEDS_LINUX_FAMILY'"
      class="runtime-form"
      @submit.prevent="confirmRuntime"
    >
      <p v-if="suggesting" class="form-hint">正在读取浏览器可提供的建议…</p>
      <p v-else-if="suggestionMessage" class="form-hint">{{ suggestionMessage }}</p>
      <label>
        操作系统
        <select v-model="operatingSystem" name="operatingSystem" :disabled="busy">
          <option value="" disabled>请选择</option>
          <option value="WINDOWS">Windows</option>
          <option value="MACOS">macOS</option>
          <option value="LINUX">Linux</option>
        </select>
      </label>
      <label>
        处理器架构
        <select v-model="architecture" name="architecture" :disabled="busy">
          <option value="" disabled>请选择</option>
          <option value="X64">x64</option>
          <option value="ARM64">arm64</option>
        </select>
      </label>
      <div class="form-actions">
        <button
          data-testid="confirm-runtime"
          class="primary-button"
          type="button"
          :disabled="busy || disabled"
          @click="confirmRuntime"
        >
          {{ busy ? '正在匹配…' : '确认并匹配' }}
        </button>
        <button class="text-button" type="button" :disabled="busy" @click="continueWithoutRecommendation">
          其他或不确定，继续看原始资源
        </button>
      </div>
    </form>

    <form
      v-if="releases.recommendation.status === 'NEEDS_LINUX_FAMILY'"
      class="runtime-form family-form"
      @submit.prevent="confirmLinuxFamily"
    >
      <div>
        <strong>还需要确认 Linux 资源族</strong>
        <p>当前没有明确匹配的通用资源；下面的选择确实会影响推荐结果。</p>
      </div>
      <label>
        Linux 资源族
        <select v-model="linuxPackageFamily" name="linuxPackageFamily" :disabled="busy">
          <option value="" disabled>请选择</option>
          <option
            v-for="family in releases.recommendation.availableLinuxFamilies"
            :key="family"
            :value="family"
          >
            {{ familyLabel(family) }}
          </option>
        </select>
      </label>
      <button
        data-testid="confirm-linux-family"
        class="primary-button"
        type="button"
        :disabled="busy || disabled"
        @click="confirmLinuxFamily"
      >
        {{ busy ? '正在匹配…' : '确认 Linux 资源族' }}
      </button>
    </form>

    <div v-if="releases.recommendation.status === 'READY'" class="recommendation-result">
      <p class="version-warning">该区域的资源可能不是“最新版”；优先采用最新正式 Release。</p>

      <div v-if="hasMatches" class="match-groups">
        <section v-if="directStableAssets.length" class="match-group direct-group">
          <h5>直接推荐</h5>
          <p>来自最新正式版，且处于匹配资源中的最高用途层级。</p>
          <ul>
            <li v-for="asset in directStableAssets" :key="assetKey(asset)">
              <a :href="asset.downloadUrl" target="_blank" rel="noopener noreferrer">
                {{ asset.name }} <span aria-hidden="true">↗</span>
              </a>
              <span>{{ roleLabel(asset.role) }} · {{ formatBytes(asset.sizeBytes) }}</span>
              <details v-if="evidence[asset.evidenceId]">
                <summary>查看匹配证据</summary>
                <p>
                  GitHub Asset ID：<code>{{ evidence[asset.evidenceId].assetId }}</code>；
                  文件名分析：{{ analysisLabel(asset) }}；
                  用户确认环境：{{ runtimeLabel(releases.recommendation.runtime) }}
                </p>
              </details>
            </li>
          </ul>
        </section>

        <section v-if="otherStableAssets.length" class="match-group">
          <h5>正式版其他匹配项</h5>
          <ul>
            <li v-for="asset in otherStableAssets" :key="assetKey(asset)">
              <a :href="asset.downloadUrl" target="_blank" rel="noopener noreferrer">{{ asset.name }}</a>
              <span>{{ roleLabel(asset.role) }} · {{ formatBytes(asset.sizeBytes) }}</span>
              <details v-if="evidence[asset.evidenceId]">
                <summary>查看匹配证据</summary>
                <p>
                  GitHub Asset ID：<code>{{ evidence[asset.evidenceId].assetId }}</code>；
                  文件名分析：{{ analysisLabel(asset) }}；
                  用户确认环境：{{ runtimeLabel(releases.recommendation.runtime) }}
                </p>
              </details>
            </li>
          </ul>
        </section>

        <section v-if="prereleaseAssets.length" class="match-group preview-group">
          <h5>预览版备选</h5>
          <p>这些资源来自预发布版本，稳定性可能较低，不会成为直接推荐。</p>
          <ul>
            <li v-for="asset in prereleaseAssets" :key="assetKey(asset)">
              <a :href="asset.downloadUrl" target="_blank" rel="noopener noreferrer">{{ asset.name }}</a>
              <span>{{ roleLabel(asset.role) }} · {{ formatBytes(asset.sizeBytes) }}</span>
              <details v-if="evidence[asset.evidenceId]">
                <summary>查看匹配证据</summary>
                <p>
                  GitHub Asset ID：<code>{{ evidence[asset.evidenceId].assetId }}</code>；
                  文件名分析：{{ analysisLabel(asset) }}；
                  用户确认环境：{{ runtimeLabel(releases.recommendation.runtime) }}
                </p>
              </details>
            </li>
          </ul>
        </section>
      </div>
      <p v-else class="no-match">没有找到可明确匹配的普通下载资源，请查看下方原始列表。</p>

      <p
        v-if="releases.latestStable?.matchingAssetsTruncated || releases.latestPrerelease?.matchingAssetsTruncated"
        class="version-warning"
      >
        匹配区域最多展示 50 项，其余匹配项已隐藏。
      </p>
      <button class="text-button" type="button" :disabled="busy" @click="openRuntimeControl">
        修改已确认环境
      </button>
    </div>

    <p v-if="localMessage" class="form-message">{{ localMessage }}</p>
    <div v-if="errorMessage" class="request-error" role="alert">
      <p>{{ errorMessage }}</p>
      <p v-if="retryMessage">{{ retryMessage }}</p>
    </div>
    <p class="safety-copy">
      匹配不保证实际兼容；文件未经 RepoGuide 安全认证，页面不会自动下载或执行任何文件。
    </p>
  </section>
</template>

<style scoped>
.recommendation-panel,
.runtime-form,
.recommendation-result,
.match-groups,
.match-group {
  display: grid;
  gap: 0.8rem;
}

.recommendation-panel {
  padding: 1rem;
  border: 1px solid #dac5e2;
  border-radius: 0.95rem;
  background: linear-gradient(145deg, rgba(250, 246, 252, 0.96), rgba(255, 255, 255, 0.82));
}

.recommendation-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.eyebrow,
.intro-copy,
.form-hint,
.family-form p,
.match-group p,
.version-warning,
.no-match,
.form-message,
.request-error p,
.safety-copy {
  margin: 0;
}

.eyebrow {
  color: var(--color-accent);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h4,
h5 { margin: 0; color: var(--color-heading); }
h4 { font-size: 0.98rem; }
h5 { font-size: 0.88rem; }

.runtime-badge {
  padding: 0.3rem 0.55rem;
  border-radius: 999px;
  color: #377358;
  background: #e9f6ef;
  font-size: 0.72rem;
  font-weight: 750;
}

.intro-copy,
.form-hint,
.family-form p,
.match-group p,
.no-match,
.safety-copy {
  color: var(--color-text-muted);
  font-size: 0.78rem;
  line-height: 1.55;
}

.runtime-form {
  padding-top: 0.2rem;
}

.runtime-form label {
  display: grid;
  gap: 0.35rem;
  color: var(--color-heading);
  font-size: 0.78rem;
  font-weight: 700;
}

.runtime-form select {
  min-height: 2.5rem;
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--color-border-strong);
  border-radius: 0.65rem;
  color: var(--color-heading);
  background: white;
  font: inherit;
}

.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
  align-items: center;
}

.primary-button,
.text-button {
  border: 0;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 750;
  cursor: pointer;
}

.primary-button {
  justify-self: start;
  min-height: 2.45rem;
  padding: 0.55rem 0.8rem;
  border-radius: 0.7rem;
  color: white;
  background: var(--color-accent);
}

.text-button {
  padding: 0.35rem 0;
  color: var(--color-accent);
  background: transparent;
  text-decoration: underline;
  text-underline-offset: 0.18em;
}

button:disabled { cursor: wait; opacity: 0.6; }

.version-warning {
  padding: 0.5rem 0.65rem;
  border-radius: 0.6rem;
  color: #7d5b26;
  background: #fff4d9;
  font-size: 0.75rem;
}

.match-group {
  padding: 0.8rem;
  border: 1px solid var(--color-border);
  border-radius: 0.75rem;
  background: rgba(255, 255, 255, 0.72);
}

.direct-group { border-color: #acd6be; background: #f2fbf6; }
.preview-group { border-color: #ead5a8; background: #fffaf0; }

.match-group ul {
  display: grid;
  gap: 0.55rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.match-group li { display: grid; gap: 0.2rem; font-size: 0.79rem; }
.match-group a { overflow-wrap: anywhere; color: var(--color-accent); font-weight: 750; text-decoration: none; }
.match-group li > span { color: var(--color-text-muted); font-size: 0.72rem; }
.match-group details { color: var(--color-text-muted); font-size: 0.72rem; }
.match-group summary { cursor: pointer; font-weight: 700; }
.match-group details p { padding-top: 0.35rem; }

.form-message { color: #526c5b; font-size: 0.76rem; }
.request-error { padding: 0.65rem; border-radius: 0.65rem; color: #7d3443; background: #fff0f3; font-size: 0.76rem; }
.safety-copy { padding-top: 0.65rem; border-top: 1px solid var(--color-border); }

@media (max-width: 520px) {
  .recommendation-heading { align-items: flex-start; flex-direction: column; }
}
</style>
