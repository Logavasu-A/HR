import assert from 'node:assert/strict'
import { randomBytes } from 'node:crypto'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { preview } from 'vite'

const require = createRequire(import.meta.url)
const { chromium } = require('playwright')
const { SMTPServer } = require('smtp-server')
const backend = new URL(process.env.HRMS_BROWSER_BACKEND)
assert.equal(backend.hostname, '127.0.0.1')
assert.equal(backend.protocol, 'http:')
const ownerPassword = process.env.HRMS_BROWSER_OWNER_PASSWORD
assert.ok(ownerPassword, 'Set the isolated fixture owner password')
const smtpPort = Number(process.env.HRMS_BROWSER_SMTP_PORT)
assert.ok(smtpPort > 1024 && smtpPort <= 65535)
const frontendPort = Number(process.env.HRMS_BROWSER_FRONTEND_PORT)
assert.ok(frontendPort > 1024 && frontendPort <= 65535)

const testInbox = 'first-admin@example.invalid'
const sender = 'hrms@example.invalid'
const companyCode = 'browser-' + randomBytes(4).toString('hex')
const company = 'Browser test company with an intentionally long name for narrow screens'
const adminPassword = randomBytes(18).toString('base64url')
const messages = []
const smtp = new SMTPServer({
  disabledCommands: ['AUTH', 'STARTTLS'],
  disableReverseLookup: true,
  onRcptTo(address, _session, done) {
    done(address.address === testInbox ? undefined : new Error('Test address required'))
  },
  onData(stream, session, done) {
    let data = ''
    stream.setEncoding('utf8')
    stream.on('data', (chunk) => { data += chunk })
    stream.on('end', () => {
      messages.push({ data, envelope: session.envelope })
      done()
    })
    stream.on('error', done)
  },
})

await new Promise((resolve, reject) => {
  smtp.once('error', reject)
  smtp.listen(smtpPort, '127.0.0.1', resolve)
})

