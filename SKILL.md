---
name: vibium-java-test
description: Run the Vibium Java API regression suite or bug hardening suite. Tests all public methods across Page, Element, Browser, BrowserContext, Keyboard, Mouse, Touch, Clock, and Dialog APIs using a no-build-tool single-file Java runner. Labels each test PASS/FAIL/SKIP with known-bug notes.
---

# Vibium Java API Test Suite

Tests all public methods in the Vibium Java language bindings (`com.vibium:vibium:26.5.31`).
Two modes: full regression suite (`VibiumJavaApiTests`) and bug hardening across 6 sites (`VibiumBugHardening`).

**v26.5.31 status:** 140 PASS / 0 FAIL / 22 SKIP. B4/B5/B6 SKIP annotations removed, tests now active and passing. Hardening: 52 confirmed / 60 unexpected passes. B1, B2, B8, B9 partially fixed; B3, B7, B10 still broken.

**macOS PATH note:** on macOS the Python vibium client can shadow the npm binary. Prefix PATH when running: `PATH="/usr/local/bin:$PATH" java -cp ...`

## Repository

[github.com/lana-20/vibium-java-test](https://github.com/lana-20/vibium-java-test)

## Project directory

All commands run from:
```sh
cd ~/vibium-java-test
```

## Files

| File | Purpose |
|---|---|
| `VibiumJavaApiTests.java` | Main regression suite — 162 tests across 24 sections |
| `VibiumBugHardening.java` | Hardens all 10 confirmed bugs across multiple sites/contexts |
| `vibium-26.5.31.jar` | Vibium Java client (not committed — download from Maven Central) |
| `gson-2.11.0.jar` | JSON dependency (not committed — download from Maven Central) |

## Compile

Sources live in `src/`; compiled classes go to `out/`. Recompile when sources change:
```sh
javac -cp ".:vibium-26.5.31.jar:gson-2.11.0.jar" -d out src/VibiumJavaApiTests.java src/VibiumBugHardening.java
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
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" VibiumJavaApiTests
```

**Bug hardening (all 10 bugs, 6 sites):**
```sh
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" VibiumBugHardening
```

**Single bug hardening (B1–B10):**
```sh
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" VibiumBugHardening B3
```
Replace `B3` with `B1`–`B10`. Only that bug's section runs.

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
B2  page.addScript()             13 probes — all BUG (confirmed)
...
──────────────────────────────────
Total confirmed: 105 / 105
```

## Known bugs (skipped in regression suite)

| Bug | Method(s) | v26.5.31 status | Notes |
|---|---|---|---|
| B1 | `page.waitForURL()` | **PARTIAL** (#129/#167) | URL/glob fixed; `**/*.html` (path-sep glob) and regex `.*x.*` still fail |
| B2 | `page.addScript()` | **PARTIAL** (#130/#167) | `setContent→addScript→evaluate` works; `addScript→go()→evaluate` still null — use `context.addInitScript()` for cross-navigation persistence |
| B3 | `page.waitForFunction()` | **STILL BROKEN** | Java client wraps ALL expressions (bare and lambda) before sending → engine double-wraps → `SyntaxError: Unexpected token ')'`; confirmed via B3Repro.java (2026-06-06); not addressed in #167 |
| B4 | `el.dispatchEvent()` | **FIXED** ✓ (#132/#167) | `eventType` param key fix; all 11 hardening probes PASS |
| B5 | `el.highlight()` | **FIXED** ✓ (#133/#167) | Engine command implemented; all 11 probes PASS |
| B6 | `el.dragTo(Element)` | **FIXED** ✓ (#134/#167) | Nested `target` param fix; "requires target parameter" error gone; 3 residual failures are site-specific element issues (zero size, obscured), not B6 |
| B7 | `page.expose()` | **DEFERRED** (#135) | Java `Function` callback incompatible with engine's fn-string injection; needs API redesign |
| B8 | `onError()`, `collectErrors()` | **PARTIAL** (#136/#167) | `setTimeout`/dynamic script errors forwarded; `window.ErrorEvent` dispatch and unhandled Promise rejection not captured (different BiDi event types) |
| B9 | `clock.setFixedTime()`, `clock.pauseAt()`, `clock.setSystemTime()`, `ClockOptions.time()` | **PARTIAL** (#137/#167) | ISO-8601 and epoch-ms strings accepted; human-readable `"Month DD, YYYY"` intentionally unsupported; `ClockOptions.time()` value still ignored (off by 1 year) — genuine remaining bug |
| B10 | `page.setHeaders()` | **DEFERRED** (#128) | Threading deadlock; same root cause as route/dialog deadlock |

B4, B5, B6 SKIP annotations removed — tests now active and passing.

## Baseline

| Suite | Version | Pass | Fail | Skip | Total | Notes |
|---|---|---|---|---|---|---|
| VibiumJavaApiTests | v26.3.18 | 136 | 0 | 26 | 162 | Original baseline |
| VibiumJavaApiTests | v26.5.31 | 140 | 0 | 22 | 162 | B4/B5/B6 SKIP→PASS; src/→out/ compile structure |
| VibiumBugHardening | v26.3.18 | — | — | — | 112 | 112 confirmed / 0 unexpected |
| VibiumBugHardening | v26.5.31 | — | — | — | 112 | 52 confirmed / 60 unexpected passes (B4/B5/B6 fully fixed; B1/B2/B8/B9 partial) |

## Last Run Results

**Run date:** 2026-06-06 · **vibium:** v26.5.31 · **140 PASS · 0 FAIL · 22 SKIP**

```
Section              Total  Pass  Fail  Skip  Notes
─────────────────────────────────────────────────────────────────
Navigation           9      8     0     1     waitForURL (#129, partial fix)
Content & Scripting  2      1     0     1     addScript (#130, partial fix)
Evaluate             8      8     0     0
Find / FindAll       10     7     0     3     findAll timeout; waitForFunction ×2 (#131, Java client conflict)
Element State        21     21    0     0
Element Actions      18     18    0     0     B4/B5/B6 all PASS
Keyboard             3      3     0     0
Mouse                5      5     0     0
Touch                1      1     0     0
Dialog               7      7     0     0
Console Events       3      3     0     0
Error Events         2      0     0     2     onError/collectErrors (#136, partial fix)
Network Listeners    7      6     0     1     setHeaders (#128, deferred)
Route                7      0     0     7     deadlock (#128, deferred)
Screenshot           4      4     0     0
PDF                  1      1     0     0
Viewport & Window    5      5     0     0
Accessibility        5      5     0     0
Clock                8      4     0     4     setFixedTime/pauseAt/setSystemTime/ClockOptions.time (#137, partial fix)
BrowserContext       9      9     0     0
Multi-Page           7      7     0     0
Expose               3      0     0     3     expose() (#135, deferred)
Scroll               3      3     0     0
Page Misc            11     11    0     0
─────────────────────────────────────────────────────────────────
TOTAL                162    140   0     22
```

**Release note verification (v26.5.31):** All 22 SKIPs confirmed valid.
- B1/B2/B8/B9: release claims fixed — actual fix is partial; our tests target remaining broken paths
- B3 (waitForFunction): engine fix landed (#163) but Java client pre-wraps bare expressions → `SyntaxError`; not addressed in #167
- B7/B10: deferred in release notes — SKIPs correct
- B4/B5/B6: fully fixed — SKIP annotations removed, all PASS

## Input

If the user passes `harden`, run VibiumBugHardening.
If the user passes `harden B<n>` (e.g. `harden B4`), run VibiumBugHardening and show only that bug's section.
If the user passes a section name (e.g. `dialog`, `clock`, `network`), run VibiumJavaApiTests and highlight that section's output.
If no argument, run VibiumJavaApiTests and produce the full summary.
