# vibium-java-test

Vibium Java API regression suite and bug hardening harness for [`com.vibium:vibium:26.5.31`](https://github.com/VibiumDev/vibium). No Maven, no Gradle — single-file Java, plain `javac` + `java`.

## Files

| File | Description |
|---|---|
| `src/VibiumJavaApiTests.java` | Full API regression suite — 162 tests across 24 sections |
| `src/VibiumBugHardening.java` | Hardens all 10 confirmed bugs across 6 sites and multiple page contexts |
| `src/NetworkDemo.java` | Network module demo: fetches a kitten, injects it into all images on a page, saves a screenshot |
| `src/RouteDeadlockRepro.java` | Minimal reproducer for the `page.route()` server deadlock ([issue #128](https://github.com/VibiumDev/vibium/issues/128)) |
| `bug-reports/` | Individual bug reports B1–B10 |

## Requirements

- Java 21+
- `vibium-26.5.31.jar` and `gson-2.11.0.jar` in the project root (not committed — download below)

Download dependencies:
```sh
curl -L -o vibium-26.5.31.jar \
  https://repo1.maven.org/maven2/com/vibium/vibium/26.5.31/vibium-26.5.31.jar
curl -L -o gson-2.11.0.jar \
  https://repo1.maven.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar
```

**macOS note:** the Python vibium client can shadow the npm binary in PATH. Prefix all commands with `PATH="/usr/local/bin:$PATH"` if you see `vibium process did not send ready signal`.

## Compile

Sources live in `src/`; compiled classes go to `out/`:
```sh
javac -cp ".:vibium-26.5.31.jar:gson-2.11.0.jar" -d out \
  src/VibiumJavaApiTests.java src/VibiumBugHardening.java src/NetworkDemo.java src/RouteDeadlockRepro.java
```

## Run

**Full regression suite** (140 pass / 22 skip, ~3 min):
```sh
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" VibiumJavaApiTests
```

**Bug hardening** — all 10 bugs × 6 sites (~15 min):
```sh
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" VibiumBugHardening
```

**Network demo** — injects a random kitten into all images on books.toscrape.com:
```sh
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" NetworkDemo
```

**Route deadlock reproducer**:
```sh
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" RouteDeadlockRepro
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

## Baseline

| Version | Suite | Pass | Fail | Skip | Total |
|---|---|---|---|---|---|
| v26.3.18 | VibiumJavaApiTests | 136 | 0 | 26 | 162 |
| v26.5.31 | VibiumJavaApiTests | 140 | 0 | 22 | 162 |
| v26.3.18 | VibiumBugHardening | 112 confirmed | 0 unexpected | — | 112 probes |
| v26.5.31 | VibiumBugHardening | 52 confirmed | 60 unexpected passes | — | 112 probes |

## Bug status (v26.5.31)

| Bug | Method(s) | v26.5.31 status | Notes |
|---|---|---|---|
| B1 | `page.waitForURL()` | PARTIAL (#129/#167) | URL/glob patterns fixed; `**/*.html` and regex still fail |
| B2 | `page.addScript()` | PARTIAL (#130/#167) | `setContent→addScript→evaluate` works; `addScript→go()` still null — use `context.addInitScript()` for cross-navigation persistence |
| B3 | `page.waitForFunction()` | STILL BROKEN | Engine fix (#163) landed but Java client pre-wraps bare expressions → `SyntaxError: Unexpected token ')'`; not fixed in #167 |
| B4 | `el.dispatchEvent()` | **FIXED** ✓ (#132/#167) | All 11 hardening probes PASS |
| B5 | `el.highlight()` | **FIXED** ✓ (#133/#167) | Engine command implemented; all 11 probes PASS |
| B6 | `el.dragTo(Element)` | **FIXED** ✓ (#134/#167) | "requires target parameter" error gone; 3 residual failures are site-specific element issues |
| B7 | `page.expose()` | DEFERRED (#135) | Java `Function` callback incompatible with engine's fn-string injection |
| B8 | `onError()` / `collectErrors()` | PARTIAL (#136/#167) | `setTimeout`/dynamic script errors now forwarded; `window.ErrorEvent` and unhandled Promise rejection still not captured |
| B9 | `clock.setFixedTime()` / `pauseAt()` / `setSystemTime()` / `ClockOptions.time()` | PARTIAL (#137/#167) | ISO-8601 and epoch-ms accepted; human-readable `"Month DD, YYYY"` unsupported; `ClockOptions.time()` value still ignored (off by 1 year) |
| B10 | `page.setHeaders()` | DEFERRED (#128) | Threading deadlock; same root cause as route/dialog deadlock |

B4, B5, B6 SKIP annotations removed — tests now active and passing.
