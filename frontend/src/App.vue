<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { AuthApiError, initializeAuthentication, login, logout } from './features/auth/authApi'
import { PendingActionSlot } from './features/auth/pendingAction'
import type { PendingAction } from './features/auth/pendingAction'
import type { AuthenticationState } from './features/auth/authTypes'
import TrialAccessPanel from './features/auth/TrialAccessPanel.vue'
import GuideView from './features/guide/GuideView.vue'

const authenticationState = ref<AuthenticationState>('initializing')
const authSubmitting = ref(false)
const authErrorMessage = ref('')
const pendingAction = new PendingActionSlot()

onMounted(loadAuthenticationState)

async function loadAuthenticationState() {
  authenticationState.value = 'initializing'
  authErrorMessage.value = ''
  try {
    const session = await initializeAuthentication()
    authenticationState.value = session.authenticated ? 'authenticated' : 'anonymous'
  } catch (error) {
    authenticationState.value = 'anonymous'
    authErrorMessage.value = messageFor(error, '暂时无法初始化安全会话，请稍后重试。')
  }
}

async function submitAccessCode(accessCode: string) {
  authSubmitting.value = true
  authErrorMessage.value = ''
  try {
    await login(accessCode)
    authenticationState.value = 'authenticated'

    const action = pendingAction.take()
    if (action) {
      await nextTick()
      await action()
    }
  } catch (error) {
    authErrorMessage.value = messageFor(error, '访问码验证失败，请稍后重试。')
  } finally {
    authSubmitting.value = false
  }
}

function requireAuthentication(action: PendingAction) {
  pendingAction.remember(action)
  authErrorMessage.value = '登录状态已过期，请重新输入试用访问码。'
  authenticationState.value = 'anonymous'
}

async function endSession() {
  authSubmitting.value = true
  try {
    await logout()
    pendingAction.clear()
    authErrorMessage.value = ''
    authenticationState.value = 'anonymous'
  } catch (error) {
    authErrorMessage.value = messageFor(error, '退出失败，请稍后重试。')
  } finally {
    authSubmitting.value = false
  }
}

function messageFor(error: unknown, fallback: string): string {
  return error instanceof AuthApiError ? error.message : fallback
}
</script>

<template>
  <TrialAccessPanel
    v-show="authenticationState !== 'authenticated'"
    :initializing="authenticationState === 'initializing'"
    :submitting="authSubmitting"
    :error-message="authErrorMessage"
    @submit="submitAccessCode"
    @retry="loadAuthenticationState"
  />
  <GuideView
    v-show="authenticationState === 'authenticated'"
    :logging-out="authSubmitting"
    @authentication-required="requireAuthentication"
    @logout="endSession"
  />
</template>
