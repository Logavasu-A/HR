import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { basename, dirname, join, resolve } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'

const origin = process.env.HRMS_TEST_FRONTEND_ORIGIN || 'http://127.0.0.1:5174'
assert.ok(['http://127.0.0.1:5174', 'http://localhost:5173'].includes(origin), 'Use a verified local staging frontend')
const password = process.env.HRMS_ISOLATION_OWNER_PASSWORD
const fixturePassword = process.env.HRMS_ISOLATION_FIXTURE_PASSWORD
assert.ok(password, 'Set the owner password for the local staging restore')
const temp = resolve(tmpdir())
const profile = await mkdtemp(join(temp, 'hrms-admin-view-'))
const chrome = process.env.HRMS_TEST_CHROME || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const browser = spawn(chrome, [
  '--headless=new', '--no-first-run', '--no-default-browser-check', '--disable-background-networking',
  '--disable-component-update', '--disable-sync', '--remote-debugging-address=127.0.0.1',
  '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank',
], { windowsHide: true, stdio: 'ignore' })
let browserError
browser.on('error', (error) => { browserError = error })
let socket
let call

async function until(test, message) {
  const end = Date.now() + 12000
  while (Date.now() < end) {
    if (browserError) throw browserError
    const result = await test()
    if (result) return result
    await delay(60)
  }
  throw new Error(message)
}

