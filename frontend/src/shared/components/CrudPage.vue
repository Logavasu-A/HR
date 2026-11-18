<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/shared/api'
import { can } from '@/shared/auth'
import { errorMessage, getPath, money, pretty } from '@/shared/utils'
import FieldInput from './FieldInput.vue'
import DataTable from './DataTable.vue'
import PaginationBar from './PaginationBar.vue'

const props = defineProps({ config: { type: Object, required: true } })

const rows = ref([])
const loading = ref(true)
const error = ref('')
const search = ref('')
const page = ref(0)
const pageSize = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)

const showForm = ref(false)
const editingId = ref(null)
const form = ref({})
const formError = ref('')
const saving = ref(false)
const refOptions = reactive({})

// ---- what this user may do on this screen ----
const writeAllowed = computed(() => (props.config.perm ? can(`${props.config.perm}:write`) : false))
const flag = (name) => (props.config[name] === undefined ? writeAllowed.value : props.config[name] && (props.config.perm ? writeAllowed.value : true))
const canCreate = computed(() => flag('create'))
const canEdit = computed(() => flag('edit'))
const canRemove = computed(() => flag('remove'))

const rowActions = (row) =>
  (props.config.actions || []).filter((a) => (!a.perm || can(a.perm)) && (!a.show || a.show(row)))
const hasActionColumn = computed(() => canEdit.value || canRemove.value || (props.config.actions || []).length > 0)

// ---- table ----
function cell(col, row) {
  const v = getPath(row, col.key)
  if (v == null || v === '') return ''
  if (col.money) return money(v)
  if (v === true) return 'Yes'
  if (v === false) return 'No'
  if (typeof v === 'string' && /^[A-Z][A-Z_]+$/.test(v)) return pretty(v)
  if (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(v)) return v.replace('T', ' ').slice(0, 16)
  return v
}

// short values such as codes and dates should stay on one line
function isShort(col, row) {
  return String(cell(col, row)).length <= 16
}

function badgeClass(col, row) {
  if (!col.badge) return ''
  const v = getPath(row, col.key)
  return v ? `badge ${String(v).toLowerCase()}` : ''
}

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter((row) =>
    props.config.columns.some((c) => String(cell(c, row)).toLowerCase().includes(q)),
  )
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get(props.config.endpoint, {
      params: { page: page.value, size: pageSize.value },
    })
    if (Array.isArray(data)) {
      rows.value = data
      totalElements.value = data.length
      totalPages.value = data.length ? 1 : 0
    } else if (data && Array.isArray(data.content)) {
      rows.value = data.content
      totalElements.value = data.totalElements ?? data.content.length
      totalPages.value = data.totalPages ?? (data.content.length ? 1 : 0)
      if (data.content.length === 0 && page.value > 0 && totalPages.value > 0) {
        page.value = Math.max(0, totalPages.value - 1)
        await load()
        return
      }
    } else {
      rows.value = []
      totalElements.value = 0
      totalPages.value = 0
    }
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

function onPageChange(nextPage) {
  page.value = nextPage
  load()
}

function onPageSizeChange(nextSize) {
  pageSize.value = nextSize
  page.value = 0
  load()
}

// ---- form <-> record conversion ----
function toForm(fields, row) {
  const out = {}
  for (const f of fields) {
    const v = row ? row[f.key] : undefined
    if (f.type === 'ref') out[f.key] = v?.id ?? ''
    else if (f.type === 'lines') out[f.key] = (v || []).map((line) => ({ id: line.id, ...toForm(f.fields, line) }))
    else if (f.type === 'checkbox') out[f.key] = row ? v === true : f.default === true
    else if (f.type === 'multicheck') out[f.key] = [...(v || [])]
    else if (f.type === 'password') out[f.key] = ''
    else out[f.key] = v ?? (row ? '' : f.default ?? '')
  }
  return out
}

function toPayload(fields, values) {
  const out = {}
  for (const f of fields) {
    const v = values[f.key]
    if (f.type === 'ref') out[f.key] = v === '' || v == null ? null : { id: v }
    else if (f.type === 'lines') out[f.key] = (v || []).map((line) => ({ ...(line.id ? { id: line.id } : {}), ...toPayload(f.fields, line) }))
    else if (f.type === 'checkbox') out[f.key] = v === true
    else if (f.type === 'multicheck') out[f.key] = v || []
    else if (f.type === 'number') out[f.key] = v === '' || v == null ? null : Number(v)
    else if (f.type === 'password') { if (v) out[f.key] = v }
    else out[f.key] = v === '' || v == null ? null : v
  }
  return out
}

async function loadOptions(fields) {
  for (const f of fields) {
    if (f.type === 'lines') {
      await loadOptions(f.fields)
    } else if (f.endpoint && !refOptions[f.endpoint]) {
      const { data } = await api.get(f.endpoint)
      refOptions[f.endpoint] = Array.isArray(data) ? data : (data?.content || [])
    }
  }
}

async function openForm(row) {
  formError.value = ''
  editingId.value = row ? row.id : null
  form.value = toForm(props.config.fields, row)
  showForm.value = true
  try {
    // dropdown contents may have changed on another screen, so fetch them fresh each time
    Object.keys(refOptions).forEach((k) => delete refOptions[k])
    await loadOptions(props.config.fields)
  } catch (e) {
    formError.value = `Could not load the dropdown lists: ${errorMessage(e)}`
  }
}

