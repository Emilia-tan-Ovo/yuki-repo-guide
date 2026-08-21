<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  initializing: boolean
  submitting: boolean
  errorMessage: string
}>()

const emit = defineEmits<{
  submit: [accessCode: string]
  retry: []
}>()

const accessCode = ref('')

function submitAccessCode() {
  const submittedAccessCode = accessCode.value
  accessCode.value = ''
  emit('submit', submittedAccessCode)
}
</script>

<template>
  <main class="access-page">
    <section class="access-card" aria-labelledby="access-title">
      <p class="product-tag"><span aria-hidden="true">✦</span> Yuki Repo Guide · V0</p>
      <div v-if="initializing" class="loading-state" role="status">
        <span class="spinner" aria-hidden="true"></span>
        <p>正在确认试用资格…</p>
      </div>
      <template v-else>
        <p class="eyebrow">小范围试用</p>
        <h1 id="access-title">输入试用访问码</h1>
        <p class="intro">访问码只用于取得本次试用资格，不会创建用户账号或个人资料。</p>

        <form @submit.prevent="submitAccessCode">
          <label for="trial-access-code">试用访问码</label>
          <input
            id="trial-access-code"
            v-model="accessCode"
            name="accessCode"
            type="password"
            autocomplete="current-password"
            required
            :disabled="submitting"
          />
          <button type="submit" :disabled="submitting">
            <span v-if="submitting" class="spinner" aria-hidden="true"></span>
            {{ submitting ? '正在验证…' : '进入项目导览' }}
          </button>
        </form>

        <div v-if="errorMessage" class="error-message" role="alert">
          <p>{{ errorMessage }}</p>
          <button v-if="errorMessage.includes('连接')" class="retry-button" type="button" @click="emit('retry')">
            重新连接
          </button>
        </div>
      </template>
    </section>
  </main>
</template>

<style scoped>
.access-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 2rem 1rem;
}

.access-card {
  width: min(100%, 30rem);
  padding: clamp(1.5rem, 5vw, 2.4rem);
  border: 1px solid var(--color-border);
  border-radius: 1.5rem;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(16px);
}

.product-tag {
  display: inline-flex;
  gap: 0.45rem;
  align-items: center;
  margin: 0 0 2rem;
  color: var(--color-accent);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.eyebrow {
  margin: 0 0 0.5rem;
  color: var(--color-accent);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.08em;
}

h1 {
  margin: 0;
  color: var(--color-heading);
  font-family: var(--font-serif);
  font-size: clamp(2rem, 8vw, 3.2rem);
  letter-spacing: -0.04em;
}

.intro {
  margin: 0.9rem 0 1.5rem;
  line-height: 1.7;
}

form {
  display: grid;
  gap: 0.75rem;
}

label {
  color: var(--color-heading);
  font-size: 0.9rem;
  font-weight: 700;
}

input,
button {
  min-height: 3rem;
  border-radius: 0.85rem;
  font: inherit;
}

input {
  padding: 0.75rem 0.9rem;
  border: 1px solid var(--color-border-strong);
  outline: none;
}

input:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 4px var(--color-accent-soft);
}

button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border: 0;
  color: white;
  background: var(--color-accent);
  font-weight: 750;
  cursor: pointer;
}

button:disabled {
  cursor: wait;
  opacity: 0.7;
}

.loading-state {
  display: grid;
  min-height: 12rem;
  place-items: center;
  align-content: center;
  gap: 0.8rem;
}

.loading-state p,
.error-message p {
  margin: 0;
}

.error-message {
  margin-top: 1rem;
  padding: 0.85rem 1rem;
  border: 1px solid #efced5;
  border-radius: 0.85rem;
  color: #7d3443;
  background: #fff7f8;
  font-size: 0.88rem;
  line-height: 1.55;
}

.retry-button {
  min-height: auto;
  margin-top: 0.65rem;
  padding: 0;
  color: #7d3443;
  background: transparent;
  text-decoration: underline;
  text-underline-offset: 0.2em;
}

.spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid rgba(116, 83, 154, 0.24);
  border-top-color: currentColor;
  border-radius: 50%;
  animation: spin 700ms linear infinite;
}

button .spinner {
  border-color: rgba(255, 255, 255, 0.45);
  border-top-color: white;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .spinner { animation: none; }
}
</style>
