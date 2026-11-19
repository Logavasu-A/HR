<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/shared/api'
import { auth, can } from '@/shared/auth'
import { errorMessage } from '@/shared/utils'

const route = useRoute()
const employeePath = `/core/employees/${encodeURIComponent(route.params.id)}`
const signal = new AbortController()
const employee = ref(null)
const details = ref(null)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const canEdit = computed(() => can('core:write') && auth.user?.permissions?.includes('employee-bank:write'))

async function load() {
  try {
    const options = { signal: signal.signal }
    const [profile, banking] = await Promise.all([
      api.get(employeePath, options),
      api.get(`${employeePath}/bank-details`, options),
    ])
    employee.value = profile.data
    details.value = { bankName: banking.data.bankName ?? '', bankAccountNo: banking.data.bankAccountNo ?? '' }
  } catch (e) {
    if (!signal.signal.aborted) error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!canEdit.value || saving.value || !window.confirm('Save changes to this employee’s bank details?')) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const { bankName, bankAccountNo } = details.value
    await api.put(`${employeePath}/bank-details`, { bankName: bankName || null, bankAccountNo: bankAccountNo || null },
      { signal: signal.signal })
    notice.value = 'Bank details saved.'
  } catch (e) {
    if (!signal.signal.aborted) error.value = errorMessage(e)
  } finally {
    saving.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => signal.abort())
</script>

<template>
  <section>
    <div class="page-head">
      <h1>Employee bank details</h1>
      <router-link class="btn" to="/employees">Back to employees</router-link>
    </div>
    <p v-if="loading" role="status">Loading bank details…</p>
    <p v-if="error" class="alert" role="alert">{{ error }}</p>
    <p v-if="notice" class="alert ok" role="status">{{ notice }}</p>
    <form v-if="details" class="card bank-details" @submit.prevent="save">
      <h2>{{ employee.fullName }}</h2>
      <p class="muted">{{ employee.employeeCode }}</p>
      <div class="form-grid">
        <div class="field">
          <label for="employee-bank-name">Bank name</label>
          <input id="employee-bank-name" v-model.trim="details.bankName" maxlength="120" autocomplete="off" :readonly="!canEdit || saving" />
        </div>
        <div class="field">
          <label for="employee-bank-account">Bank account number</label>
          <input id="employee-bank-account" v-model.trim="details.bankAccountNo" maxlength="60" autocomplete="off" :readonly="!canEdit || saving" />
        </div>
      </div>
      <button v-if="canEdit" class="btn primary" type="submit" :disabled="saving">{{ saving ? 'Saving…' : 'Save bank details' }}</button>
    </form>
  </section>
</template>

<style scoped>
.bank-details { max-width: 720px; padding: 20px; }
.bank-details .form-grid { margin: 16px 0; }
</style>
