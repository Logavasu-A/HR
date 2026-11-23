<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useWorkspace } from '@/shared/workspaceContext'
import { errorMessage, getPath, money, pretty } from '@/shared/utils'
import AppDialog from './AppDialog.vue'
import FieldInput from './FieldInput.vue'
import DataTable from './DataTable.vue'
import ActionMenu from './ActionMenu.vue'
import PaginationBar from './PaginationBar.vue'

const props = defineProps({ config: { type: Object, required: true } })
const router = useRouter()
const workspace = useWorkspace()
const { api, can } = workspace

const rows = ref([])
const loading = ref(true)
const error = ref('')
const search = ref('')
const page = ref(0)
const pageSize = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)
let latestLoad = 0

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
  (props.config.actions || []).filter((a) => (!a.perm || can(a.perm)) && (!a.show || a.show(row, workspace)))
const hasActionColumn = computed(() => canEdit.value || canRemove.value || rows.value.some(row => rowActions(row).length))

function menuItems(row) {
  const items = rowActions(row).map((a) => ({
    label: a.label,
    danger: Boolean(a.danger),
    onClick: () => runAction(a, row),
  }))
  if (canEdit.value) items.push({ label: 'Edit', onClick: () => openForm(row) })
  if (canRemove.value) items.push({ label: 'Delete', danger: true, onClick: () => remove(row) })
  // Keep non-destructive actions above destructive ones so the divider groups cleanly
  return [...items.filter((i) => !i.danger), ...items.filter((i) => i.danger)]
}

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
  const request = ++latestLoad
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get(props.config.endpoint, {
      params: { page: page.value, size: pageSize.value },
    })
    if (request !== latestLoad) return
    if (Array.isArray(data)) {
      rows.value = data
      totalElements.value = data.length
      totalPages.value = data.length ? 1 : 0
      page.value = 0
    } else if (data && Array.isArray(data.content)) {
      rows.value = data.content
      totalElements.value = data.totalElements ?? data.content.length
      totalPages.value = data.totalPages ?? (data.content.length ? 1 : 0)
      if (data.content.length === 0 && page.value > 0 && page.value >= totalPages.value) {
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
    if (request === latestLoad) error.value = errorMessage(e)
  } finally {
    if (request === latestLoad) loading.value = false
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
  if (action.route) return router.push(action.route(row, workspace))
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
    <div class="page-head crud-heading">
      <div class="crud-title-wrap">
        <h1>{{ config.title }}</h1>
      </div>
      <div class="spacer"></div>
      <div class="crud-toolbar">
        <input v-model="search" class="search" type="search" placeholder="Search records…" aria-label="Search this page" />
        <button v-if="canCreate" class="btn primary" @click="openForm(null)">
          <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16" aria-hidden="true">
            <path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd"/>
          </svg>
          New
        </button>
      </div>
    </div>
    <div v-if="error" class="alert">{{ error }}</div>

    <DataTable
      :columns="config.columns"
      :rows="filtered"
      :loading="loading"
      :has-actions="hasActionColumn"
      :format-cell="cell"
      :badge-class="badgeClass"
      :is-short="isShort"
      :empty-icon="rows.length ? 'search' : 'records'"
      :empty-text="rows.length ? 'Nothing matches the search.' : 'No records yet.'"
      :aria-label="config.title"
    >
      <template v-if="hasActionColumn" #actions="{ row }">
        <ActionMenu :items="menuItems(row)" />
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

    <AppDialog
      v-if="showForm"
      :title="`${editingId ? 'Edit' : 'New'} · ${config.title}`"
      form
      @dismiss="showForm = false"
      @submit="save"
    >
      <div v-if="formError" class="alert">{{ formError }}</div>

      <div class="form-grid">
        <template v-for="f in config.fields" :key="f.key">
          <div v-if="f.type === 'lines'" class="field wide">
            <label>{{ f.label }}</label>
            <div class="table-card table-card--inline">
              <div class="table-wrapper table-wrapper--inline" tabindex="0" role="region" :aria-label="f.label">
                <table class="data-table data-table--compact lines responsive-table" role="table">
                  <thead>
                    <tr>
                      <th v-for="lf in f.fields" :key="lf.key" scope="col" :class="{ num: lf.type === 'number' }">
                        {{ lf.label }}
                      </th>
                      <th scope="col" class="actions-col">Actions</th>
                    </tr>
                  </thead>
                  <tbody role="rowgroup">
                    <tr v-for="(line, i) in form[f.key]" :key="i" role="row">
                      <td v-for="lf in f.fields" :key="lf.key" :data-label="lf.label" role="cell" :class="{ num: lf.type === 'number' }">
                        <FieldInput v-model="line[lf.key]" :field="lf" :options="refOptions[lf.endpoint] || []"
                          :aria-label="`${lf.label}, row ${i + 1}`"
                          :aria-describedby="lf.help ? `crud-help-${f.key}-${i}-${lf.key}` : undefined" />
                        <small v-if="lf.help" :id="`crud-help-${f.key}-${i}-${lf.key}`" class="control-note">{{ lf.help }}</small>
                      </td>
                      <td class="actions" role="cell"><button type="button" class="btn small danger" @click="form[f.key].splice(i, 1)">Remove</button></td>
                    </tr>
                    <tr v-if="!form[f.key].length" class="table-state-row">
                      <td :colspan="f.fields.length + 1" class="empty">
                        <div class="table-state-message">No rows.</div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
            <button type="button" class="btn small add-line" @click="addLine(f)">Add row</button>
          </div>
          <div v-else class="field" :class="{ wide: f.type === 'textarea' || f.type === 'multicheck', inline: f.type === 'checkbox' }">
            <label :id="f.type === 'multicheck' ? `crud-label-${f.key}` : undefined"
              :for="f.type === 'multicheck' ? undefined : `crud-field-${f.key}`">
              {{ f.label }}<span v-if="f.required" class="req" aria-hidden="true"> *</span>
            </label>
            <small v-if="f.help" :id="`crud-help-${f.key}`" class="control-note">{{ f.help }}</small>
            <FieldInput :id="`crud-field-${f.key}`" v-model="form[f.key]" :field="f" :options="refOptions[f.endpoint] || []"
              :role="f.type === 'multicheck' ? 'group' : undefined"
              :aria-labelledby="f.type === 'multicheck' ? `crud-label-${f.key}` : undefined"
              :aria-describedby="f.help ? `crud-help-${f.key}` : undefined" />
          </div>
        </template>
      </div>

      <template #footer>
        <small v-if="config.saveNote" id="crud-save-note" class="control-note">{{ config.saveNote }}</small>
        <button type="button" class="btn" @click="showForm = false">Cancel</button>
        <button type="submit" class="btn primary" :disabled="saving"
          :aria-describedby="config.saveNote ? 'crud-save-note' : undefined">{{ saving ? 'Saving…' : 'Save' }}</button>
      </template>
    </AppDialog>
  </div>
</template>
