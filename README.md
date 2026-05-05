# vibium-java-tests

Vibium Java API regression suite and bug hardening harness for [`com.vibium:vibium:26.3.18`](https://github.com/VibiumDev/vibium). No Maven, no Gradle — single-file Java, plain `javac` + `java`.

## Files

| File | Description |
|---|---|
| `VibiumJavaApiTests.java` | Full API regression suite — 162 tests across 24 sections |
| `VibiumBugHardening.java` | Hardens all 10 confirmed bugs across 6 sites and multiple page contexts |
| `NetworkDemo.java` | Network module demo: fetches a kitten, injects it into all images on a page, saves a screenshot |
| `RouteDeadlockRepro.java` | Minimal reproducer for the `page.route()` server deadlock ([issue #128](https://github.com/VibiumDev/vibium/issues/128)) |
| `vibium-route-deadlock-bug.md` | Full bug report filed at VibiumDev/vibium#128 |

## Requirements

- Java 21+
- `vibium-26.3.18.jar` and `gson-2.11.0.jar` in the same directory (not committed — download below)

Download dependencies:
```sh
curl -L -o vibium-26.3.18.jar \
  https://repo1.maven.org/maven2/com/vibium/vibium/26.3.18/vibium-26.3.18.jar
curl -L -o gson-2.11.0.jar \
  https://repo1.maven.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar
```

## Compile

```sh
javac -cp ".:vibium-26.3.18.jar:gson-2.11.0.jar" \
  VibiumJavaApiTests.java VibiumBugHardening.java NetworkDemo.java RouteDeadlockRepro.java
```

## Run

**Full regression suite** (136 pass / 26 skip, ~3 min):
```sh
java -cp ".:vibium-26.3.18.jar:gson-2.11.0.jar" VibiumJavaApiTests
```

**Bug hardening** — all 10 bugs × 6 sites (105 confirmed, ~15 min):
```sh
java -cp ".:vibium-26.3.18.jar:gson-2.11.0.jar" VibiumBugHardening
```

**Network demo** — injects a random kitten into all images on books.toscrape.com:
```sh
java -cp ".:vibium-26.3.18.jar:gson-2.11.0.jar" NetworkDemo
```

**Route deadlock reproducer**:
```sh
java -cp ".:vibium-26.3.18.jar:gson-2.11.0.jar" RouteDeadlockRepro
```

## Coverage (VibiumJavaApiTests)

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

## Confirmed bugs (VibiumBugHardening)

All 10 bugs reproduced across 6 sites (example.com, books.toscrape.com, httpbin.org, en.wikipedia.org, var.parts, testtrack.org) with zero unexpected passes.

| Bug | Method(s) | Error | Probes |
|---|---|---|---|
| B1 | `page.waitForURL()` | "pattern is required" — all format variants rejected | 8/8 |
| B2 | `page.addScript()` | script `null` in all contexts; use `context.addInitScript()` | 13/13 |
| B3 | `page.waitForFunction()` | times out even for `true` and `1+1===2` | 12/12 |
| B4 | `el.dispatchEvent()` | `onclick` and `addEventListener` both unresponsive | 11/11 |
| B5 | `el.highlight()` | "Unknown command 'vibium:element.highlight'" | 11/11 |
| B6 | `el.dragTo(Element)` | "dragTo requires 'target' parameter" with correct arg | 9/9 |
| B7 | `page.expose()` | function `undefined` in page JS context in all orderings | 9/9 |
| B8 | `onError()` / `collectErrors()` | errors never forwarded — `setTimeout`, script injection, `Promise.reject`, `ErrorEvent` all miss | 12/12 |
| B9 | `clock.setFixedTime()` / `pauseAt()` / `setSystemTime()` / `ClockOptions.time()` | "time is required" for all string formats | 13/13 |
| B10 | `page.setHeaders()` | server deadlock on subsequent `page.go()` — same root cause as [#128](https://github.com/VibiumDev/vibium/issues/128) | 6/6 sites |
