import type { Ref } from 'vue'
import { requestExplanation } from './guideApi'
import { useGeneratedRegion } from './useGeneratedRegion'

export function useExplanation(now: Ref<number>, authenticate: (retry: () => Promise<void>) => void) {
  return useGeneratedRegion(now, authenticate, requestExplanation, result => result.status === 'AVAILABLE'
    ? 'available' : result.status === 'INSUFFICIENT_EVIDENCE' ? 'insufficient' : 'unavailable')
}
