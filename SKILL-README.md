# vibium-java-test

A Claude Code skill that runs the [Vibium](https://github.com/VibiumDev/vibium) Java API regression suite and bug hardening harness against `com.vibium:vibium:26.3.18`.

**Source:** [github.com/lana-20/vibium-java-tests](https://github.com/lana-20/vibium-java-tests)

## What it does

Compiles and runs two single-file Java test programs — no Maven, no Gradle, no IDE required. Labels each test `PASS`, `FAIL`, or `SKIP` (with bug notes), then prints a summary table. A second mode hardens each confirmed bug across 4 real sites and multiple page contexts.

## Coverage

### VibiumJavaApiTests — 162 tests

| Section | Tests | Key methods |
|---|---|---|
| Navigation | 8 | `go`, `back`, `forward`, `reload`, `url`, `title`, `content`, `waitForLoad` |
| Content & Scripting | 2 | `setContent`, `addStyle` |
| Evaluate | 8 | `evaluate` — string, number, boolean, null, DOM query/mutation, array |
| Find / FindAll / Wait | 9 | `find` (CSS/role/text/placeholder/label/xpath), `findAll`, `waitFor` |
| Element State | 21 | `text`, `innerText`, `html`, `attr`, `getAttribute`, `value`, `isVisible`, `isHidden`, `isEnabled`, `isChecked`, `isEditable`, `role`, `info`, `bounds`, `boundingBox`, `screenshot`, scoped `find`/`findAll`, `waitUntil` |
| Element Actions | 14 | `click`, `dblclick`, `fill`, `type`, `clear`, `press`, `check`, `uncheck`, `selectOption`, `focus`, `hover`, `scrollIntoView`, `dispatchEvent`, `tap` |
| Keyboard | 3 | `type`, `press`, `down`/`up` |
| Mouse | 5 | `click`, `move`, `down`/`up`, button options, `wheel` |
| Touch | 1 | `tap` |
| Dialog | 7 | `accept`, `dismiss`, `message`, `type` (alert/prompt), `accept(text)`, `defaultValue` |
| Console Events | 3 | `onConsole`, `ConsoleMessage.type`, `collectConsole`/`consoleMessages` |
| Network Listeners | 6 | `onRequest`, `Request.url`/`method`, `onResponse`, `Response.url`/`status` |
| Screenshot | 4 | plain, `fullPage`, `clip`, PNG magic-byte check |
| PDF | 1 | `pdf` (PDF header check) |
| Viewport & Window | 5 | `setViewport`, `viewport`, `window`, `setWindow`, `emulateMedia` |
| Accessibility | 5 | `a11yTree`, `.children`, `.role`, `a11yTree(A11yOptions)`, `el.label` |
| Clock | 5 | `install`, `fastForward`, `runFor`, `pauseAt`+`resume`, `setTimezone` |
| BrowserContext | 9 | `cookies`, `setCookies`, `Cookie` fields, `clearCookies`, `storage`, `clearStorage`, `addInitScript` |
| Multi-Page | 7 | `newPage`, `pages`, `newContext`, `context.newPage`, `onPage`, `frames`, `mainFrame` |
| Scroll | 3 | `scroll`, direction options |
| Page Misc | 11 | `id`, `bringToFront`, `sleep`, accessor methods, `removeAllListeners`, `setGeolocation` |

**Baseline: 136 pass / 0 fail / 26 skip**

### VibiumBugHardening — 87 probes

Tests each of the 10 confirmed bugs across 4 sites (example.com, books.toscrape.com, httpbin.org, en.wikipedia.org) and multiple page contexts.

**Baseline: 87 confirmed / 0 unexpected**

## Known bugs

All 10 confirmed bugs are documented as `SKIP` entries in the regression suite with error messages and root-cause notes. The hardening suite tests each one exhaustively.

| Bug | Method(s) | Error | Probes |
|---|---|---|---|
| B1 | `page.waitForURL()` | "pattern is required" — all 8 format variants rejected | 8/8 |
| B2 | `page.addScript()` | script value `null` in all contexts; `context.addInitScript()` works | 9/9 |
| B3 | `page.waitForFunction()` | times out even for `true` and `1+1===2` | 10/10 |
| B4 | `el.dispatchEvent()` | `onclick` and `addEventListener` both unresponsive; `el.click()` works | 9/9 |
| B5 | `el.highlight()` | "Unknown command 'vibium:element.highlight'" on all element types | 9/9 |
| B6 | `el.dragTo(Element)` | "dragTo requires 'target' parameter" despite correct `Element` arg | 7/7 |
| B7 | `page.expose()` | function is `undefined` in page JS context after `expose()` in all orderings | 7/7 |
| B8 | `onError()` / `collectErrors()` | errors never forwarded — `setTimeout` throws, script injection, `Promise.reject`, `ErrorEvent` all miss | 10/10 |
| B9 | `clock.setFixedTime()` / `pauseAt()` / `setSystemTime()` / `ClockOptions.time()` | "time is required" for all string formats; `ClockOptions.time()` silently ignored | 13/13 |
| B10 | `page.setHeaders()` | server deadlock on subsequent `page.go()` — same root cause as [issue #128](https://github.com/VibiumDev/vibium/issues/128) | 4/4 sites |

## Files

```
~/vibium-java-tests/
├── VibiumJavaApiTests.java   # main regression suite
├── VibiumBugHardening.java   # multi-site bug hardening
├── NetworkDemo.java          # kitten image injection demo
├── RouteDeadlockRepro.java   # page.route() deadlock reproducer
├── SKILL.md                  # Claude Code skill definition
├── README.md                 # this file
├── vibium-26.3.18.jar        # Vibium Java client (not committed)
└── gson-2.11.0.jar           # JSON dependency (not committed)
```

## Requirements

- Java 21+
- `vibium-26.3.18.jar` and `gson-2.11.0.jar` in `~/vibium-java-tests/`
- Network access (navigation tests hit example.com, httpbin.org, books.toscrape.com, en.wikipedia.org)

## Running manually

```sh
cd ~/vibium-java-tests

# Download JARs (first time only)
curl -L -o vibium-26.3.18.jar \
  https://repo1.maven.org/maven2/com/vibium/vibium/26.3.18/vibium-26.3.18.jar
curl -L -o gson-2.11.0.jar \
  https://repo1.maven.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar

# Compile (only needed once, or after editing)
javac -cp ".:vibium-26.3.18.jar:gson-2.11.0.jar" VibiumJavaApiTests.java VibiumBugHardening.java

# Full regression suite
java -cp ".:vibium-26.3.18.jar:gson-2.11.0.jar" VibiumJavaApiTests

# Bug hardening (all 10 bugs)
java -cp ".:vibium-26.3.18.jar:gson-2.11.0.jar" VibiumBugHardening
```

## Adding tests

Each section is a `static void runXxx()` method in `VibiumJavaApiTests`. Add a `test("name", () -> { ... })` call inside the relevant method. For bugs, add a `skip("name", "BUG: explanation")` and a corresponding probe in `VibiumBugHardening`.

## Installation

```sh
git clone https://github.com/lana-20/vibium-java-tests ~/vibium-java-tests
mkdir -p ~/.claude/skills/vibium-java-test
cp ~/vibium-java-tests/SKILL.md ~/.claude/skills/vibium-java-test/
```

Then add to `~/.claude/CLAUDE.md`:
```
- `/vibium-java-test` — Vibium Java API regression suite + bug hardening (10 bugs × 4 sites)
```

## Usage

```
/vibium-java-test                # run full regression suite
/vibium-java-test harden         # run bug hardening (all 10 bugs, 4 sites)
/vibium-java-test harden B4      # run hardening for one specific bug
/vibium-java-test dialog         # run suite, highlight Dialog section
```
