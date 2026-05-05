# Bug: `page.waitForFunction()` always times out — never evaluates the expression (Java client)

## Summary

`page.waitForFunction(String expression, WaitOptions options)` always times out regardless of the expression passed. Even trivially-true expressions that return `true` before the call is made — `true`, `1 + 1 === 2`, `window !== undefined` — time out after the specified timeout elapses. The method never evaluates the expression at all.

## Environment

Vibium:26.3.18`
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

        page.setContent("<html><body></body></html>");
        page.waitForFunction("true", new WaitOptions().timeout(3000)); // times out after 3s
    }
}
```

**Expected:** returns immediately — `true` is already truthy before the call.

**Actual:**
```
com.vibium.errors.VibiumTimeoutException: timeout waiting for function to return truthy
```

## All expressions tested

Every expression that should resolve truthy immediately was tried:

| Expression | Result |
|---|---|
| `"true"` | timeout |
| `"1 + 1 === 2"` | timeout |
| `"typeof document !== 'undefined'"` | timeout |
| `"document.readyState === 'complete'"` | timeout |
| `"window !== undefined"` | timeout |
| `"window.__wff === true"` (set via `evaluate()` first) | timeout |

The flag-via-evaluate variant is particularly telling: `page.evaluate("window.__wff = true")` succeeds, confirming the JS context is live, but `waitForFunction("window.__wff === true")` still times out. The expression is never being evaluated.

## Confirmed across sites

Tested on 6 real pages after `page.go()` completes (page is fully loaded):

| Site | Expression | Result |
|---|---|---|
| `https://example.com` | `document.readyState === 'complete'` | timeout |
| `https://books.toscrape.com` | `document.readyState === 'complete'` | timeout |
| `https://httpbin.org` | `document.readyState === 'complete'` | timeout |
| `https://en.wikipedia.org` | `document.readyState === 'complete'` | timeout |
| `https://var.parts` | `document.readyState === 'complete'` | timeout |
| `https://testtrack.org` | `document.readyState === 'complete'` | timeout |

`document.readyState === 'complete'` is guaranteed true after `page.go()` returns, yet the wait always times out.

## Likely root cause

The method appears to send the expression to the server but never poll or re-evaluate it. The server likely waits for a BiDi event that never arrives rather than actively re-running the expression on an interval. The result is a hard timeout on every call regardless of expression value.

## Workaround

Poll manually using `page.evaluate()`:

```java
long deadline = System.currentTimeMillis() + 10_000;
while (!(Boolean) page.evaluate("window.__ready === true")) {
    if (System.currentTimeMillis() > deadline) throw new RuntimeException("condition never met");
    Thread.sleep(100);
}
```

## Test suite references

- Regression suite skips: [`VibiumJavaApiTests.java#L162`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumJavaApiTests.java#L162)
- Hardening probes (6 expressions + flag variant + 6 sites): [`VibiumBugHardening.java#L133`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumBugHardening.java#L133)

## Hardening results

Reproduced 12 / 12 probes with 0 unexpected passes.

```
already-true: true                                    BUG  timeout waiting for function to return truthy
already-true: 1 + 1 === 2                             BUG  timeout waiting for function to return truthy
already-true: typeof document !== 'undefined'         BUG  timeout waiting for function to return truthy
already-true: document.readyState === 'complete'      BUG  timeout waiting for function to return truthy
already-true: window !== undefined                    BUG  timeout waiting for function to return truthy
evaluate sets flag → waitForFunction checks it        BUG  timeout waiting for function to return truthy
already-true after go [example.com]                   BUG  timeout waiting for function to return truthy
already-true after go [books.toscrape.com]            BUG  timeout waiting for function to return truthy
already-true after go [httpbin.org]                   BUG  timeout waiting for function to return truthy
already-true after go [en.wikipedia.org]              BUG  timeout waiting for function to return truthy
already-true after go [var.parts]                     BUG  timeout waiting for function to return truthy
already-true after go [testtrack.org]                 BUG  timeout waiting for function to return truthy
```
