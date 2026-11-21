import api from '@/shared/api'

export const getSummary = (client = api) => client.get('/dashboard/summary').then((r) => r.data)
