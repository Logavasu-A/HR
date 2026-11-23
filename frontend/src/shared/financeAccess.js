import { CanceledError } from 'axios'

export const financeChallenges = new EventTarget()

const approvalsPath = '/sensitive/approvals'
const protectedRead = (config, path) =>
  config.method === 'get' && (path.startsWith('/payroll/') ||
    path === '/me/payslips' || path === approvalsPath ||
    /^\/core\/employees\/[^/]+\/bank-details\/?$/.test(path))

export function installFinanceAccess(api, sessionKey = () => null) {
  let verification
  let accessCheck

  function challenge(code, scope, failure) {
    return new Promise((resolve, reject) => {
      const event = new CustomEvent('finance-challenge', {
        cancelable: true,
        detail: {
          api, code,
          isCurrent: () => sessionKey() === scope,
          cancel: () => reject(failure),
          retry: resolve,
        },
      })
      if (financeChallenges.dispatchEvent(event)) reject(failure)
    })
  }

  function verify(scope) {
    if (verification && verification.scope === scope) return verification.promise
    const current = { scope }
    verification = current
    current.promise = challenge('STEP_UP_REQUIRED', scope,
      new Error('Financial access is locked. Verify your password to continue.'))
      .finally(() => { if (verification === current) verification = undefined })
    return current.promise
  }

  function checkAccess(scope) {
    if (accessCheck && accessCheck.scope === scope) return accessCheck.promise
    const current = { scope }
    accessCheck = current
    current.promise = api.get('/sensitive/status').then(async ({ data }) => {
      assertCurrent(scope)
      if (data.unlocked === false) await verify(scope)
      return data
    }).finally(() => { if (accessCheck === current) accessCheck = undefined })
    return current.promise
  }

  function assertCurrent(scope, signal) {
    if (sessionKey() !== scope || signal?.aborted) {
      throw new CanceledError('Financial workspace or session changed.')
    }
  }

  api.interceptors.request.use(async (config) => {
    const scope = sessionKey()
    if ('financeSessionKey' in config) assertCurrent(config.financeSessionKey, config.signal)
    config.financeSessionKey = scope
    const path = (config.url || '').split('?')[0]
    if (!protectedRead(config, path)) return config
    const status = await checkAccess(scope)
    assertCurrent(scope, config.signal)
    if (path === approvalsPath && status.approvalsEnabled === false) {
      throw new Error('Financial approvals are unavailable in this workspace.')
    }
    return config
  })

  api.interceptors.response.use((response) => {
    if (response.status === 202 && response.data?.code === 'REVIEW_REQUIRED') {
      throw Object.assign(new Error(response.data.message), { response })
    }
    return response
  }, async (error) => {
    const code = error.response?.data?.code
    const config = error.config
    if (error.response?.status !== 428 || !config || config.url?.startsWith('/sensitive/status')
        || !['STEP_UP_REQUIRED', 'CONFIRMATION_REQUIRED'].includes(code)) {
      throw error
    }
    const scope = config.financeSessionKey
    assertCurrent(scope, config.signal)
    if (code === 'STEP_UP_REQUIRED') {
      if (config.financeStepUpRetried) throw error
      await verify(scope)
      assertCurrent(scope, config.signal)
      return api.request({ ...config, financeStepUpRetried: true })
    }
    if (config.financeConfirmationRetried) throw error
    const headers = await challenge(code, scope, error)
    assertCurrent(scope, config.signal)
    return api.request({
      ...config,
      financeConfirmationRetried: true,
      headers: { ...config.headers, ...headers },
    })
  })
}
