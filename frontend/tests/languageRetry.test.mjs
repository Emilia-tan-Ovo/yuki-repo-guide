import assert from 'node:assert/strict'
import test from 'node:test'

test('语言重试遇到限额时保留 code 和绝对等待依据，且不会自动重试', async (context) => {
  const { GuideApiError, retryLanguages } = await import('../src/features/guide/guideApi.ts')
  const requests = []
  const responses = [
    jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token' }),
    jsonResponse(
      {
        title: 'GitHub 请求受限',
        status: 429,
        detail: 'GitHub 暂时限制了请求，请稍后重试。',
        code: 'GITHUB_RATE_LIMITED',
        retryAfterSeconds: 120,
      },
      429,
      { 'Retry-After': '120' },
    ),
  ]
  context.mock.method(globalThis, 'fetch', async (input, init) => {
    requests.push({ input, init })
    return responses.shift()
  })

  await assert.rejects(
    retryLanguages('https://github.com/octo/example'),
    (error) => {
      assert.ok(error instanceof GuideApiError)
      assert.equal(error.code, 'GITHUB_RATE_LIMITED')
      assert.equal(error.retryAfterSeconds, 120)
      return true
    },
  )

  assert.equal(requests.length, 2)
  assert.equal(requests[1].input, '/api/guides/languages/retry')
  assert.deepEqual(JSON.parse(requests[1].init.body), {
    canonicalUrl: 'https://github.com/octo/example',
  })
})

test('等待时间按绝对截止点计算，到期只恢复按钮', async () => {
  const { createRetryDeadline, retryAvailability } = await import(
    '../src/features/guide/languageRetry.ts'
  )
  const startedAt = Date.parse('2026-08-25T00:00:00Z')
  const deadline = createRetryDeadline(120, startedAt)

  assert.equal(deadline, startedAt + 120_000)
  assert.deepEqual(retryAvailability(deadline, startedAt + 1_000), {
    disabled: true,
    remainingSeconds: 119,
    message: '约 2 分钟后可重试',
  })
  assert.deepEqual(retryAvailability(deadline, deadline), {
    disabled: false,
    remainingSeconds: 0,
    message: '请点击重试',
  })
})

test('语言重试成功只替换语言区域并保留仓库卡片', async () => {
  const { applyLanguageRetry } = await import('../src/features/guide/languageRetry.ts')
  const repository = { owner: 'octo', name: 'example', canonicalUrl: 'https://github.com/octo/example' }
  const guide = {
    repository,
    languages: { status: 'FAILED', items: [], failure: { code: 'GITHUB_TIMEOUT' } },
    evidence: { 'repository-metadata': { type: 'REPOSITORY', source: 'GitHub' } },
  }
  const retried = {
    languages: { status: 'AVAILABLE', items: [{ name: 'Java', percentage: 100 }] },
    evidence: { 'repository-languages': { type: 'LANGUAGES', source: 'GitHub Languages REST API' } },
  }

  const updated = applyLanguageRetry(guide, retried)

  assert.equal(updated.repository, repository)
  assert.equal(updated.languages, retried.languages)
  assert.deepEqual(Object.keys(updated.evidence).sort(), [
    'repository-languages',
    'repository-metadata',
  ])
})

test('新导览已开始时忽略旧仓库的语言重试成功或失败结果', async () => {
  const { isCurrentLanguageRetry } = await import('../src/features/guide/languageRetry.ts')
  const oldUrl = 'https://github.com/octo/old-repository'
  const newGuide = {
    repository: { canonicalUrl: 'https://github.com/octo/new-repository' },
  }

  assert.equal(isCurrentLanguageRetry(oldUrl, 3, newGuide, 4), false)
  assert.equal(isCurrentLanguageRetry(oldUrl, 3, newGuide, 3), false)
  assert.equal(isCurrentLanguageRetry(newGuide.repository.canonicalUrl, 4, newGuide, 4), true)
})

function jsonResponse(body, status = 200, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}
