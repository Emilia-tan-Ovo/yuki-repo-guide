import { describe, expect, it } from 'vitest'
import { suggestRuntimeEnvironment } from '../src/features/guide/runtimeSuggestion'

describe('runtime suggestion', () => {
  it('uses User-Agent Client Hints for an OS and architecture suggestion', async () => {
    const result = await suggestRuntimeEnvironment({
      platform: 'ignored fallback',
      userAgent: 'ignored fallback',
      userAgentData: {
        platform: 'Windows',
        getHighEntropyValues: async () => ({ architecture: 'x86', bitness: '64' }),
      },
    })

    expect(result).toEqual({
      operatingSystem: 'WINDOWS',
      architecture: 'X64',
      source: 'USER_AGENT_CLIENT_HINTS',
    })
  })

  it('falls back to a coarse OS suggestion without guessing architecture', async () => {
    const result = await suggestRuntimeEnvironment({
      platform: 'MacIntel',
      userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)',
    })

    expect(result).toEqual({
      operatingSystem: 'MACOS',
      architecture: undefined,
      source: 'USER_AGENT_FALLBACK',
    })
  })

  it('returns no suggestion when the browser signals are not reliable', async () => {
    const result = await suggestRuntimeEnvironment({
      platform: '',
      userAgent: 'Unknown Browser',
    })

    expect(result).toEqual({
      operatingSystem: undefined,
      architecture: undefined,
      source: 'NONE',
    })
  })
})
