export interface RepositorySummary {
  owner: string
  name: string
  canonicalUrl: string
}

export interface GuideResponse {
  repository: RepositorySummary
}

export interface ProblemDetail {
  title?: string
  status?: number
  detail?: string
  code?: string
  field?: string
}

export type GuideStatus = 'idle' | 'submitting' | 'success' | 'error'
