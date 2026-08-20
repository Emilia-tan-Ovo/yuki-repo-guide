<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  submitting: boolean
}>()

const emit = defineEmits<{
  submit: [repositoryUrl: string]
}>()

const repositoryUrl = ref('')

function submitRepository() {
  emit('submit', repositoryUrl.value.trim())
}
</script>

<template>
  <form class="guide-form" @submit.prevent="submitRepository">
    <label for="repository-url">GitHub 仓库地址</label>
    <div class="input-row">
      <input
        id="repository-url"
        v-model="repositoryUrl"
        name="repositoryUrl"
        type="url"
        inputmode="url"
        autocomplete="url"
        placeholder="https://github.com/owner/repository"
        required
        :disabled="submitting"
        aria-describedby="repository-url-hint"
      />
      <button type="submit" :disabled="submitting">
        <span v-if="submitting" class="spinner" aria-hidden="true"></span>
        {{ submitting ? '正在识别…' : '开始导览' }}
      </button>
    </div>
    <p id="repository-url-hint" class="hint">
      当前支持公开的 github.com 仓库；不会保存你输入的地址。
    </p>
  </form>
</template>

<style scoped>
.guide-form {
  display: grid;
  gap: 0.75rem;
  padding: 1.25rem;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(16px);
}

label {
  color: var(--color-heading);
  font-size: 0.9rem;
  font-weight: 700;
}

.input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.75rem;
}

input {
  min-width: 0;
  padding: 0.9rem 1rem;
  border: 1px solid var(--color-border-strong);
  border-radius: 0.85rem;
  outline: none;
  color: var(--color-heading);
  background: var(--color-surface);
  font: inherit;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

input::placeholder {
  color: #a39cac;
}

input:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 4px var(--color-accent-soft);
}

button {
  display: inline-flex;
  min-width: 8rem;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.9rem 1.15rem;
  border: 0;
  border-radius: 0.85rem;
  color: white;
  background: var(--color-accent);
  box-shadow: 0 8px 20px rgba(112, 78, 155, 0.22);
  font: inherit;
  font-weight: 750;
  cursor: pointer;
  transition: transform 160ms ease, background 160ms ease, opacity 160ms ease;
}

button:hover:not(:disabled) {
  transform: translateY(-1px);
  background: var(--color-accent-hover);
}

button:focus-visible {
  outline: 3px solid var(--color-accent-soft);
  outline-offset: 3px;
}

button:disabled,
input:disabled {
  cursor: wait;
  opacity: 0.72;
}

.hint {
  color: var(--color-text-muted);
  font-size: 0.82rem;
}

.spinner {
  width: 0.85rem;
  height: 0.85rem;
  border: 2px solid rgba(255, 255, 255, 0.45);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 700ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 620px) {
  .input-row {
    grid-template-columns: 1fr;
  }

  button {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  button,
  input {
    transition: none;
  }
}
</style>
