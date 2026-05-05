# Bug: `page.route()` makes `page.go()` deadlock permanently (Java client)

## Summary

Any call to `page.route()` before `page.go()` causes navigation to hang indefinitely and time out after 60 seconds. This makes `page.route()` unusable for its primary purpose — intercepting requests during page load.

The deadlock occurs even when the route pattern never matches any request (e.g. `**/*.jpg` on `example.com`, which serves no images), so the bug is in the route registration itself, not in the fulfillment logic.

## Environment

- Vibium Java client: `com.vibium:vibium:26.3.18`
- Java: 21
- OS: macOS 15.3
- Chrome for Testing: 147.0.7727.56

## Minimal reproduction

```java
import com.vibium.Vibium;
import com.vibium.types.FulfillOptions;

public class Repro {
    public static void main(String[] args) throws Exception {
        var bro = Vibium.start();
        var page = bro.page();

        page.route("**/*.jpg", route -> route.fulfill(
            new FulfillOptions().status(200).contentType("image/svg+xml").body("<svg/>")
        ));

        page.go("https://example.com"); // hangs 60s, then throws
    }
}
```

**Expected:** navigates normally; route intercepts matching `.jpg` requests.

**Actual:**
```
Exception in thread "main" com.vibium.errors.VibiumTimeoutException: Timeout after 60000ms waiting for response to vibium:page.navigate
```

## Confirmed across sites

Tested by running `page.go()` in a background thread and checking whether it completes within 5 seconds:

| Site | Result |
|---|---|
| `https://example.com` | DEADLOCK (still blocked after 5s) |
| `https://wikipedia.org` | DEADLOCK (still blocked after 5s) |
| `https://books.toscrape.com` | DEADLOCK (still blocked after 5s) |
| `https://httpbin.org` | DEADLOCK (still blocked after 5s) |

`example.com` is particularly telling — no images are served so the route handler is never invoked, yet navigation still deadlocks. The problem is triggered by `page.route()` alone.

## Likely root cause

The Vibium server appears to process commands sequentially. When `page.go()` is called with a route active, the sequence becomes:

1. `page.route()` tells the server to enable Chrome network interception ✓
2. `page.go()` tells the server to navigate — server sends `browsingContext.navigate` to Chrome and **waits** for a response
3. Chrome starts loading and intercepts network requests per step 1
4. Chrome sends intercept events back to the Vibium server, expecting `fulfill` or `continue` responses
5. **The server cannot relay these events to the Java client because it is still blocked in step 2**
6. Java client never receives events → never sends `fulfill`/`continue` → Chrome stalls
7. `browsingContext.navigate` never completes → 60s timeout

The fix would be for the server to dispatch intercept events to clients concurrently with — not after — waiting for navigation to complete.

## Workaround

Navigate first, then register routes. Routes will only intercept requests triggered after registration (e.g. fetch calls or image reloads initiated via `page.evaluate()`). Note that `page.evaluate()` itself will also deadlock if the JS it runs makes a fetch that the active route intercepts — the same blocking behavior applies.

```java
page.go("https://example.com"); // navigate first — no routes active, works fine
page.route("**/*.jpg", handler); // register routes after load completes
```
