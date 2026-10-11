import type { ConfirmedRuntime } from './guideTypes'

export interface ExperienceReferences {
  guideId: string
  readmeResultId: string
  releasesResultId: string
  quickStartResultId: string | null
}
export interface ExperienceRequest extends ExperienceReferences { runtime: ConfirmedRuntime | null }
export interface ExperienceOption { label: string; url: string; evidenceIds: string[]; warnings: string[] }
export interface ExperiencePath {
  id: string
  kind: 'ONLINE' | 'STABLE_RELEASE' | 'PRERELEASE' | 'QUICK_START'
  reliable: boolean
  options: ExperienceOption[]
  optionCount?: number
}
export interface ExperienceEvidence { source: string; url: string; detail: string; path: string | null; sha: string | null }
export interface ExperienceResult {
  status: 'PRIMARY_AVAILABLE' | 'CANDIDATES_ONLY' | 'UNDETERMINED' | 'NO_PATH'
  primary: ExperiencePath | null
  alternatives: ExperiencePath[]
  unresolved: string[]
  evidence: Record<string, ExperienceEvidence>
}
export type ExperienceState = 'idle' | 'loading' | 'available' | 'error' | 'expired'
