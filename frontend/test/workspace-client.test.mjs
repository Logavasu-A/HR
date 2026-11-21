import assert from 'node:assert/strict'
import test from 'node:test'
import axios from 'axios'
import { createWorkspaceClient } from '../src/modules/platform/workspaceClient.js'

test('a delayed old response cannot populate a new owner workspace', async () => {
  const seen = []
  let deliverAlpha
  let started
  const alphaStarted = new Promise(resolve => { started = resolve })
  const alpha = createWorkspaceClient({
    token: 'one-platform-token',
    id: 'alpha-grant',
    onInvalid: () => assert.fail('An aborted request must not close the new workspace'),
    adapter: config => new Promise(resolve => {
      seen.push(config.headers.get('X-HRMS-Workspace'))
      assert.equal(config.headers.get('Authorization'), 'Bearer one-platform-token')
      deliverAlpha = () => resolve({ data: ['ALPHA'], status: 200, config })
      started()
      // Simulate a network adapter that still delivers an old response after cancellation.
    }),
  })
  const old = alpha.api.get('/core/employees')
  const cancelled = assert.rejects(old, axios.isCancel)
  await alphaStarted
  alpha.retire()
  const bravo = createWorkspaceClient({
    token: 'one-platform-token',
    id: 'bravo-grant',
    onInvalid: () => assert.fail('Bravo should not expire'),
    adapter: async config => {
      seen.push(config.headers.get('X-HRMS-Workspace'))
      assert.equal(config.headers.get('Authorization'), 'Bearer one-platform-token')
      return { data: ['BRAVO'], status: 200, config }
    },
  })
  assert.deepEqual((await bravo.api.get('/core/employees')).data, ['BRAVO'])
  deliverAlpha()
  await cancelled
  await assert.rejects(alpha.api.post('/core/employees', { firstName: 'Old form' }), axios.isCancel)
  assert.deepEqual(seen, ['alpha-grant', 'bravo-grant'])
})