try {
  const port = await until(async () => {
    const data = await readFile(join(profile, 'DevToolsActivePort'), 'utf8').catch(() => '')
    return Number(data.split(/\r?\n/)[0])
  }, 'Headless Chrome did not expose a local debugging port')
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(r => r.json())
  const tab = tabs.find(item => item.type === 'page')
  assert.ok(tab, 'Headless Chrome should have a page')
  socket = new WebSocket(tab.webSocketDebuggerUrl)
  await once(socket, 'open')
  const pending = new Map()
  let id = 0
  let held
  let holdAlpha = false
  socket.addEventListener('message', ({ data }) => {
    const packet = JSON.parse(data)
    if (packet.id) {
      const request = pending.get(packet.id)
      if (!request) return
      pending.delete(packet.id)
      if (packet.error) request.reject(new Error(packet.error.message))
      else request.resolve(packet.result)
    } else if (packet.method === 'Fetch.requestPaused') {
      const paused = packet.params
      if (holdAlpha && new URL(paused.request.url).searchParams.get('size') === '10') {
        holdAlpha = false
        held = paused.requestId
      } else {
        call('Fetch.continueResponse', { requestId: paused.requestId }).catch(() => {})
      }
    }
  })
  call = (method, params = {}) => new Promise((resolve, reject) => {
    const requestId = ++id
    pending.set(requestId, { resolve, reject })
    socket.send(JSON.stringify({ id: requestId, method, params }))
  })
  const evaluate = async expression => {
    const result = await call('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || 'Browser assertion failed')
    return result.result.value
  }
  const waitFor = (condition, message) => until(() => evaluate(condition), message)
  const input = (selector, value, event = 'input') => evaluate(`(() => {
    const field = document.querySelector(${JSON.stringify(selector)});
    field.value = ${JSON.stringify(value)};
    field.dispatchEvent(new Event(${JSON.stringify(event)}, { bubbles: true }));
  })()`)
  const click = selector => evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`)
  const key = async (key, windowsVirtualKeyCode) => {
    for (const type of ['keyDown', 'keyUp']) {
      await call('Input.dispatchKeyEvent', { type, key, code: key, windowsVirtualKeyCode })
    }
  }
  const assertFocus = async (selector, token = '--primary') => {
    const result = await evaluate(`(() => {
      const element = document.querySelector(${JSON.stringify(selector)});
      if (!element || element !== document.activeElement || !element.matches(':focus-visible')) {
        return { active: document.activeElement.outerHTML.slice(0, 240), matches: element?.matches(':focus-visible') };
      }
      const style = getComputedStyle(element);
      const color = getComputedStyle(document.documentElement).getPropertyValue(${JSON.stringify(token)}).trim();
      const probe = document.createElement('span');
      probe.style.color = color;
      document.body.append(probe);
      const expected = getComputedStyle(probe).color;
      probe.remove();
      return { outline: style.outline, expected };
    })()`)
    assert.deepEqual(result, { outline: `${result.expected} solid 2px`, expected: result.expected },
      `Expected a visible keyboard outline on ${selector}`)
  }
  const selectCompany = async name => {
    const company = await evaluate(`Array.from(document.querySelector('#owner-workspace-company').options)
      .find(option => option.textContent.trim() === ${JSON.stringify(name)}).value`)
    await input('#owner-workspace-company', company, 'change')
  }
  const selectWorkspace = async (name, reason) => {
    await selectCompany(name)
    await input('#owner-workspace-reason', reason)
    await click('.workspace-switch button[type="submit"]')
  }
  const rows = code => `document.querySelector('#workspace-content')?.textContent.includes('${code}')`
  const selected = name => `document.querySelector('.workspace-name')?.textContent === '${name}'`

  await call('Page.enable')
  await call('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false })
  await call('Page.navigate', { url: origin + '/platform/login' })
  await waitFor("document.querySelector('#owner-username') !== null", 'Owner sign-in did not render')
  await evaluate("localStorage.setItem('hrms_token', 'ordinary-company-session-sentinel')")
  await input('#owner-username', 'logavasu')
  await input('#owner-password', password)
  await click('form button.primary')
  await waitFor(`document.querySelector('a[href="/platform/workspace"]') !== null`, 'Control Tower did not show Admin View')
  await waitFor("Array.from(document.querySelectorAll('.tower-table a')).some(a => a.textContent === 'Isolation Alpha')",
    'The global comparison did not display the Alpha company')
  for (const [route, ready] of [
    ['/platform/control-tower', '.tower-table'],
    ['/platform/companies', '.owner-directory'],
    ['/platform/workspace/dashboard', '#workspace-picker'],
    ['/platform/audit', '.audit-scroll'],
    ['/platform/access', '.access-table'],
  ]) {
    await call('Page.navigate', { url: origin + route })
    await waitFor(`document.querySelector(${JSON.stringify(ready)}) !== null`, `Layout did not load for ${route}`)
    for (const width of [1440, 1280, 1024, 768, 390, 320]) {
      await call('Emulation.setDeviceMetricsOverride', { width, height: 900, deviceScaleFactor: 1, mobile: false })
      const misaligned = await evaluate(`(() => {
        const main = document.querySelector('#workspace-content')
        const header = main.querySelector('.platform-page-header')
        const layout = main.getBoundingClientRect()
        const titleBox = header.getBoundingClientRect()
        const padding = parseFloat(getComputedStyle(main).paddingLeft)
        if (Math.abs(titleBox.left - layout.left - padding) > 2 || document.documentElement.scrollWidth > innerWidth + 1) {
          return 'page gutter or horizontal overflow'
        }
        const forms = main.querySelectorAll('.tower-period, .audit-filters, .owner-filters, .access-filters, .workspace-switch')
        for (const form of forms) {
          const bounds = form.getBoundingClientRect()
          for (const input of form.querySelectorAll('input, select, button')) {
            const box = input.getBoundingClientRect()
            if (box.width && (box.left < bounds.left - 1 || box.right > bounds.right + 1)) return input.id || input.textContent || input.placeholder
          }
        }
        return ''
      })()`)
      assert.equal(misaligned, '', `${route} alignment at ${width}px: ${misaligned}`)
    }
  }
  await call('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false })
  await call('Page.navigate', { url: origin + '/platform/control-tower' })
  await waitFor("document.querySelector('.tower-table') !== null", 'Control Tower did not return after alignment checks')
  console.log('PASS all five Super Admin pages align at desktop, tablet and mobile widths')
  await key('Tab', 9)
  for (const [selector, token] of [
    ['.sidebar a[href="/platform/companies"]', '--ink-accent'],
    ['.tower-period input', '--primary'],
    ['.tower-period select', '--primary'],
    ['.tower-period button', '--primary'],
  ]) {
    await evaluate(`document.querySelector(${JSON.stringify(selector)}).focus()`)
    await assertFocus(selector, token)
  }
  assert.equal(await evaluate("document.querySelectorAll('.chart-row').length"), 12)
  await input('select[aria-label="Activity metric"]', 'leave', 'change')
  await waitFor("document.querySelector('.chart-row')?.getAttribute('aria-label').includes('Approved')",
    'The monthly chart did not update its series when the metric changed')
  if (process.env.HRMS_TEST_AUDIT === 'true') {
    await evaluate(`(() => {
      const original = window.fetch;
      window.fetch = async (...args) => {
        if (String(args[0]).startsWith('/api/platform/audit?')) {
          await new Promise(resolve => { window.releaseAudit = resolve });
          window.fetch = original;
        }
        return original(...args);
      };
    })()`)
    await click('a[href="/platform/audit"]')
    await waitFor("document.querySelector('[data-state=loading]')?.textContent.includes('Loading audit events') && !!window.releaseAudit",
      'Audit Center did not show its loading state')
    await evaluate('window.releaseAudit()')
    await waitFor("document.querySelector('.audit-scroll tbody tr') !== null", 'Audit Center did not show recorded events')
    assert.ok(await evaluate("getComputedStyle(document.querySelector('.audit-scroll tbody button')).whiteSpace === 'nowrap'"))
    await input('#audit-source', 'FINANCE', 'change')
    await input('#audit-search', 'CHANGE_EXECUTED')
    await click('.audit-filters button.primary')
    await waitFor("document.querySelector('.audit-scroll tbody')?.textContent.toLowerCase().includes('change executed')",
      'Financial audit filter did not return executed approvals')
    await click('.audit-scroll tbody tr button')
    await waitFor("document.querySelector('.audit-timeline')?.textContent.toLowerCase().includes('change approved')",
      'Approval timeline did not show its decision')
    assert.ok(await evaluate("!document.querySelector('.audit-detail').textContent.includes('LOCAL-CONTROL-')"))
    await input('#audit-search', 'missing-audit-ui-test-9b065')
    await click('.audit-filters button.primary')
    await waitFor("document.querySelector('[data-state=empty]')?.textContent.includes('No audit events')",
      'Audit Center did not show a filtered empty state')
    await input('#audit-from', '2099-01-01')
    await click('.audit-filters button.primary')
    await waitFor("document.querySelector('[data-state=error]')?.textContent.includes('could not be loaded')",
      'Audit Center did not show an invalid range error')
    await click('[data-state=error] .state-actions button:last-child')
    await waitFor("document.querySelector('.audit-scroll tbody tr') !== null", 'Resetting filters did not restore audit results')
    await click('#platform-navigation a[href="/platform/control-tower"]')
    await waitFor("document.querySelector('.tower-table')?.textContent.includes('Isolation Alpha')",
      'Navigation back to the Control Tower failed')
    console.log('PASS Audit Center navigation, finance filters and redacted approval history')
  }
  if (process.env.HRMS_TEST_FINANCE === 'true') {
    const unlock = "Array.from(document.querySelectorAll('button')).find(b => b.textContent.trim() === 'Unlock payroll totals')"
    await waitFor(`!!(${unlock})`, 'Global payroll totals were not locked')
    await evaluate(`(${unlock}).click()`)
    await input('#tower-password', password)
    await evaluate("document.querySelector('#tower-password').form.requestSubmit()")
    await waitFor("!document.querySelector('#tower-password') && document.querySelector('.tower-table')?.textContent.includes('Isolation Alpha')",
      'Global payroll did not reload after verification')
  }
  await input('#tower-month', '11', 'change')
  await click('.tower-period button')
  await waitFor("location.search.includes('month=11') && document.querySelectorAll('.chart-row').length === 1",
    'The monthly period did not reload the dashboard')
  await evaluate("document.querySelector('[aria-label=\"View metrics for Isolation Alpha\"]').click()")
  await waitFor("document.querySelector('h1')?.textContent === 'Isolation Alpha' && document.querySelector('.tower-table')?.textContent.includes('Alpha Operations')",
    'Alpha drill-down did not load its department data')
  assert.ok(await evaluate("!document.querySelector('.tower-table').textContent.includes('Bravo Operations')"))
  await click('a.tower-back')
  await waitFor("document.querySelector('h1')?.textContent === 'Global Control Tower' && document.querySelector('.tower-table')?.textContent.includes('Isolation Bravo')",
    'Returning to the global comparison did not restore the company list')
  console.log('PASS Control Tower period filters, Alpha department drill-down, and return to the global comparison')
  await waitFor("document.querySelector('#platform-navigation a[href=\"/platform/audit\"]') !== null", 'Super Admin sidebar did not load')
  await click('#platform-navigation a[href="/platform/companies"]')
  await waitFor("document.querySelector('input[aria-label=\"Search companies\"]') !== null", 'Company directory did not load')
  await input('input[aria-label="Search companies"]', 'isolation-alpha')
  await evaluate("document.querySelector('input[aria-label=\"Search companies\"]').form.requestSubmit()")
  await waitFor("Array.from(document.querySelectorAll('tbody a')).some(a => a.textContent.trim() === 'Isolation Alpha')",
    'Company directory did not show a direct Alpha workspace link')
  await click('a[aria-label="Open Isolation Alpha workspace"]')
  await waitFor("document.querySelector('#owner-workspace-company')?.options.length > 2", 'Company switcher did not render')
  assert.ok(await evaluate("document.querySelector('#platform-navigation [aria-current=page]')?.textContent.includes('Company workspaces')"))
  assert.ok(await evaluate("document.querySelector('#owner-workspace-company').selectedOptions[0].textContent.includes('Isolation Alpha')"))
  await selectWorkspace('Isolation Alpha', 'Browser verification for Alpha')
  await waitFor(`${selected('Isolation Alpha')} && document.querySelector('a[href="/platform/workspace/employees"]') !== null`,
    'Alpha company shell did not render')
  await click('a[href="/platform/workspace/company-details"]')
  await waitFor("document.querySelector('.company-facts')?.textContent.includes('isolation-alpha')",
    'Selected-company details did not show Alpha')
  assert.ok(await evaluate("!document.querySelector('.company-facts').textContent.includes('isolation-bravo')"))
  await click('a[href="/platform/workspace/employees"]')
  await waitFor(`${rows('VERIFY-ALPHA')} && !${rows('VERIFY-BRAVO')}`, 'Alpha directory was not scoped')
  assert.ok(await evaluate("document.querySelector('.main .workspace-mode').textContent.includes('logavasu')"))
  assert.ok(await evaluate("document.querySelector('#workspace-picker').getClientRects().length === 0"))
  assert.ok(await evaluate("document.querySelector('.menu-btn').getClientRects().length === 0"))
  await click('a[href="/platform/workspace/attendance"]')
  await waitFor("document.querySelector('#workspace-content h1')?.textContent.toLowerCase().includes('attendance')",
    'Attendance did not open inside Alpha Admin View')
  await click('a[href="/platform/workspace/employees"]')
  await waitFor(rows('VERIFY-ALPHA'), 'Alpha directory did not return after Attendance')
  await key('Tab', 9)
  await evaluate("document.querySelector('.action-menu-btn').focus()")
  await assertFocus('.action-menu-btn')
  await evaluate("new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))")
  await click('.action-menu-btn')
  await waitFor("document.querySelector('.action-menu-item') !== null", 'Row menu did not open')
  await key('ArrowDown', 40)
  await waitFor("document.querySelector('.action-menu-item:focus-visible') !== null", 'Arrow Down did not focus a row menu item')
  await assertFocus('.action-menu-item:focus-visible')
  await key('Escape', 27)
  console.log('PASS keyboard focus on sidebar, form controls, and the shared row action menu')
  console.log('PASS Companies opens the selected company with Attendance and Employees in the same console')
  if (process.env.HRMS_TEST_FINANCE === 'true') {
    await click('a[href="/platform/workspace/payroll-runs"]')
    await waitFor("document.querySelector('dialog[open] #finance-password') !== null", 'Workspace payroll did not request verification')
    await input('#finance-password', password)
    await click('dialog[open] form button.primary')
    await waitFor("!document.querySelector('dialog[open] #finance-password') && document.querySelector('main h1')?.textContent.includes('Payroll runs')",
      'Workspace payroll did not finish step-up')
    await click('a[href="/platform/workspace/employees"]')
    await waitFor(rows('VERIFY-ALPHA'), 'Alpha directory did not return after payroll verification')
    console.log('PASS Control Tower payroll and workspace payroll require separate browser verification')
  }

  await call('Fetch.enable', { patterns: [{ urlPattern: `${origin}/api/core/employees?*`, requestStage: 'Response' }] })
  holdAlpha = true
  await input('select[aria-label="Rows per page"]', '10', 'change')
  await until(() => held, 'The browser did not start the delayed Alpha query')
  await click('.picker-toggle')
  assert.ok(await evaluate("document.activeElement.id === 'workspace-company-search'"))
  await selectCompany('Isolation Bravo')
  await input('#owner-workspace-reason', 'Browser verification for Bravo')
  await evaluate(`(() => {
    document.querySelector('.workspace-switch button').click();
    window.staleRows = false;
    Promise.resolve().then(() => {
      window.staleWatch = new MutationObserver(() => {
        if (${rows('VERIFY-ALPHA')}) window.staleRows = true;
      });
      window.staleWatch.observe(document.body, { childList: true, subtree: true, characterData: true });
    });
  })()`)
  await waitFor(`!${rows('VERIFY-ALPHA')}`, 'Alpha rows remained visible during the switch')
  await waitFor(`${selected('Isolation Bravo')} && ${rows('VERIFY-BRAVO')} && !${rows('VERIFY-ALPHA')}`, 'Bravo directory did not load')
  await call('Fetch.continueResponse', { requestId: held }).catch(() => {})
  await call('Fetch.disable')
  await evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))')
  assert.ok(await evaluate(`!${rows('VERIFY-ALPHA')} && ${rows('VERIFY-BRAVO')} && !window.staleRows`))
  console.log('PASS Alpha to Bravo clears the prior screen and cancels or discards a delayed Alpha response')

  await call('Page.reload')
  await waitFor(`${selected('Isolation Bravo')} && ${rows('VERIFY-BRAVO')} && !${rows('VERIFY-ALPHA')}`, 'Bravo did not resume after refresh')
  assert.equal(await evaluate("localStorage.getItem('hrms_token')"), 'ordinary-company-session-sentinel')
  console.log('PASS refresh resumes Bravo and leaves company login storage intact')
  await waitFor("document.querySelector('a[href=\"/platform/workspace/company-details\"]') !== null",
    'Company details navigation did not restore after reload')
  await click('a[href="/platform/workspace/company-details"]')
  await waitFor("document.querySelector('.company-facts')?.textContent.includes('isolation-bravo')",
    'Selected-company details did not switch to Bravo')
  assert.ok(await evaluate("!document.querySelector('.company-facts').textContent.includes('isolation-alpha')"))
  console.log('PASS Company details follow the selected Admin View grant')
  await call('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: false })
  assert.ok(await evaluate('document.documentElement.scrollWidth <= window.innerWidth'))
  assert.ok(await evaluate("document.querySelector('#workspace-picker').getClientRects().length === 0"))
  await click('.picker-toggle')
  await input('#workspace-company-search', 'missing-company-ui-test')
  await waitFor("document.querySelector('#workspace-picker [role=status]')?.textContent.includes('No companies match')",
    'Mobile company switcher did not show its search empty state')
  await input('#workspace-company-search', 'isolation-alpha')
  assert.ok(await evaluate("Array.from(document.querySelector('#owner-workspace-company').options).some(o => o.textContent.includes('Isolation Alpha'))"))
  console.log('PASS mobile company switcher opens, searches, and preserves the current workspace')
  await click('.workspace-heading button')
  await waitFor("location.pathname === '/platform/control-tower' && document.querySelector('.tower') !== null",
    'Returning to the Control Tower did not work')
  await waitFor("document.querySelector('.tower-table')?.textContent.includes('Isolation Bravo')",
    'The global comparison did not return after leaving Admin View')
  assert.ok(await evaluate("document.documentElement.scrollWidth <= window.innerWidth && document.querySelector('.chart-scroll').scrollWidth > document.querySelector('.chart-scroll').clientWidth"))
  await click('.platform-shell .menu-btn')
  await waitFor("document.querySelector('.platform-shell .sidebar.open') !== null", 'Mobile platform sidebar did not open')
  await click('.platform-shell .menu-btn')
  assert.ok(await evaluate('document.documentElement.scrollWidth <= window.innerWidth'))
  if (fixturePassword) {
    await click('a[href="/platform/access"]')
    await waitFor("document.querySelector('.access-table')?.textContent.includes('isolation-security-admin')",
      'Global Super Admin Access Center did not list the security administrator')
    assert.ok(await evaluate("!!document.querySelector('[aria-label=\"Change role for isolation-security-admin\"]')"))
  }
  assert.ok(await evaluate(`(async () => {
    const token = sessionStorage.getItem('hrms_platform_token');
    const status = await fetch('/api/platform/workspace', { headers: { Authorization: 'Bearer ' + token } });
    const closed = status.ok && !(await status.text());
    await fetch('/api/platform/logout', { method: 'POST', headers: { Authorization: 'Bearer ' + token } });
    return closed;
  })()`))
  console.log('PASS narrow layout and return to Control Tower revoke the grant without requiring another sign-in')

  if (fixturePassword) {
    const signInPlatform = async username => {
      await call('Page.navigate', { url: origin + '/platform/login' })
      await waitFor("document.querySelector('#owner-username') !== null", 'Platform login did not render')
      await input('#owner-username', username)
      await input('#owner-password', fixturePassword)
      await click('form button.primary')
    }
    await signInPlatform('isolation-security-admin')
    await waitFor("location.pathname === '/platform/access' && document.querySelector('.access-table')?.textContent.includes('isolation-platform-admin')",
      'Security Administrator did not open Access Center')
    await input('#access-search', 'isolation-platform-candidate')
    await waitFor("document.querySelectorAll('.access-table tbody tr').length === 1 && document.querySelector('.access-table tbody').textContent.includes('isolation-platform-candidate')",
      'Access Center account search did not narrow the table')
    await click('[aria-label="Change role for isolation-platform-candidate"]')
    assert.ok(await evaluate("!document.querySelector('#platform-role option[value=SECURITY_ADMINISTRATOR]')"))
    await evaluate("Array.from(document.querySelectorAll('button')).find(button => button.textContent.trim() === 'Sign out').click()")
    await waitFor("location.pathname === '/platform/login'", 'Security Administrator sign-out did not complete')

    await signInPlatform('isolation-platform-admin')
    await waitFor("location.pathname === '/platform/companies' && document.body.textContent.includes('Company directory')",
      'Platform Administrator did not open the registry')
    assert.ok(await evaluate("!document.querySelector('.owner-activity, a[href=\"/platform/workspace\"], a[href=\"/platform/access\"]')"))
    await call('Page.navigate', { url: origin + '/platform/access' })
    await waitFor("document.querySelector('[role=alert]')?.textContent.includes('Access denied')",
      'Platform Administrator did not see an access-denied message')
    await evaluate("Array.from(document.querySelectorAll('button')).find(button => button.textContent.trim() === 'Sign out').click()")
    await waitFor("location.pathname === '/platform/login'", 'Platform Administrator sign-out did not complete')
    console.log('PASS platform role landing pages, role controls and access-denied UI')

    await call('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false })
    for (const [account, visible, hidden] of [
      ['dept', 'VERIFY-ACCESS-PEER', 'VERIFY-ACCESS-REPORT'],
      ['report', 'VERIFY-ACCESS-DIRECT', 'VERIFY-ACCESS-INDIRECT'],
    ]) {
      await call('Page.navigate', { url: origin + '/login?company=isolation-access' })
      await waitFor("document.querySelector('#username') !== null", 'Company login did not render')
      await input('#username', `isolation.access.${account}`)
      await input('#password', fixturePassword)
      await click('form button.primary')
      await waitFor("location.pathname === '/my'", 'Scoped manager did not reach self-service')
      await call('Page.navigate', { url: origin + '/employees' })
      await waitFor(`document.body.textContent.includes('${visible}')`, 'Scoped manager directory did not load')
      assert.ok(await evaluate(`!document.body.textContent.includes('${hidden}')`))
      await evaluate(`(async () => {
        await fetch('/api/auth/logout', { method: 'POST',
          headers: { Authorization: 'Bearer ' + localStorage.getItem('hrms_token') } });
        localStorage.removeItem('hrms_token');
        localStorage.removeItem('hrms_user');
      })()`)
    }
    console.log('PASS department and reporting-manager directories show only permitted employee rows')
  }
} finally {
  if (call && socket?.readyState === WebSocket.OPEN) {
    await Promise.race([call('Browser.close').catch(() => {}), delay(800)])
    socket.close()
  }
  browser.kill()
  if (browser.exitCode === null && browser.signalCode === null && browser.pid) {
    await Promise.race([once(browser, 'exit'), delay(2000)])
  }
  if (dirname(resolve(profile)) === temp && basename(profile).startsWith('hrms-admin-view-')) {
    await rm(profile, { recursive: true, force: true, maxRetries: 2 }).catch(() => {})
  }
}
