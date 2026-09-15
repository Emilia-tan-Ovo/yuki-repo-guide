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
  | 'RELEASE_HISTORY_UNSUPPORTED'

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

export type ReleaseSectionStatus = 'AVAILABLE' | 'NOT_PROVIDED' | 'FAILED'
export type ReleaseWarning = 'PRERELEASE' | 'SOME_ASSETS_OMITTED'
export type ReleaseChannel = 'STABLE' | 'PRERELEASE'
export type RuntimeOperatingSystem = 'WINDOWS' | 'MACOS' | 'LINUX'
export type ProcessorArchitecture = 'X64' | 'ARM64'
export type LinuxPackageFamily = 'DEB' | 'RPM' | 'ARCH' | 'ALPINE' | 'OTHER_OR_UNKNOWN'
export type ReleaseAssetRole =
  | 'STANDARD_INSTALLER'
  | 'PORTABLE'
  | 'MANUAL_ARCHIVE'
  | 'AUXILIARY'
  | 'SOURCE'
  | 'UNKNOWN'
export type ReleaseAssetMatchStatus =
  | 'MATCHED'
  | 'POSSIBLY_APPLICABLE'
  | 'NOT_MATCHED'
  | 'UNABLE_TO_CONFIRM'
export type ReleaseRecommendationStatus = 'NOT_REQUESTED' | 'NEEDS_LINUX_FAMILY' | 'READY'

export interface ConfirmedRuntime {
  operatingSystem: RuntimeOperatingSystem
  architecture: ProcessorArchitecture
  linuxPackageFamily?: LinuxPackageFamily | null
}

export interface ReleaseAssetAssessment {
  matchStatus: ReleaseAssetMatchStatus
  directlyRecommended: boolean
  detectedOperatingSystem?: RuntimeOperatingSystem | null
  detectedArchitecture?: ProcessorArchitecture | null
  detectedLinuxPackageFamily?: LinuxPackageFamily | null
}

export interface ReleaseAsset {
  name: string
  sizeBytes: number
  downloadUrl: string
  evidenceId: string
  role: ReleaseAssetRole
  assessment?: ReleaseAssetAssessment | null
}

export interface ReleaseSummary {
  name?: string | null
  tagName: string
  publishedAt: string
  assets: ReleaseAsset[]
  matchingAssets: ReleaseAsset[]
  matchingAssetCount: number
  matchingAssetsTruncated: boolean
  reportedAssetCount: number
  excludedAssetCount: number
  assetsTruncated: boolean
  warnings: ReleaseWarning[]
  evidenceId: string
}

export interface ReleaseFailure {
  code: GuideErrorCode
  retryable: boolean
  retryAfterSeconds?: number | null
}

export interface ReleaseSection {
  status: ReleaseSectionStatus
  latestStable?: ReleaseSummary | null
  latestPrerelease?: ReleaseSummary | null
  failure?: ReleaseFailure | null
  recommendation: {
    status: ReleaseRecommendationStatus
    runtime?: ConfirmedRuntime | null
    availableLinuxFamilies: LinuxPackageFamily[]
  }
}

export interface Evidence {
  type: 'REPOSITORY' | 'LANGUAGES' | 'README' | 'RELEASE' | 'RELEASE_ASSET'
  source: string
  repositoryUrl?: string | null
  recentCodeUpdate?: { field: string; value: string } | null
  totalBytes?: number | null
  languages: Array<LanguageItem & { bytes: number }>
  readmeUrl?: string | null
  path?: string | null
  sha?: string | null
  context?: string | null
  releaseUrl?: string | null
  releaseId?: number | null
  tagName?: string | null
  publishedAt?: string | null
  channel?: ReleaseChannel | null
  reportedAssetCount?: number | null
  releaseEvidenceId?: string | null
  assetId?: number | null
  assetName?: string | null
  sizeBytes?: number | null
  downloadUrl?: string | null
}

export interface GuideResponse {
  experience?: import('./experienceTypes').ExperienceReferences | null
  explanationInputId?: string | null
  repository: RepositorySummary
  readme: ReadmeSection
  languages: LanguageSection
  releases: ReleaseSection
  evidence: Record<string, Evidence>
}

export interface ReadmeRetryResponse {
  resultId?: string | null
  readme: ReadmeSection
  evidence: Record<string, Evidence>
}

export interface LanguageRetryResponse {
  languages: LanguageSection
  evidence: Record<string, Evidence>
}

export interface ReleaseRetryResponse {
  resultId?: string | null
  releases: ReleaseSection
  evidence: Record<string, Evidence>
}

export type ReleaseRecommendationResponse = ReleaseRetryResponse

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
