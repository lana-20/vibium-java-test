# Bug: `page.waitForURL()` throws "pattern is required" for every pattern format (Java client)

## Summary

`page.waitForURL(String pattern)` always throws `"pattern is required"` regardless of the pattern string passed. Every format accepted by the underlying BiDi specification was tested — exact URLs, glob patterns, path-only globs, regex-like strings, and `WaitOptions` overloads — and all fail with the same error.

The method is completely unusable in the Java client.

## Environment

- Vibium Java client: `com.vibium:vibium:26.3.18`
- Java: 21
- OS: macOS 15.3
- Chrome for Testing: 147.0.7727.56

## Minimal reproduction

```java
import com.vibium.Vibium;
import com.vibium.types.WaitOptions;

public class Repro {
    public static void main(String[] args) throws Exception {
        var bro = Vibium.start();
        var page = bro.page();

        page.go("https://httpbin.org");
        // trigger a navigation after a short delay
        page.evaluate("setTimeout(() => { location.href = 'https://example.com'; }, 200)");

        page.waitForURL("https://example.com"); // throws immediately
    }
}
```

**Expected:** waits until the page URL matches `"https://example.com"`, then returns.

**Actual:**
```
com.vibium.errors.VibiumException: pattern is required
```

## All pattern formats tested

Every format that a URL-matching API might accept was tried. All fail with the same error:

| Pattern | Result |
|---|---|
| `"https://example.com"` | `pattern is required` |
| `"https://example.com/"` | `pattern is required` |
| `"*example*"` | `pattern is required` |
| `"**example**"` | `pattern is required` |
| `"https://**"` | `pattern is required` |
| `"**/*.html"` | `pattern is required` |
| `".*example.*"` | `pattern is required` |
| `waitForURL(url, new WaitOptions().timeout(4000))` | `pattern is required` |

8 / 8 variants rejected. The error fires before any timeout elapses, indicating a parameter validation failure at the server layer, not a matching failure.

## Likely root cause

The error message `"pattern is required"` is a server-side validation failure, not a client-side one. This suggests the Java client is not serializing the pattern argument into the request body — either the field is missing, named incorrectly, or the method dispatches to a server command that expects a different parameter key than the Java client sends.

The Playwright TypeScript client names the parameter `url` in its `waitForURL` call; if the Vibium server also expects `url` but the Java client sends `pattern` (or vice versa), every call would fail with this error regardless of value.

## Test suite references

- Regression suite skip: [`VibiumJavaApiTests.java#L73`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumJavaApiTests.java#L73)
- Hardening probes (all 8 variants): [`VibiumBugHardening.java#L65`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumBugHardening.java#L65)

## Hardening results

Reproduced 8 / 8 probes with 0 unexpected passes across the full pattern space.

```
pattern: "https://example.com"           BUG  pattern is required
pattern: "https://example.com/"          BUG  pattern is required
pattern: "*example*"                     BUG  pattern is required
pattern: "**example**"                   BUG  pattern is required
pattern: "https://**"                    BUG  pattern is required
pattern: "**/*.html"                     BUG  pattern is required
pattern: ".*example.*"                   BUG  pattern is required
waitForURL(url, WaitOptions)             BUG  pattern is required
```

## Workaround

None currently available via the Java API. As a manual substitute:

```java
// Poll the URL yourself until it matches
long deadline = System.currentTimeMillis() + 10_000;
while (!page.url().contains("example.com")) {
    if (System.currentTimeMillis() > deadline) throw new RuntimeException("URL never changed");
    Thread.sleep(100);
}
```
