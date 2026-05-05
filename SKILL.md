---
name: vibium-java-test
description: Run the Vibium Java API regression suite or bug hardening suite. Tests all public methods across Page, Element, Browser, BrowserContext, Keyboard, Mouse, Touch, Clock, and Dialog APIs using a no-build-tool single-file Java runner. Labels each test PASS/FAIL/SKIP with known-bug notes.
---

# Vibium Java API Test Suite

Tests all public methods in the Vibium Java language bindings (`com.vibium:vibium:26.3.18`).
Two modes: full regression suite (`VibiumJavaApiTests`) and bug hardening across 4 sites (`VibiumBugHardening`).

## Repository

[github.com/lana-20/vibium-java-tests](https://github.com/lana-20/vibium-java-tests)

## Project directory

All commands run from:
```sh
cd ~/vibium-java-tests
```

## Files

| File | Purpose |
|---|---|
| `VibiumJavaApiTests.java` | Main regression suite — 162 tests across 24 sections |
| `VibiumBugHardening.java` | Hardens all 10 confirmed bugs across multiple sites/contexts |
| `vibium-26.3.18.jar` | Vibium Java client (not committed — download from Maven Central) |
| `gson-2.11.0.jar` | JSON dependency (not committed — download from Maven Central) |

## Compile

Check if `.class` files are present and newer than `.java` sources. If not, compile both:
```sh
javac -cp ".:vibium-26.3.18.jar:gson-2.11.0.jar" VibiumJavaApiTests.java VibiumBugHardening.java
```

## Sections covered (VibiumJavaApiTests)

| Section | Tests | Methods |
|---|---|---|
| Navigation | 8 | go, url, title, content, back, forward, reload, waitForLoad |
| Content & Scripting | 2 | setContent, addStyle |
| Evaluate | 8 | evaluate (string/number/boolean/null/DOM query/mutation/array) |
| Find / FindAll / Wait | 9 | find (CSS/role/text/placeholder/label/xpath), findAll, waitFor |
| Element State | 21 | text, innerText, html, attr, getAttribute, value, isVisible, isHidden, isEnabled, isChecked, isEditable, role, info, bounds, boundingBox, screenshot, find, findAll, waitUntil |
| Element Actions | 14 | click, dblclick, fill, type, clear, press, check, uncheck, selectOption, focus, hover, scrollIntoView, dispatchEvent, tap |
| Keyboard | 3 | type, press, down/up |
| Mouse | 5 | click, move, down/up, button, wheel |
| Touch | 1 | tap |
| Dialog | 7 | accept, dismiss, message, type (alert/prompt), accept(text), defaultValue |
| Console Events | 3 | onConsole, ConsoleMessage.type, collectConsole/consoleMessages |
| Error Events | 0 | (2 skipped — B8 bug) |
| Network Listeners | 6 | onRequest, Request.url/method, onResponse, Response.url/status |
| Route | 0 | (7 skipped — B10/issue #128) |
| Screenshot | 4 | screenshot, fullPage, clip, PNG magic bytes |
| PDF | 1 | pdf (signature check) |
| Viewport & Window | 5 | setViewport, viewport, window, setWindow, emulateMedia |
| Accessibility | 5 | a11yTree, a11yTree.children, a11yTree.role, a11yTree(A11yOptions), el.label |
| Clock | 5 | install, fastForward, runFor, pauseAt+resume, setTimezone |
| BrowserContext | 9 | context, id, cookies, setCookies, Cookie fields, clearCookies, storage, clearStorage, addInitScript |
| Multi-Page | 7 | newPage, pages, newContext, context.newPage, onPage, frames, mainFrame |
| Expose | 0 | (3 skipped — B7 bug) |
| Scroll | 3 | scroll, scroll(down), scroll(up) |
| Page Misc | 11 | id, bringToFront, sleep, keyboard/mouse/touch/clock/context accessors, removeAllListeners, setGeolocation |

## Running

**Full regression suite:**
```sh
java -cp ".:vibium-26.3.18.jar:gson-2.11.0.jar" VibiumJavaApiTests
```

**Bug hardening (all 10 bugs, 4 sites):**
```sh
java -cp ".:vibium-26.3.18.jar:gson-2.11.0.jar" VibiumBugHardening
```

**Single bug hardening (B1–B10):**
Run VibiumBugHardening and filter output to that bug's section:
```sh
java -cp ".:vibium-26.3.18.jar:gson-2.11.0.jar" VibiumBugHardening 2>&1 | awk '/╔══ B<N>:/{p=1} /╔══ B/{if(p && !/B<N>:/)p=0} p'
```
Replace `<N>` with the bug number (1–10).

## Reporting

Parse the output from VibiumJavaApiTests. Produce a result line per test:

- `PASS [test name]` — test passed
- `FAIL [test name]` — unexpected failure; include the error message
- `SKIP [test name] — BUG: …` — skipped due to a confirmed bug
- `SKIP [test name]` — skipped for documented behavioral reason

After all sections, print a summary:

```
Section              Total  Pass  Fail  Skip
Navigation           8      7     0     1
Content & Scripting  2      2     0     0
Evaluate             8      8     0     0
Find / FindAll       9      8     0     1
Element State        21     21    0     0
Element Actions      14     13    0     2 (bugs)
...
──────────────────────────────────────────
TOTAL                162    136   0     26
```

For VibiumBugHardening, report each bug section with confirmed/unexpected counts:
```
B1  page.waitForURL()            8 probes — all BUG (confirmed)
B2  page.addScript()             9 probes — all BUG (confirmed)
...
──────────────────────────────────
Total confirmed: 87 / 87
```

## Known bugs (skipped in regression suite)

| Bug | Method(s) | Error | Hardening probes |
|---|---|---|---|
| B1 | `page.waitForURL()` | "pattern is required" for all 8 format variants | 8/8 |
| B2 | `page.addScript()` | script value null in all contexts (use `context.addInitScript()` instead) | 9/9 |
| B3 | `page.waitForFunction()` | times out even for trivially-true expressions (`true`, `1+1===2`) | 10/10 |
| B4 | `el.dispatchEvent()` | onclick and addEventListener both unresponsive; `el.click()` works | 9/9 |
| B5 | `el.highlight()` | "Unknown command 'vibium:element.highlight'" on all elements | 9/9 |
| B6 | `el.dragTo(Element)` | "dragTo requires 'target' parameter" with correct Element arg | 7/7 |
| B7 | `page.expose()` | function is `undefined` in all page contexts after expose | 7/7 |
| B8 | `onError()`, `collectErrors()` | uncaught errors never forwarded (setTimeout, script injection, Promise.reject, ErrorEvent) | 10/10 |
| B9 | `clock.setFixedTime()`, `clock.pauseAt()`, `clock.setSystemTime()`, `ClockOptions.time()` | "time is required" for all string formats; ClockOptions.time() silently ignored | 13/13 |
| B10 | `page.setHeaders()` | server deadlock on subsequent `page.go()` — same root cause as route issue #128 | 4/4 |

## Baseline

Confirmed across two independent runs:

| Suite | Pass | Fail | Skip | Total |
|---|---|---|---|---|
| VibiumJavaApiTests | 136 | 0 | 26 | 162 |
| VibiumBugHardening | 87 confirmed | 0 unexpected | — | 87 |

## Input

If the user passes `harden`, run VibiumBugHardening.
If the user passes `harden B<n>` (e.g. `harden B4`), run VibiumBugHardening and show only that bug's section.
If the user passes a section name (e.g. `dialog`, `clock`, `network`), run VibiumJavaApiTests and highlight that section's output.
If no argument, run VibiumJavaApiTests and produce the full summary.
