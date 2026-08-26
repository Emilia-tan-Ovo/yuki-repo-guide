export interface RepositorySummary {
  owner: string
  name: string
  description: string | null
  canonicalUrl: string
  stars: number
  createdAt: string
  pushedAt: string | null
  evidenceId: string
}

export type GuideErrorCode =
  | 'REPOSITORY_NOT_ACCESSIBLE'
  | 'GITHUB_RATE_LIMITED'
  | 'GITHUB_UPSTREAM_FAILURE'
  | 'GITHUB_SERVICE_UNAVAILABLE'
  | 'GITHUB_TIMEOUT'

export type LanguageSectionStatus = 'AVAILABLE' | 'NOT_PROVIDED' | 'FAILED'

export interface LanguageItem {
  name: string
  percentage: number
}

export interface LanguageFailure {
  code: GuideErrorCode
  retryAfterSeconds?: number | null
}

export interface LanguageSection {
  status: LanguageSectionStatus
  items: LanguageItem[]
  failure?: LanguageFailure | null
  evidenceId?: string | null
}

export interface Evidence {
  type: 'REPOSITORY' | 'LANGUAGES'
  source: string
  repositoryUrl?: string | null
  recentCodeUpdate?: { field: string; value: string } | null
  totalBytes?: number | null
  languages: Array<LanguageItem & { bytes: number }>
}

export interface GuideResponse {
  repository: RepositorySummary
  languages: LanguageSection
  evidence: Record<string, Evidence>
}

export interface LanguageRetryResponse {
  languages: LanguageSection
  evidence: Record<string, Evidence>
}

export interface ProblemDetail {
  title?: string
  status?: number
  detail?: string
  code?: string
  field?: string
  retryAfterSeconds?: number
}

export type GuideStatus = 'idle' | 'submitting' | 'success' | 'error'
