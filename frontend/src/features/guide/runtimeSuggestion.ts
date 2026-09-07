import type { ProcessorArchitecture, RuntimeOperatingSystem } from './guideTypes'

interface UserAgentDataLike {
  platform?: string
  getHighEntropyValues?: (
    hints: string[],
  ) => Promise<{ architecture?: string; bitness?: string }>
}

export interface RuntimeNavigatorLike {
  platform?: string
  userAgent?: string
  userAgentData?: UserAgentDataLike
}

export interface RuntimeSuggestion {
  operatingSystem?: RuntimeOperatingSystem
  architecture?: ProcessorArchitecture
  source: 'USER_AGENT_CLIENT_HINTS' | 'USER_AGENT_FALLBACK' | 'NONE'
}

export async function suggestRuntimeEnvironment(
  runtimeNavigator: RuntimeNavigatorLike = navigator as RuntimeNavigatorLike,
): Promise<RuntimeSuggestion> {
  const userAgentData = runtimeNavigator.userAgentData
  const hintedOperatingSystem = operatingSystemFrom(userAgentData?.platform)
  if (hintedOperatingSystem) {
    let architecture: ProcessorArchitecture | undefined
    if (userAgentData?.getHighEntropyValues) {
      try {
        const values = await userAgentData.getHighEntropyValues(['architecture', 'bitness'])
        architecture = architectureFrom(values.architecture, values.bitness)
      } catch {
        // Browser policy may decline high-entropy hints. A coarse OS hint is still useful.
      }
    }
    return {
      operatingSystem: hintedOperatingSystem,
      architecture,
      source: 'USER_AGENT_CLIENT_HINTS',
    }
  }

  const fallbackOperatingSystem = operatingSystemFrom(
    `${runtimeNavigator.platform ?? ''} ${runtimeNavigator.userAgent ?? ''}`,
  )
  return fallbackOperatingSystem
    ? {
        operatingSystem: fallbackOperatingSystem,
        architecture: undefined,
        source: 'USER_AGENT_FALLBACK',
      }
    : { operatingSystem: undefined, architecture: undefined, source: 'NONE' }
}

function operatingSystemFrom(rawValue?: string): RuntimeOperatingSystem | undefined {
  const value = rawValue?.toLowerCase() ?? ''
  if (value.includes('windows') || /^win/.test(value)) return 'WINDOWS'
  if (value.includes('mac') || value.includes('darwin')) return 'MACOS'
  if (value.includes('linux')) return 'LINUX'
  return undefined
}

function architectureFrom(
  rawArchitecture?: string,
  rawBitness?: string,
): ProcessorArchitecture | undefined {
  const architecture = rawArchitecture?.toLowerCase() ?? ''
  const bitness = rawBitness?.toLowerCase() ?? ''
  if (['x64', 'x86_64', 'amd64'].includes(architecture)) return 'X64'
  if (architecture === 'x86' && bitness === '64') return 'X64'
  if (['arm64', 'aarch64'].includes(architecture)) return 'ARM64'
  if (architecture === 'arm' && bitness === '64') return 'ARM64'
  return undefined
}
