# Bug: `onError()` / `collectErrors()` never receive uncaught page errors (Java client)

## Summary

`page.onError(Consumer<String> handler)` and `page.collectErrors()` / `page.errors()` silently fail to capture uncaught JavaScript errors. Every mechanism for generating an uncaught error — `setTimeout` throws, dynamically injected scripts, `window.dispatchEvent(new ErrorEvent(...))`, and unhandled `Promise` rejections — goes undetected. The listener is registered without error, but it is never invoked.

## Environment

- Vibium Java client: `com.vibium:vibium:26.3.18`
- Java: 21
- OS: macOS 15.3
- Chrome for Testing: 147.0.7727.56

## Minimal reproduction

```java
import com.vibium.Vibium;
import java.util.concurrent.atomic.AtomicBoolean;

public class Repro {
    public static void main(String[] args) throws Exception {
        var bro = Vibium.start();
        var page = bro.page();

        page.setContent("<html><body></body></html>");

        var fired = new AtomicBoolean(false);
        page.onError(e -> fired.set(true));

        page.evaluate("setTimeout(() => { throw new Error('boom'); }, 100)");
        Thread.sleep(600);

        System.out.println(fired.get()); // false — handler never called
    }
}
```

**Expected:** the `onError` handler fires with the error message after the `setTimeout` throws.

**Actual:** `fired` remains `false`. No exception is thrown by `onError()` or `evaluate()`.

## All error sources tested

Four distinct mechanisms for generating uncaught errors were tried:

| Error source | Listener | Result |
|---|---|---|
| `setTimeout(() => { throw new Error(...) })` | `onError` | handler not fired |
| Dynamic `<script>` tag with invalid JS | `onError` | handler not fired |
| `window.dispatchEvent(new ErrorEvent('error', {...}))` | `onError` | handler not fired |
| `Promise.reject(new Error(...))` | `onError` | handler not fired |
| `setTimeout(() => { throw new Error(...) })` | `collectErrors` | `errors()` empty |
| Dynamic `<script>` tag with invalid JS | `collectErrors` | `errors()` empty |

## Confirmed across sites

`onError` registered on 6 live pages, then `evaluate("setTimeout(() => { throw new Error('site-err'); }, 100)")` injected:

| Site | Result |
|---|---|
| `https://example.com` | handler not fired |
| `https://books.toscrape.com` | handler not fired |
| `https://httpbin.org` | handler not fired |
| `https://en.wikipedia.org` | handler not fired |
| `https://var.parts` | handler not fired |
| `https://testtrack.org` | handler not fired |

## Likely root cause

The BiDi protocol exposes uncaught errors via the `script.realmDestroyed` or `log.entryAdded` events. The Vibium server likely does not subscribe to or forward these events to the Java client. The `onError` handler is registered client-side but the server never emits the event that would trigger it, regardless of what errors occur in the page.

The `collectErrors()` / `errors()` pair has the same root cause — it relies on the same event forwarding that never happens.

## Workaround

Approximate error collection via console listener and `window.onerror`:

```java
page.evaluate("""
    window.onerror = function(msg, src, line, col, err) {
        console.error('UNCAUGHT: ' + msg);
    };
    window.addEventListener('unhandledrejection', function(e) {
        console.error('UNHANDLED_REJECTION: ' + e.reason);
    });
""");
page.collectConsole();
// errors appear in page.consoleMessages() as type "error"
```

## Test suite references

- Regression suite skips: [`VibiumJavaApiTests.java#L511`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumJavaApiTests.java#L511)
- Hardening probes (6 error sources + 6 sites): [`VibiumBugHardening.java#L397`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumBugHardening.java#L397)

## Hardening results

Reproduced 12 / 12 probes with 0 unexpected passes.

```
setTimeout throw → onError                                      BUG  onError not fired
dynamic script throw → onError                                  BUG  onError not fired
window ErrorEvent dispatch → onError                            BUG  onError not fired
unhandled Promise rejection → onError                           BUG  onError not fired
setTimeout throw → collectErrors                                BUG  errors() is empty
dynamic script throw → collectErrors                            BUG  errors() is empty
onError on real page → throw in setTimeout [example.com]        BUG  onError not fired on https://example.com
onError on real page → throw in setTimeout [books.toscrape.com] BUG  onError not fired on https://books.toscrape.com
onError on real page → throw in setTimeout [httpbin.org]        BUG  onError not fired on https://httpbin.org
onError on real page → throw in setTimeout [en.wikipedia.org]   BUG  onError not fired on https://en.wikipedia.org
onError on real page → throw in setTimeout [var.parts]          BUG  onError not fired on https://var.parts
onError on real page → throw in setTimeout [testtrack.org]      BUG  onError not fired on https://testtrack.org
```