let front
let browser
try {
  front = await preview({
    configFile: false,
    root: fileURLToPath(new URL('..', import.meta.url)),
    preview: { host: '127.0.0.1', port: frontendPort, strictPort: true, proxy: { '/api': backend.origin } },
  })
  const origin = 'http://127.0.0.1:' + front.httpServer.address().port
  browser = await chromium.launch({ channel: 'chrome', headless: true, chromiumSandbox: true })
  const context = await browser.newContext()
  const page = await context.newPage()
  page.setDefaultTimeout(12000)
  const errors = []
  const apiFailures = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) {
      errors.push(message.text())
    }
  })
  page.on('response', (response) => {
    const url = new URL(response.url())
    if (url.origin === origin && url.pathname.startsWith('/api/') && response.status() >= 400) {
      apiFailures.push([url.pathname, response.status()])
    }
  })

  await page.goto(origin + '/platform/login')
  await page.getByLabel('Username', { exact: true }).fill('logavasu')
  await page.getByLabel('Password', { exact: true }).fill(ownerPassword)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.waitForURL('**/platform/companies')
  await page.getByRole('button', { name: 'Add company', exact: true }).click()
  await page.getByLabel('Company name', { exact: true }).fill(company)
  await page.getByLabel('Company code', { exact: true }).fill(companyCode)
  await page.getByLabel('Contact email', { exact: true }).fill(testInbox)
  await page.locator('#admin-email').fill(testInbox)
  await page.getByRole('button', { name: 'Create company and invite admin' }).click()
  await page.getByText('Company created and invitation sent.', { exact: true }).waitFor()
  const directory = page.getByRole('region', { name: 'Companies table' })
  const companyRow = directory.getByRole('row').filter({ hasText: company })
  await companyRow.waitFor()
  for (const width of [1280, 320]) {
    await page.setViewportSize({ width, height: 840 })
    const gutters = await directory.evaluate((region) => {
      const heading = region.parentElement.querySelector('h2')
      const cell = region.querySelector('th')
      const text = document.createRange()
      text.selectNodeContents(cell)
      return [heading.getBoundingClientRect().left, text.getBoundingClientRect().left]
    })
    assert.ok(Math.abs(gutters[0] - gutters[1]) < 1, 'Directory heading should align with its first column')
    const viewButton = companyRow.getByRole('button', { name: 'View ' + company })
    const bounds = await viewButton.boundingBox()
    assert.ok(bounds && bounds.x + bounds.width <= width, 'Company details should fit on narrow screens')
  }
  await page.setViewportSize({ width: 1280, height: 800 })
  assert.equal(messages.length, 1)
  assert.equal(messages[0].envelope.mailFrom.address, sender)
  assert.deepEqual(messages[0].envelope.rcptTo.map((item) => item.address), [testInbox])
  const link = messages[0].data.match(/https:\/\/hrms-test\.example\.invalid\/accept-admin-invite#[A-Za-z0-9_-]{43}/)?.[0]
  assert.ok(link, 'The SMTP message must contain an invitation to the configured HTTPS origin')
  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await page.waitForURL('**/platform/login')

  // The isolated local frontend serves the same invitation route over loopback.
  const invite = new URL(link)
  await page.goto(origin + invite.pathname + invite.hash)
  await page.getByLabel('Password (12 or more characters)', { exact: true }).fill(adminPassword)
  await page.getByLabel('Confirm password', { exact: true }).fill(adminPassword)
  const [accepted] = await Promise.all([
    page.waitForResponse((response) => new URL(response.url()).pathname === '/api/auth/accept-admin-invite'),
    page.getByRole('button', { name: 'Accept invitation', exact: true }).click(),
  ])
  assert.ok(accepted.ok(), 'Invitation acceptance returned HTTP ' + accepted.status())
  assert.equal((await accepted.json()).companyCode, companyCode)
  await page.getByRole('link', { name: 'Sign in', exact: true }).click()
  assert.equal(await page.getByLabel('Company code', { exact: true }).inputValue(), companyCode)
  await page.getByLabel('Email or username', { exact: true }).fill(testInbox)
  await page.getByLabel('Password', { exact: true }).fill(adminPassword)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.waitForURL('**/dashboard')
  await page.getByRole('heading', { name: 'Dashboard', exact: true }).waitFor()
  await page.getByRole('link', { name: 'Departments', exact: true }).click()
  await page.getByRole('heading', { name: 'Departments', exact: true }).waitFor()
  await page.getByRole('button', { name: 'New', exact: true }).click()
  await page.getByRole('textbox', { name: 'Name', exact: true }).fill('Browser department')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await page.getByRole('cell', { name: 'Browser department', exact: true }).waitFor()
  await page.setViewportSize({ width: 390, height: 844 })
  const menu = page.getByRole('button', { name: 'Menu', exact: true })
  const workspaceNav = page.getByRole('navigation', { name: 'Workspace navigation' })
  assert.equal(await menu.getAttribute('aria-expanded'), 'false')
  await menu.click()
  await workspaceNav.waitFor()
  assert.equal(await page.locator('[aria-controls="workspace-navigation"]').getAttribute('aria-expanded'), 'true')
  await page.keyboard.press('Escape')
  await workspaceNav.waitFor({ state: 'hidden' })
  assert.ok(await menu.evaluate((button) => button === document.activeElement))
  await page.goto(origin + '/attendance-summary')
  const emptyAttendance = page.getByRole('region', { name: 'Attendance summary' })
    .locator('.table-state-message').filter({ hasText: 'No active employees.' })
  await emptyAttendance.waitFor()
  assert.ok(await emptyAttendance.evaluate((message) => {
    const scroller = message.closest('.table-wrapper')
    const viewport = scroller.getBoundingClientRect()
    scroller.scrollLeft = scroller.scrollWidth
    const bounds = message.getBoundingClientRect()
    return bounds.left >= viewport.left && bounds.right <= viewport.right
  }), 'Empty attendance message should stay visible after horizontal scrolling')
  await page.goto(origin + '/expense-claims')
  const newExpense = page.getByRole('button', { name: 'New', exact: true })
  await newExpense.click()
  const expenseDialog = page.getByRole('dialog', { name: 'New · Expense claims' })
  await expenseDialog.waitFor()
  await page.getByRole('button', { name: 'Add row', exact: true }).click()
  const expenses = page.getByRole('region', { name: 'Expenses', exact: true })
  const amount = await expenses.getByLabel('Amount, row 1').boundingBox()
  assert.ok(amount && amount.width >= 105, 'Expense amount field should stay usable on a phone')
  await page.keyboard.press('Escape')
  await expenseDialog.waitFor({ state: 'hidden' })
  assert.ok(await newExpense.evaluate((button) => button === document.activeElement))
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.getByRole('link', { name: 'Employees', exact: true }).click()
  await page.setViewportSize({ width: 320, height: 568 })
  await page.getByRole('button', { name: 'New', exact: true }).click()
  const employeeDialog = page.getByRole('dialog', { name: 'New · Employees' })
  const saveEmployee = employeeDialog.getByRole('button', { name: 'Save', exact: true })
  assert.ok(await saveEmployee.evaluate((button) => button.getBoundingClientRect().bottom <= window.innerHeight),
    'Save should be visible on a phone even when the employee form scrolls')
  await page.getByLabel('First name').fill('Browser')
  await page.getByLabel('Last name').fill('Employee')
  await page.getByLabel('Date of joining').fill('2020-01-01')
  await saveEmployee.click()
  await page.getByRole('cell', { name: 'Browser Employee', exact: true }).waitFor()
  await page.setViewportSize({ width: 1280, height: 800 })
  for (const [route, title, endpoint] of [
    ['/employees', 'Employees', '/api/core/employees'],
    ['/attendance-summary', 'Attendance summary', '/api/attendance/summary'],
    ['/leave-applications', 'Leave applications', '/api/leave/applications'],
    ['/payroll-runs', 'Payroll runs', '/api/payroll/runs'],
    ['/job-applicants', 'Job applicants', '/api/recruitment/applicants'],
    ['/expense-claims', 'Expense claims', '/api/expense/claims'],
    ['/appraisals', 'Appraisals', '/api/performance/appraisals'],
    ['/roles', 'Roles', '/api/admin/roles'],
    ['/notifications', 'Notifications', '/api/notifications'],
    ['/audit-log', 'Audit log', '/api/admin/audit-logs'],
  ]) {
    const [response] = await Promise.all([
      page.waitForResponse((item) => new URL(item.url()).pathname === endpoint),
      page.goto(origin + route),
    ])
    assert.ok(response.ok(), endpoint + ' returned HTTP ' + response.status())
    await page.getByRole('heading', { name: title, exact: true }).waitFor()
  }
  await page.goto(origin + '/attendance-summary')
  const attendance = page.getByRole('region', { name: 'Attendance summary' })
  await attendance.getByRole('row').filter({ hasText: 'Browser Employee' }).waitFor()
  const present = attendance.getByRole('columnheader', { name: 'Present', exact: true })
  const numericEdges = () => present.evaluate((header) => {
    const cell = header.closest('table').tBodies[0].rows[0].cells[header.cellIndex]
    const value = document.createRange()
    value.selectNodeContents(cell)
    return {
      heading: header.querySelector('.th-inner > span').getBoundingClientRect().right,
      number: value.getBoundingClientRect().right,
      width: header.getBoundingClientRect().width,
    }
  })
  const beforeSort = await numericEdges()
  assert.ok(Math.abs(beforeSort.heading - beforeSort.number) < 1, 'Numeric heading and value should align')
  await present.press('Enter')
  assert.equal(await present.getAttribute('aria-sort'), 'ascending')
  assert.deepEqual(await numericEdges(), beforeSort, 'Sorting should preserve the column geometry')
  await page.setViewportSize({ width: 390, height: 844 })
  const [tableOverflow, pageOverflow] = await attendance.evaluate((region) => [
    region.scrollWidth - region.clientWidth,
    document.documentElement.scrollWidth - document.documentElement.clientWidth,
  ])
  assert.ok(tableOverflow > 0, 'Wide tables should scroll within their own region')
  assert.equal(pageOverflow, 0, 'Wide tables should fit within the page')
  await page.getByRole('button', { name: 'Log out', exact: true }).click()
  await page.waitForURL('**/login')
  assert.deepEqual(errors, [])
  assert.deepEqual(apiFailures, [])
  console.log('PASS browser onboarding, module page checks, table alignment at desktop and mobile widths, and logout')
} finally {
  await browser?.close()
  await front?.close()
  await new Promise((resolve) => smtp.close(resolve))
}