function addLine(field) {
  form.value[field.key].push(toForm(field.fields, null))
}

async function save() {
  saving.value = true
  formError.value = ''
  try {
    const payload = toPayload(props.config.fields, form.value)
    if (editingId.value) await api.put(`${props.config.endpoint}/${editingId.value}`, payload)
    else await api.post(props.config.endpoint, payload)
    showForm.value = false
    await load()
  } catch (e) {
    formError.value = errorMessage(e)
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  if (!window.confirm('Delete this record? This cannot be undone.')) return
  error.value = ''
  try {
    await api.delete(`${props.config.endpoint}/${row.id}`)
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function runAction(action, row) {
  if (action.confirm && !window.confirm(action.confirm)) return
  let body = {}
  if (action.ask) {
    const answer = window.prompt(action.ask.label, '')
    if (answer === null) return
    if (action.ask.required && answer.trim() === '') return
    body = { [action.ask.key]: action.ask.number ? Number(answer) : answer }
    if (action.ask.number && Number.isNaN(body[action.ask.key])) {
      error.value = 'Please enter a number'
      return
    }
  }
  error.value = ''
  try {
    await api.post(action.path(row), body)
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

watch(
  () => props.config.endpoint,
  () => {
    page.value = 0
    search.value = ''
    load()
  },
)

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h1>{{ config.title }}</h1>
      <div class="spacer"></div>
      <input v-model="search" class="search" type="search" placeholder="Search" />
      <button v-if="canCreate" class="btn primary" @click="openForm(null)">New</button>
    </div>
    <p v-if="config.hint" class="hint">{{ config.hint }}</p>
    <div v-if="error" class="alert">{{ error }}</div>

    <DataTable
      :columns="config.columns"
      :rows="filtered"
      :loading="loading"
      :has-actions="hasActionColumn"
      :format-cell="cell"
      :badge-class="badgeClass"
      :is-short="isShort"
      :empty-icon="rows.length ? '🔍' : '📭'"
      :empty-text="rows.length ? 'Nothing matches the search.' : 'No records yet.'"
      :aria-label="config.title"
    >
      <template v-if="hasActionColumn" #actions="{ row }">
        <button
          v-for="a in rowActions(row)" :key="a.label"
          class="btn small" :class="{ danger: a.danger }" @click="runAction(a, row)"
        >{{ a.label }}</button>
        <button v-if="canEdit" class="btn small" @click="openForm(row)">Edit</button>
        <button v-if="canRemove" class="btn small danger" @click="remove(row)">Delete</button>
      </template>
      <template #footer>
        <PaginationBar
          v-if="!loading && totalElements > 0"
          :page="page"
          :page-size="pageSize"
          :total-elements="totalElements"
          :total-pages="totalPages"
          :loading="loading"
          :filtered-count="search.trim() ? filtered.length : null"
          @update:page="onPageChange"
          @update:page-size="onPageSizeChange"
        />
      </template>
    </DataTable>

    <div v-if="showForm" class="modal-backdrop" @click.self="showForm = false">
      <form class="modal" @submit.prevent="save">
        <h2>{{ editingId ? 'Edit' : 'New' }} · {{ config.title }}</h2>
        <div v-if="formError" class="alert">{{ formError }}</div>

        <div class="form-grid">
          <template v-for="f in config.fields" :key="f.key">
            <div v-if="f.type === 'lines'" class="field wide">
              <label>{{ f.label }}</label>
              <div class="table-card table-card--inline">
                <div class="table-wrapper table-wrapper--inline">
                  <table class="data-table data-table--compact lines">
                    <thead>
                      <tr><th v-for="lf in f.fields" :key="lf.key">{{ lf.label }}</th><th class="actions-col"></th></tr>
                    </thead>
                    <tbody>
                      <tr v-for="(line, i) in form[f.key]" :key="i">
                        <td v-for="lf in f.fields" :key="lf.key">
                          <FieldInput v-model="line[lf.key]" :field="lf" :options="refOptions[lf.endpoint] || []" />
                        </td>
                        <td class="actions"><button type="button" class="btn small danger" @click="form[f.key].splice(i, 1)">Remove</button></td>
                      </tr>
                      <tr v-if="!form[f.key].length" class="table-state-row"><td :colspan="f.fields.length + 1" class="empty">No rows.</td></tr>
                    </tbody>
                  </table>
                </div>
              </div>
              <button type="button" class="btn small" @click="addLine(f)">Add row</button>
            </div>
            <div v-else class="field" :class="{ wide: f.type === 'textarea' || f.type === 'multicheck', inline: f.type === 'checkbox' }">
              <label>{{ f.label }}<span v-if="f.required" class="req"> *</span></label>
              <FieldInput v-model="form[f.key]" :field="f" :options="refOptions[f.endpoint] || []" />
            </div>
          </template>
        </div>

        <div class="modal-foot">
          <button type="button" class="btn" @click="showForm = false">Cancel</button>
          <button type="submit" class="btn primary" :disabled="saving">{{ saving ? 'Saving…' : 'Save' }}</button>
        </div>
      </form>
    </div>
  </div>
</template>
