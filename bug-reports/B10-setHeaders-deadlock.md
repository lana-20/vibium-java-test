# Bug: `page.setHeaders()` causes `page.go()` to deadlock permanently (Java client)

> **Note:** This is the same root cause as [issue #128 (`page.route()` deadlock)](https://github.com/VibiumDev/vibium/issues/128). The additional findings below were posted as a comment on that issue. A separate issue was not filed.

## Summary

`page.setHeaders(Map<String, String>)` enables server-side network interception — the same mode activated by `page.route()`. Any subsequent `page.go()` then deadlocks indefinitely for the same reason: Chrome intercepts network requests and sends events to the Vibium server, but the server is blocked waiting for the navigation to complete and cannot relay those events to the Java client. The result is a permanent hang that times out after 60 seconds.

## Environment

- Vibium Java client: `com.vibium:vibium:26.3.18`
- Java: 21
- OS: macOS 15.3
- Chrome for Testing: 147.0.7727.56

## Minimal reproduction

```java
import com.vibium.Vibium;
import java.util.Map;

public class Repro {
    public static void main(String[] args) throws Exception {
        var bro = Vibium.start();
        var page = bro.page();

        page.setHeaders(Map.of("X-Custom-Header", "value"));
        page.go("https://example.com"); // hangs 60s, then throws
    }
}
```

**Expected:** navigates normally with the custom header attached to requests.

**Actual:**
```
com.vibium.errors.VibiumTimeoutException: Timeout after 60000ms waiting for response to vibium:page.navigate
```

## Key distinction from issue #128

`page.setHeaders()` deadlocks even though it does not register a route handler — it has no fulfillment or continuation logic. The deadlock is triggered purely by enabling network interception mode, which both `page.route()` and `page.setHeaders()` do. `page.setHeaders()` alone (without a subsequent `page.go()`) does not deadlock.

## Confirmed across sites

Tested by calling `page.go()` in a background thread and checking whether it completes within 5 seconds:

| Site | Result |
|---|---|
| `https://example.com` | DEADLOCK (still blocked after 5s) |
| `https://books.toscrape.com` | DEADLOCK (still blocked after 5s) |
| `https://httpbin.org` | DEADLOCK (still blocked after 5s) |
| `https://en.wikipedia.org` | DEADLOCK (still blocked after 5s) |
| `https://var.parts` | DEADLOCK (still blocked after 5s) |
| `https://testtrack.org` | DEADLOCK (still blocked after 5s) |

`page.setHeaders()` alone (no `page.go()`) completes without hanging — confirming the deadlock requires the combination of active network interception + navigation.

## Root cause

See [issue #128](https://github.com/VibiumDev/vibium/issues/128) for full analysis. In brief: the Vibium server processes commands sequentially. When `page.go()` is called with network interception active, Chrome intercepts requests and sends events back to the server — but the server cannot relay them to the Java client because it is blocked waiting for navigation to complete. The navigation never completes; Chrome stalls waiting for intercept responses that never arrive.

## Workaround

Navigate before setting headers — headers registered after `page.go()` will apply to subsequent fetch requests initiated via `page.evaluate()`, but not to the initial page load.

```java
page.go("https://example.com"); // navigate first — no interception active
page.setHeaders(Map.of("X-Custom-Header", "value")); // register after load
// headers will apply to subsequent evaluate()-initiated fetches only
```

## Test suite references

- Regression suite skip: [`VibiumJavaApiTests.java#L569`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumJavaApiTests.java#L569)
- Hardening probes (6 sites + no-go control): [`VibiumBugHardening.java#L536`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumBugHardening.java#L536)

## Hardening results

Reproduced 7 / 7 probes with 0 unexpected passes.

```
setHeaders → go [example.com]           BUG  DEADLOCK confirmed (still blocked after 5s)
setHeaders → go [books.toscrape.com]    BUG  DEADLOCK confirmed (still blocked after 5s)
setHeaders → go [httpbin.org]           BUG  DEADLOCK confirmed (still blocked after 5s)
setHeaders → go [en.wikipedia.org]      BUG  DEADLOCK confirmed (still blocked after 5s)
setHeaders → go [var.parts]             BUG  DEADLOCK confirmed (still blocked after 5s)
setHeaders → go [testtrack.org]         BUG  DEADLOCK confirmed (still blocked after 5s)
setHeaders only (no go)                 OK   (no deadlock — interception alone is safe)
```
