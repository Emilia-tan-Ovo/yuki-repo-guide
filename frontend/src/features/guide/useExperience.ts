import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { GuideApiError, GuideAuthenticationRequiredError, requestExperience } from './guideApi'
import type { ExperienceRequest, ExperienceResult, ExperienceState } from './experienceTypes'

export function useExperience(source: () => ExperienceRequest | null, authenticate: (retry: () => Promise<void>) => void, regionState: () => string = () => '') {
  const result = shallowRef<ExperienceResult | null>(null)
  const state = ref<ExperienceState>('idle')
  const message = ref('')
  let version = 0
  let guideId: string | null = null
  let active = true

  const fingerprintOf = () => JSON.stringify([source(), regionState()])

  async function load(allowRecovery = true, expectedVersion?: number): Promise<void> {
    if (!active || (expectedVersion !== undefined && expectedVersion !== version)) return
    const request = source()
    const current = ++version
    const fingerprint = fingerprintOf()
    if (request?.guideId !== guideId) result.value = null
    guideId = request?.guideId ?? null
    message.value = ''
    if (!request) { state.value = 'idle'; return }
    state.value = 'loading'
    const isCurrent = () => active && current === version && fingerprint === fingerprintOf()
    try {
      const response = await requestExperience(request)
      if (!isCurrent()) return
      result.value = response
      state.value = 'available'
    } catch (error) {
      if (!isCurrent()) return
      state.value = 'error'
      message.value = '推荐暂时无法更新，其他导览内容仍可浏览。'
      if (error instanceof GuideAuthenticationRequiredError && allowRecovery) {
        authenticate(() => load(false, current))
      } else if (error instanceof GuideApiError && (error.status === 410 || error.status === 409 || error.code === 'INVALID_EXPERIENCE_INPUT')) {
        state.value = 'expired'
        message.value = '路径选择所需资料已过期或版本不一致，请重新生成导览。'
      }
    }
  }
  watch(fingerprintOf, () => { void load() }, { immediate: true })
  onBeforeUnmount(() => { active = false; version++ })
  return { result, state, message, retry: () => load() }
}
