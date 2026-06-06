# Comment draft — issue #136 (onError partial fix)

---

The fix in [#167](https://github.com/VibiumDev/vibium/pull/167) forwarded some error types — `setTimeout` throws and dynamically injected script errors are now captured on `v26.5.31`. However, two error sources are still not delivered to `onError` handlers or `collectErrors()`:

**`window.ErrorEvent` dispatch — not captured:**
```java
page.setContent("<html><body></body></html>");
var fired = new AtomicBoolean(false);
page.onError(e -> fired.set(true));

page.evaluate("""
    window.dispatchEvent(new ErrorEvent('error', {
        message: 'manual error',
        error: new Error('manual error')
    }))
""");
Thread.sleep(300);
System.out.println(fired.get()); // false — handler not fired
```

**Unhandled `Promise` rejection — not captured:**
```java
page.setContent("<html><body></body></html>");
var fired = new AtomicBoolean(false);
page.onError(e -> fired.set(true));

page.evaluate("Promise.reject(new Error('unhandled rejection'))");
Thread.sleep(300);
System.out.println(fired.get()); // false — handler not fired
```

These map to different BiDi event types than `setTimeout` throws (`log.entryAdded` covers script errors; `ErrorEvent` dispatch and unhandled rejections may require `browsingContext.userPromptOpened` or a separate subscription). If the fix in [#167](https://github.com/VibiumDev/vibium/pull/167) only wired up `log.entryAdded`, these two sources would still be missed.

**Error source status on v26.5.31:**

| Error source | `onError` fired | `collectErrors()` |
|---|---|---|
| `setTimeout(() => { throw new Error() })` | ✅ PASS (fixed in [#167](https://github.com/VibiumDev/vibium/pull/167)) | ✅ PASS |
| Dynamic `<script>` tag with invalid JS | ✅ PASS (fixed in [#167](https://github.com/VibiumDev/vibium/pull/167)) | ✅ PASS |
| `window.dispatchEvent(new ErrorEvent(...))` | ❌ not fired | ❌ empty |
| `Promise.reject(new Error(...))` | ❌ not fired | ❌ empty |

**Environment:** `com.vibium:vibium:26.5.31` · Java 21 · macOS 15.3 · Chrome for Testing 147.0.7727.56

---

## Reproducing

**Option 1 — Claude Code skill** (recommended)

[vibium-java-test](https://github.com/lana-20/vibium-java-test) is a Claude Code skill — a slash command that handles compiling, running, and reporting in a single invocation.

**Setup (once):**
```sh
git clone https://github.com/lana-20/vibium-java-test ~/vibium-java-test
cp ~/vibium-java-test/SKILL.md ~/.claude/skills/vibium-java-test/SKILL.md
# place vibium-26.5.31.jar and gson-2.11.0.jar in ~/vibium-java-test/
```

**Reproduce B8:**
```
/vibium-java-test harden B8
```

This runs all 4 error-source probes (setTimeout throw, dynamic script, ErrorEvent dispatch, Promise.reject) and prints a result line per probe. Still-broken sources show `BUG onError not fired`.

**Option 2 — direct Java:**
```sh
git clone https://github.com/lana-20/vibium-java-test ~/vibium-java-test
cd ~/vibium-java-test
# place vibium-26.5.31.jar and gson-2.11.0.jar here, then:
javac -cp ".:vibium-26.5.31.jar:gson-2.11.0.jar" -d out src/VibiumJavaApiTests.java src/VibiumBugHardening.java
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" VibiumBugHardening B8
```
