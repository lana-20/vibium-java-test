# Bug: `page.addScript()` never executes — value always `null` in every context (Java client)

## Summary

`page.addScript(String js)` silently fails in every ordering and page context tested. The script is accepted without error, but any variable or side-effect it sets is never visible to subsequent `page.evaluate()` calls — the value is always `null`. In one context variant (setContent after navigation on books.toscrape.com) it throws `"Cannot find context with specified id"`, suggesting the script is being injected into a stale or invalid execution context.

`context.addInitScript()` works correctly as a workaround.

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

        page.setContent("<html><body></body></html>");
        page.addScript("window.__test = 'hello';");

        Object val = page.evaluate("window.__test");
        System.out.println(val); // prints: null
    }
}
```

**Expected:** `page.addScript()` injects the script into the current page context; `page.evaluate("window.__test")` returns `"hello"`.

**Actual:** returns `null`. No exception is thrown.

## All orderings tested

Three invocation orderings were tried to rule out sequencing as the cause:

**Variant 1 — setContent → addScript → evaluate (same page)**
```java
page.setContent("<html><body></body></html>");
page.addScript("window.__b2 = 'b2val';");
Object v = page.evaluate("window.__b2"); // null
```

**Variant 2 — addScript → go() → evaluate (across navigation)**
```java
page.addScript("window.__b2nav = 'nav';");
page.go("https://example.com");
Object v = page.evaluate("window.__b2nav"); // null
```

**Variant 3 — go() → addScript → reload() → evaluate**
```java
page.go("https://example.com");
page.addScript("window.__b2r = 'reloaded';");
page.reload();
Object v = page.evaluate("window.__b2r"); // null
```

All three return `null`. Variant 2 is particularly notable: even if `addScript` were meant to run only on future navigations (like `addInitScript`), the value should survive `go()`.

## Two distinct failure modes

| Context | Error |
|---|---|
| Most sites and setContent pages | `got: null` — script silently not executed |
| `setContent` after prior navigation on books.toscrape.com | `unknown error: Cannot find context with specified id` |

The second error suggests `page.addScript()` may be targeting a browsing context ID that becomes stale after navigation, rather than always resolving to the current context.

## Workaround

Use `context.addInitScript()` instead. It correctly injects the script before each page load and is available on the current page after any navigation:

```java
page.context().addInitScript("window.__test = 'hello';");
page.go("https://example.com");
Object val = page.evaluate("window.__test"); // "hello" ✓
```

## Test suite references

- Regression suite skip: [`VibiumJavaApiTests.java#L93`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumJavaApiTests.java#L93)
- Hardening probes (all 3 orderings × 6 sites): [`VibiumBugHardening.java#L98`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumBugHardening.java#L98)

## Hardening results

Reproduced 13 / 13 probes with 0 unexpected passes across 6 sites and 3 invocation orderings.

```
setContent → addScript → evaluate [example.com]         BUG  got: null
setContent → addScript → evaluate [books.toscrape.com]  BUG  unknown error: Cannot find context with specified id
setContent → addScript → evaluate [httpbin.org]         BUG  got: null
setContent → addScript → evaluate [en.wikipedia.org]    BUG  got: null
setContent → addScript → evaluate [var.parts]           BUG  got: null
setContent → addScript → evaluate [testtrack.org]       BUG  got: null
addScript → go() → evaluate [example.com]               BUG  got: null
addScript → go() → evaluate [books.toscrape.com]        BUG  got: null
addScript → go() → evaluate [httpbin.org]               BUG  got: null
addScript → go() → evaluate [en.wikipedia.org]          BUG  got: null
addScript → go() → evaluate [var.parts]                 BUG  got: null
addScript → go() → evaluate [testtrack.org]             BUG  got: null
go → addScript → reload → evaluate                      BUG  got: null
```
