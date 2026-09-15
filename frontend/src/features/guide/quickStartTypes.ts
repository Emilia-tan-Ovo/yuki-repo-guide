import type { ExplanationEvidence } from './explanationTypes'

export interface QuickStartEvidence extends ExplanationEvidence {
  kind: 'TEXT' | 'COMMAND' | 'CONFIGURATION' | 'CODE'
  section: string
  order: number
}
export interface QuickStartItem {
  text: string
  evidenceIds: string[]
  blocks: { evidenceId: string; text: string }[]
}
export interface QuickStartResult {
  status: 'AVAILABLE' | 'UNAVAILABLE'
  contentStatus: 'COMPLETE' | 'INCOMPLETE' | 'NOT_PROVIDED' | null
  requirements: QuickStartItem[]
  steps: QuickStartItem[]
  configuration: QuickStartItem[]
  cautions: QuickStartItem[]
  gaps: QuickStartItem[]
  evidence: Record<string, QuickStartEvidence>
  code: string | null
  retryAfterSeconds: number | null
}
