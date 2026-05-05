# Bug: `page.expose()` never injects function into page JS context — callback, return value, and args all broken (Java client)

## Summary

`page.expose(String name, ExposedFunction fn)` registers without throwing, but is completely non-functional: the named function is never visible in the page's JavaScript context, callbacks never fire, return values are not propagated, arguments are not received, and inline `<script>` invocations have no effect. Tested in every invocation ordering across 6 sites — all fail identically.

## Environment

- Vibium Java client: `com.vibium:vibium:26.3.18`
- Java: 21
- OS: macOS 15.3
- Chrome for Testing: 147.0.7727.56

## Minimal reproduction

```java
import com.vibium.Vibium;

public class Repro {
    public static void main(String[] args) throws Exception {
        var bro = Vibium.start();
        var page = bro.page();

        page.expose("myFn", a -> "hello-from-java");
        page.setContent("<html><body></body></html>");

        System.out.println(page.evaluate("typeof myFn")); // "undefined"
        System.out.println(page.evaluate("myFn()"));      // null (no throw, no value)
    }
}
```

**Expected:** `typeof myFn` → `"function"`; `myFn()` → `"hello-from-java"` and Java callback fires.

**Actual:** `typeof myFn` → `"undefined"`; `myFn()` → `null`; Java callback never invoked.

## All failure modes confirmed

### Function not visible in JS context

Every invocation ordering returns `typeof: undefined`:

| Ordering | Result |
|---|---|
| `expose()` → `setContent()` → `typeof` | `typeof: undefined` |
| `expose()` → `go(url)` → `typeof` | `typeof: undefined` |
| `go(url)` → `expose()` → `typeof` | `typeof: undefined` |
| `setContent()` → `expose()` → `typeof` | `typeof: undefined` |

### Callback never fires

Even forcing a call via `evaluate()` produces no invocation of the Java callback:

| Probe | Result |
|---|---|
| `expose → setContent → evaluate("fn()")` → callback fires | callback never fired |
| `expose → setContent → inline \`<script>fn();\`\`` → callback fires | callback never fired |

### Return value not propagated

`evaluate("fn()")` returns `null` instead of the Java callback's return value:

| Probe | Result |
|---|---|
| `expose → setContent → evaluate("fn()")` → return value | `got: null` |

### Arguments not received by Java callback

Args passed from JS are not forwarded to the Java callback:

| Probe | Result |
|---|---|
| `expose → setContent → evaluate("fn('hello', 42)")` → args | `args not received: null` |

## Confirmed across sites

`expose → go(site) → typeof` tested across 6 sites, all return `typeof: undefined`:

`example.com`, `books.toscrape.com`, `httpbin.org`, `en.wikipedia.org`, `var.parts`, `testtrack.org`

## Likely root cause

Similar to [#130 (`page.addScript()`)](https://github.com/VibiumDev/vibium/issues/130), `page.expose()` appears to target a browsing context that is stale or not the current page context. The BiDi command to register an exposed binding may be sent to the wrong target, or the server-side implementation may not forward the binding to the active JS realm. Unlike `context.addInitScript()` — which correctly injects scripts before each page load — `page.expose()` has no equivalent working alternative in the Java client.

## Workaround

None via the Java API. If bidirectional Java↔JS communication is needed, approximate it with polling via `page.evaluate()` and shared state in a cookie or `BrowserContext` storage.

## Test suite references

- Regression suite skips: [`VibiumJavaApiTests.java#L826`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumJavaApiTests.java#L826)
- Hardening probes (4 orderings + 6 sites + 4 invocation probes): [`VibiumBugHardening.java#L320`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumBugHardening.java#L320)
- Focused invocation probe script: [`ExposeProbe.java`](https://github.com/lana-20/vibium-java-tests/blob/main/ExposeProbe.java)

## Hardening results

Reproduced 13 / 13 probes with 0 unexpected passes.

```
expose → setContent → typeof                                 BUG  typeof: undefined
expose → go → typeof [example.com]                          BUG  typeof: undefined
expose → go → typeof [books.toscrape.com]                   BUG  typeof: undefined
expose → go → typeof [httpbin.org]                          BUG  typeof: undefined
expose → go → typeof [en.wikipedia.org]                     BUG  typeof: undefined
expose → go → typeof [var.parts]                            BUG  typeof: undefined
expose → go → typeof [testtrack.org]                        BUG  typeof: undefined
go → expose → typeof (same page)                            BUG  typeof: undefined
setContent → expose → typeof                                BUG  typeof: undefined
expose → setContent → call → callback fires                 BUG  callback never fired
expose → setContent → return value reaches JS               BUG  got: null
expose → setContent → args passed to Java callback          BUG  args not received: null
expose → setContent inline script → callback fires          BUG  callback never fired from inline script
```
