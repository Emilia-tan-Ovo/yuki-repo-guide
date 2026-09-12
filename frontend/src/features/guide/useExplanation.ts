import { computed, onBeforeUnmount, ref, shallowRef, type Ref } from 'vue'
import { GuideApiError, GuideAuthenticationRequiredError, requestExplanation } from './guideApi'
import { createRetryDeadline, retryAvailability } from './languageRetry'
import type { ExplanationResult, ExplanationState } from './explanationTypes'

export function useExplanation(now: Ref<number>, authenticate: (retry: () => Promise<void>) => void) {
  const state = ref<ExplanationState>('idle')
  const result = shallowRef<ExplanationResult | null>(null)
  const retryAt = ref<number | null>(null)
  const retryState = computed(() => retryAvailability(retryAt.value, now.value))
  const disabled = computed(() => state.value === 'loading' || retryState.value.disabled)
  let version = 0
  let inputId: string | null = null

  function reset(expired = false) {
    version++
    inputId = null
    result.value = null
    retryAt.value = null
    state.value = expired ? 'expired' : 'idle'
  }
  async function load(id?: string | null) {
    reset()
    inputId = id ?? null
    if (!inputId) { state.value = 'expired'; return }
    await retry()
  }
  async function retry(allowAuthenticationRecovery = true, expectedVersion = version): Promise<void> {
    if (!inputId || disabled.value || expectedVersion !== version) return
    const requestedId = inputId
    state.value = 'loading'
    result.value = null
    try {
      const response = await requestExplanation(requestedId)
      if (expectedVersion !== version) return
      result.value = response
      state.value = response.status === 'AVAILABLE' ? 'available'
        : response.status === 'INSUFFICIENT_EVIDENCE' ? 'insufficient' : 'unavailable'
      retryAt.value = createRetryDeadline(response.retryAfterSeconds, Date.now())
      now.value = Date.now()
    } catch (error) {
      if (expectedVersion !== version) return
      state.value = 'unavailable'
      if (error instanceof GuideAuthenticationRequiredError && allowAuthenticationRecovery) {
        authenticate(() => retry(false, expectedVersion))
      } else if (error instanceof GuideApiError) {
        if (error.code === 'EXPLANATION_INPUT_EXPIRED' || error.code === 'INVALID_EXPLANATION_INPUT') reset(true)
        else if (error.code === 'EXPLANATION_IN_PROGRESS') state.value = 'in-progress'
        else {
          retryAt.value = createRetryDeadline(error.retryAfterSeconds, Date.now())
          now.value = Date.now()
        }
      }
    }
  }
  onBeforeUnmount(() => reset())
  return { state, result, disabled, retryState, load, retry, reset }
}
