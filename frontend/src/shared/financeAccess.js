export const financeChallenges = new EventTarget()

export function installFinanceAccess(api) {
  api.interceptors.response.use((response) => {
    if (response.status === 202 && response.data?.code === 'REVIEW_REQUIRED') {
      throw Object.assign(new Error(response.data.message), { response })
    }
    return response
  }, (error) => {
    const code = error.response?.data?.code
    if (error.response?.status !== 428 || !['STEP_UP_REQUIRED', 'CONFIRMATION_REQUIRED'].includes(code)) {
      throw error
    }
    return new Promise((resolve, reject) => {
      const event = new CustomEvent('finance-challenge', {
        cancelable: true,
        detail: { api, code, cancel: () => reject(error), retry: (headers) => resolve(
          api.request({ ...error.config, headers: { ...error.config.headers, ...headers } }),
        ) },
      })
      if (financeChallenges.dispatchEvent(event)) reject(error)
    })
  })
}
