// Collects everything the feature modules provide.
//
// resources:     list-and-form screens, rendered by shared/components/CrudPage.vue.
//   columns: { key (dotted path), label, money?, badge? }
//   fields:  { key, label, type, required?, options?, endpoint?, fields? (for type 'lines'), default? }
//     types: text, textarea, number, date, time, datetime, checkbox, password,
//            select (static options), ref (record from another endpoint), lines (child rows),
//            multicheck (list of strings from an endpoint)
//   actions: { label, path(row), show(row), perm, ask?: { label, key, number? }, danger?, confirm? }
//   flags:   create / edit / remove default to the "<perm>:write" permission; set false to switch off,
//            or true for self-service screens that have no permission module.
// selfResources: the same kind of screen, for the signed-in employee's own records.
// routes:        screens that have their own page.
import * as auth from './auth'
import * as dashboard from './dashboard'
import * as employee from './employee'
import * as leave from './leave'
import * as attendance from './attendance'
import * as payroll from './payroll'
import * as recruitment from './recruitment'
import * as expense from './expense'
import * as performance from './performance'
import * as admin from './admin'
import * as notifications from './notifications'
import * as selfservice from './selfservice'

// Order decides the order of the CRUD routes; the sidebar groups them by each screen's `group`.
export const resources = [employee, leave, attendance, payroll, recruitment, expense, performance, admin].flatMap(
  (m) => m.resources,
)
export const selfResources = selfservice.selfResources
export const featureRoutes = [auth, dashboard, payroll, attendance, selfservice, notifications].flatMap((m) => m.routes)
