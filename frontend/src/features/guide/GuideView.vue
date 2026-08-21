<script setup lang="ts">
import { ref, shallowRef } from 'vue'
import { createGuide, GuideApiError, GuideAuthenticationRequiredError } from './guideApi'
import type { GuideResponse, GuideStatus } from './guideTypes'
import RepositoryIdentityCard from './components/RepositoryIdentityCard.vue'
import RepositoryUrlForm from './components/RepositoryUrlForm.vue'

defineProps<{
  loggingOut: boolean
}>()

const emit = defineEmits<{
  authenticationRequired: [retry: () => Promise<void>]
  logout: []
}>()

const status = ref<GuideStatus>('idle')
const guide = shallowRef<GuideResponse | null>(null)
const errorMessage = ref('')

async function submitGuide(repositoryUrl: string, allowAuthenticationRecovery = true) {
  status.value = 'submitting'
  guide.value = null
  errorMessage.value = ''

  try {
    guide.value = await createGuide(repositoryUrl)
    status.value = 'success'
  } catch (error) {
    if (error instanceof GuideAuthenticationRequiredError && allowAuthenticationRecovery) {
      status.value = 'idle'
      emit('authenticationRequired', () => submitGuide(repositoryUrl, false))
      return
    }
    errorMessage.value = error instanceof GuideApiError
      ? error.message
      : '发生了意料之外的错误，请稍后重试。'
    status.value = 'error'
  }
}
</script>

<template>
  <main class="guide-page">
    <section class="hero" aria-labelledby="page-title">
      <div class="hero-toolbar">
        <p class="product-tag"><span aria-hidden="true">✦</span> Yuki Repo Guide · V0</p>
        <button class="logout-button" type="button" :disabled="loggingOut" @click="emit('logout')">
          {{ loggingOut ? '正在退出…' : '退出试用' }}
        </button>
      </div>
      <h1 id="page-title">从一个地址开始，<br /><em>轻松认识 GitHub 项目。</em></h1>
      <p class="intro">
        粘贴仓库链接，我们先解析出规范的仓库引用。后续导览会从体验入口、技术栈到版本发布，一步步陪你逛明白。
      </p>
    </section>

    <section class="workspace" aria-label="仓库导览入口">
      <RepositoryUrlForm :submitting="status === 'submitting'" @submit="submitGuide" />

      <Transition name="result" mode="out-in">
        <RepositoryIdentityCard
          v-if="status === 'success' && guide"
          :repository="guide.repository"
        />
        <div v-else-if="status === 'error'" class="error-message" role="alert">
          <span aria-hidden="true">!</span>
          <div>
            <strong>这次没有识别成功</strong>
            <p>{{ errorMessage }}</p>
          </div>
        </div>
        <div v-else class="empty-state">
          <span aria-hidden="true">⌁</span>
          <p>导览结果会安静地出现在这里。</p>
        </div>
      </Transition>
    </section>

    <footer>
      <span>先逛起来，再决定是否深入。</span>
      <span aria-hidden="true">·</span>
      <span>当前不会请求 GitHub</span>
    </footer>
  </main>
</template>

<style scoped>
.guide-page {
  position: relative;
  display: grid;
  width: min(100% - 2rem, 58rem);
  min-height: 100vh;
  margin: 0 auto;
  padding: clamp(3.5rem, 10vh, 7rem) 0 2rem;
  align-content: start;
}

.guide-page::before,
.guide-page::after {
  position: fixed;
  z-index: -1;
  border-radius: 50%;
  content: '';
  filter: blur(4px);
  pointer-events: none;
}

.guide-page::before {
  top: -10rem;
  left: -8rem;
  width: 28rem;
  height: 28rem;
  background: rgba(223, 207, 239, 0.48);
}

.guide-page::after {
  right: -9rem;
  bottom: -12rem;
  width: 30rem;
  height: 30rem;
  background: rgba(249, 219, 229, 0.42);
}

.hero {
  max-width: 48rem;
  margin-bottom: 2.25rem;
}

.hero-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.25rem;
}

.product-tag {
  display: inline-flex;
  gap: 0.45rem;
  align-items: center;
  margin: 0;
  padding: 0.4rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-accent);
  background: rgba(255, 255, 255, 0.62);
  font-size: 0.73rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.logout-button {
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--color-border-strong);
  border-radius: 999px;
  color: var(--color-text);
  background: rgba(255, 255, 255, 0.72);
  font: inherit;
  font-size: 0.78rem;
  font-weight: 700;
  cursor: pointer;
}

.logout-button:disabled {
  cursor: wait;
  opacity: 0.65;
}

h1 {
  margin: 0;
  color: var(--color-heading);
  font-family: var(--font-serif);
  font-size: clamp(2.65rem, 7vw, 5.25rem);
  font-weight: 650;
  letter-spacing: -0.055em;
  line-height: 1.02;
}

h1 em {
  color: var(--color-accent);
  font-style: normal;
}

.intro {
  max-width: 43rem;
  margin: 1.35rem 0 0;
  color: var(--color-text);
  font-size: clamp(0.98rem, 2vw, 1.08rem);
  line-height: 1.8;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(18rem, 0.92fr);
  gap: 1rem;
  align-items: start;
}

.empty-state,
.error-message {
  min-height: 9.5rem;
  border: 1px dashed var(--color-border-strong);
  border-radius: 1.25rem;
}

.empty-state {
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 0.35rem;
  color: var(--color-text-muted);
  background: rgba(255, 255, 255, 0.36);
}

.empty-state span {
  color: #b8aec2;
  font-size: 1.8rem;
}

.empty-state p,
.error-message p {
  margin: 0;
}

.error-message {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.8rem;
  align-items: start;
  min-height: auto;
  padding: 1.2rem;
  border-style: solid;
  border-color: #efced5;
  color: #7d3443;
  background: #fff7f8;
}

.error-message > span {
  display: grid;
  width: 1.65rem;
  height: 1.65rem;
  place-items: center;
  border-radius: 50%;
  color: white;
  background: #b95469;
  font-weight: 800;
}

.error-message strong {
  display: block;
  margin-bottom: 0.25rem;
}

.error-message p {
  font-size: 0.88rem;
  line-height: 1.55;
}

footer {
  display: flex;
  gap: 0.55rem;
  margin-top: auto;
  padding-top: 3.5rem;
  justify-content: center;
  color: var(--color-text-muted);
  font-size: 0.78rem;
}

.result-enter-active,
.result-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.result-enter-from,
.result-leave-to {
  opacity: 0;
  transform: translateY(5px);
}

@media (max-width: 760px) {
  .guide-page {
    padding-top: 3.5rem;
  }

  .workspace {
    grid-template-columns: 1fr;
  }

  footer {
    flex-wrap: wrap;
    padding-top: 2.5rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .result-enter-active,
  .result-leave-active {
    transition: none;
  }
}
</style>
