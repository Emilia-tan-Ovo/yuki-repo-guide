export interface CsrfTokenResponse {
  headerName: string
  parameterName: string
  token: string
}

export interface SessionResponse {
  authenticated: boolean
}

export interface AuthProblemDetail {
  detail?: string
}

export type AuthenticationState = 'initializing' | 'anonymous' | 'authenticated'
