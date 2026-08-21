import assert from 'node:assert/strict'
import test from 'node:test'

test('CSRF 失效后刷新令牌且只重试一次', async (context) => {
  const { fetchWithCsrfRetry } = await import('../src/features/auth/authApi.ts')
  const requests = []
  const responses = [
    jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'first-token' }),
    new Response(null, { status: 403 }),
    jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'second-token' }),
    new Response(null, { status: 403 }),
  ]

  context.mock.method(globalThis, 'fetch', async (input, init) => {
    requests.push({ input, init })
    return responses.shift()
  })

  const response = await fetchWithCsrfRetry('/api/guides', { method: 'POST' })

  assert.equal(response.status, 403)
  assert.equal(requests.length, 4)
  assert.equal(requests[1].init.headers.get('X-CSRF-TOKEN'), 'first-token')
  assert.equal(requests[3].init.headers.get('X-CSRF-TOKEN'), 'second-token')
})

test('待恢复操作取出后立即清除，登录成功只会自动执行一次', async () => {
  const { PendingActionSlot } = await import('../src/features/auth/pendingAction.ts')
  const slot = new PendingActionSlot()
  let executions = 0
  slot.remember(async () => {
    executions += 1
  })

  const pendingAction = slot.take()
  await pendingAction()

  assert.equal(executions, 1)
  assert.equal(slot.take(), null)
})

function jsonResponse(body) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}
