export interface RepositorySummary {
  owner: string
  name: string
  description: string | null
  canonicalUrl: string
  stars: number
  createdAt: string
  pushedAt: string | null
  projectWebsiteUrl: string | null
  evidenceId: string
}

export type GuideErrorCode =
  | 'REPOSITORY_NOT_ACCESSIBLE'
  | 'GITHUB_RATE_LIMITED'
  | 'GITHUB_UPSTREAM_FAILURE'
  | 'GITHUB_SERVICE_UNAVAILABLE'
  | 'GITHUB_TIMEOUT'
  | 'README_CONTENT_UNSUPPORTED'

export type ReadmeSectionStatus = 'AVAILABLE' | 'NOT_PROVIDED' | 'FAILED'

export type OnlineExperienceWarning =
  | 'EXTERNAL_SITE_NOT_VERIFIED'
  | 'INSECURE_HTTP'

export interface OnlineExperienceCandidate {
  label: string
  url: string
  evidenceId: string
  warnings: OnlineExperienceWarning[]
}

export interface ReadmeFailure {
  code: GuideErrorCode
  retryable: boolean
  retryAfterSeconds?: number | null
}

export interface ReadmeSection {
  status: ReadmeSectionStatus
  candidates: OnlineExperienceCandidate[]
  truncated: boolean
  failure?: ReadmeFailure | null
}

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
  type: 'REPOSITORY' | 'LANGUAGES' | 'README'
  source: string
  repositoryUrl?: string | null
  recentCodeUpdate?: { field: string; value: string } | null
  totalBytes?: number | null
  languages: Array<LanguageItem & { bytes: number }>
  readmeUrl?: string | null
  path?: string | null
  sha?: string | null
  context?: string | null
}

export interface GuideResponse {
  repository: RepositorySummary
  readme: ReadmeSection
  languages: LanguageSection
  evidence: Record<string, Evidence>
}

export interface ReadmeRetryResponse {
  readme: ReadmeSection
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
  retryable?: boolean
}

export type GuideStatus = 'idle' | 'submitting' | 'success' | 'error'
