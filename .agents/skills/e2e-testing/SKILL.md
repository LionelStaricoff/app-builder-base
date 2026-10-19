---
name: e2e-testing
description: 'End-to-end testing of web applications through a real Firefox browser using the FoxMCP server. Use when the user asks for e2e tests, browser tests, "probar en el navegador", "test end to end", verifying UI flows, clicks, forms, screenshots of the running app, or network request inspection.'
---

# E2E Testing with FoxMCP

Run end-to-end tests against the running application by driving a real Firefox
browser through the **FoxMCP** MCP server (`http://localhost:3000/mcp`).

## Prerequisites

1. FoxMCP server running: `curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:3000/mcp` → `200`.
2. The browser extension connected: call `debug_websocket_status` first. Every
   other tool times out if the extension is not connected — if the status says
   disconnected, stop and tell the user to open Firefox with the FoxMCP extension.
3. The application under test running locally (e.g. Spring Boot on
   `http://localhost:8080`). Verify with `curl` before driving the browser.

## Core workflow

1. **Setup**: `create_window` (or `tabs_create` in an existing window) pointing
   at the app URL. Prefer a dedicated window so the test doesn't disturb the
   user's tabs.
2. **Navigate**: `navigation_go_to_url` for later navigations, `navigation_reload`
   to refresh, `navigation_back` / `navigation_forward` for history.
3. **Interact**:
   - `content_execute_script` to click, fill inputs, submit forms, or read DOM
     state. Prefer small, targeted scripts that RETURN a value to assert on
     (e.g. `document.querySelector('#error').textContent`).
   - `content_get_text` / `content_get_html` to inspect the rendered page.
   - `content_load_file` for file-upload inputs (returns a `file_id` to use in
     the upload flow; it does not submit the form).
4. **Assert**: check the return values of scripts/text extraction against the
   expected outcome (URL changed, element visible, message shown). Never assume
   a navigation finished — poll or re-check until the expected state appears.
5. **Evidence**: `tabs_capture_screenshot` to save a screenshot as proof of the
   final state when the user asks for visual verification.
6. **Cleanup**: `tabs_close` / `close_window` for any window or tab the test
   created.

## Network-level assertions

For flows where the important part is the HTTP traffic (Stripe webhooks, API
calls, redirects):

1. `requests_start_monitoring` → keep the returned `monitor_id`.
2. Perform the UI action that triggers the request.
3. `requests_list_captured` to confirm the request completed, then
   `requests_get_content` for full request/response bodies.
4. `requests_stop_monitoring` when done (drains gracefully).

## Good practices

- One flow per test: navigate → act → assert → cleanup.
- Make JS snippets self-contained and idempotent; the page may re-render
  (Thymeleaf full-page loads vs. JS-driven updates).
- Wait for async work: after submitting a form, re-fetch the page or poll the
  DOM instead of assuming an immediate result.
- Use the app's real selectors (ids, names, classes from the templates) rather
  than brittle XPath by position.
- If a tool times out, check `debug_websocket_status` before retrying — the
  extension disconnects when Firefox is closed.
- Report results as a checklist: flow, steps executed, assertion result, and
  screenshot path if captured.
