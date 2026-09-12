export interface ExplanationEvidence {
  id: string
  sourceUrl: string
  path: string | null
  sha: string | null
  text: string
}
export interface ExplanationResult {
  status: 'AVAILABLE' | 'INSUFFICIENT_EVIDENCE' | 'UNAVAILABLE'
  introduction: string | null
  evidenceIds: string[]
  evidence: Record<string, ExplanationEvidence>
  code: string | null
  retryAfterSeconds: number | null
}
export type ExplanationState = 'idle' | 'loading' | 'available' | 'insufficient' | 'unavailable' | 'expired' | 'in-progress'
