<script setup>
import { onMounted, ref } from 'vue'
import * as me from '../services/selfService'
import { errorMessage, pretty } from '@/shared/utils'

const profile = ref(null)
const today = ref(null)
const balances = ref([])
const error = ref('')
const busy = ref(false)

async function load() {
  error.value = ''
  try {
    const [p, t, b] = await Promise.all([
      me.getProfile(),
      me.getTodayAttendance(),
      me.getLeaveBalance(),
    ])
    profile.value = p.data
    today.value = t.data || null
    balances.value = b.data
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function punch(kind) {
  busy.value = true
  error.value = ''
  try {
    today.value = await me.punch(kind)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head"><h1>My space</h1></div>
    <div v-if="error" class="alert">{{ error }}</div>

    <div v-if="profile" class="two-col">
      <div class="card pad">
        <h3>{{ profile.fullName }}</h3>
        <dl class="details">
          <dt>Employee code</dt><dd>{{ profile.employeeCode }}</dd>
          <dt>Department</dt><dd>{{ profile.department?.name || '—' }}</dd>
          <dt>Designation</dt><dd>{{ profile.designation?.name || '—' }}</dd>
          <dt>Reports to</dt><dd>{{ profile.reportsTo?.fullName || '—' }}</dd>
          <dt>Joined</dt><dd>{{ profile.dateOfJoining }}</dd>
          <dt>Email</dt><dd>{{ profile.email || '—' }}</dd>
        </dl>
      </div>

      <div class="card pad">
        <h3>Today</h3>
        <p v-if="!today" class="muted">You have not checked in yet.</p>
        <dl v-else class="details">
          <dt>Status</dt><dd><span :class="`badge ${today.status.toLowerCase()}`">{{ pretty(today.status) }}</span></dd>
          <dt>Checked in</dt><dd>{{ today.inTime || '—' }}</dd>
          <dt>Checked out</dt><dd>{{ today.outTime || '—' }}</dd>
          <dt>Hours</dt><dd>{{ today.workingHours ?? '—' }}</dd>
        </dl>
        <div class="row-gap">
          <button class="btn primary" :disabled="busy || !!today?.inTime" @click="punch('check-in')">Check in</button>
          <button class="btn" :disabled="busy || !today?.inTime || !!today?.outTime" @click="punch('check-out')">Check out</button>
        </div>
      </div>
    </div>

    <h2 class="section">Leave balance</h2>
    <div class="card table-wrap">
      <table>
        <thead><tr><th>Leave type</th><th class="num">Allocated</th><th class="num">Used or pending</th><th class="num">Balance</th></tr></thead>
        <tbody>
          <tr v-if="!balances.length"><td colspan="4" class="muted">No leave types set up.</td></tr>
          <tr v-for="b in balances" :key="b.leaveTypeId">
            <td>{{ b.leaveType }}</td>
            <td class="num">{{ b.unpaid ? '—' : b.allocated }}</td>
            <td class="num">{{ b.used }}</td>
            <td class="num">{{ b.unpaid ? '—' : b.balance }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
