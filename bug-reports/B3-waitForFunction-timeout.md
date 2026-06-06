# Bug: `page.waitForFunction()` always fails — Java client double-wraps every expression (Java client)

## Summary

`page.waitForFunction(String expression, WaitOptions options)` always times out with a `SyntaxError: Unexpected token ')'` as the last error, regardless of what expression is passed. The Java client wraps every expression — bare strings and arrow functions alike — into `() => <expr>` before sending to the engine. The engine then wraps again, producing `() => () => <expr>`, which is a syntax error. No expression form escapes this.

## Environment

- `vibium:26.5.31`
- Java: 21
- OS: macOS 15.3
- Chrome for Testing: 147.0.7727.56

## Minimal reproduction

```java
import com.vibium.Vibium;
import com.vibium.types.WaitOptions;

public class B3Repro {
    public static void main(String[] args) throws Exception {
        var bro = Vibium.start();
        var page = bro.page();
        page.setContent("<html><body></body></html>");

        // bare expression
        page.waitForFunction("true", new WaitOptions().timeout(3000));

        // lambda-wrapped — same error
        page.waitForFunction("() => true", new WaitOptions().timeout(3000));
    }
}
```

**Expected:** returns immediately — expression is already truthy.

**Actual (both forms):**
```
com.vibium.errors.VibiumTimeoutException: timeout waiting for function to return truthy
  (last error: SyntaxError: Unexpected token ')')
```

## Root cause

The Java client unconditionally wraps the expression string into an arrow function before dispatching the BiDi `script.callFunction` command. The engine independently wraps the received string as well. The result:

| Input | Sent to engine | Engine executes | Outcome |
|---|---|---|---|
| `"true"` | `"() => true"` | `() => () => true` | `SyntaxError: Unexpected token ')'` |
| `"() => true"` | `"() => () => true"` | `() => () => () => true` | `SyntaxError: Unexpected token ')'` |

The engine fix landed in #163 but only addressed the engine side. The Java client's pre-wrap was not removed in #167, so the double-wrap persists on the client layer.

## All expressions tested

Every expression form produces the same error:

| Expression | Form | Result |
|---|---|---|
| `"true"` | bare | `SyntaxError: Unexpected token ')'` |
| `"1 + 1 === 2"` | bare | `SyntaxError: Unexpected token ')'` |
| `"typeof document !== 'undefined'"` | bare | `SyntaxError: Unexpected token ')'` |
| `"document.readyState === 'complete'"` | bare | `SyntaxError: Unexpected token ')'` |
| `"window !== undefined"` | bare | `SyntaxError: Unexpected token ')'` |
| `"() => true"` | lambda | `SyntaxError: Unexpected token ')'` |
| `"() => document.readyState === 'complete'"` | lambda | `SyntaxError: Unexpected token ')'` |
| `"() => window.__wff === true"` | lambda | `SyntaxError: Unexpected token ')'` |
| `"window.__wff === true"` (flag set via `evaluate()`) | bare | `SyntaxError: Unexpected token ')'` |

The flag-via-evaluate variant confirms the JS context is live (`evaluate` works), but `waitForFunction` still fails. Lambda forms were added specifically to test whether the engine fix helped — they fail identically, proving the wrap happens in the Java client before the engine sees the expression.

## Confirmed across sites

Tested on 6 real pages after `page.go()` completes:

| Site | Expression | Result |
|---|---|---|
| `https://example.com` | `document.readyState === 'complete'` | `SyntaxError: Unexpected token ')'` |
| `https://books.toscrape.com` | `document.readyState === 'complete'` | `SyntaxError: Unexpected token ')'` |
| `https://httpbin.org` | `document.readyState === 'complete'` | `SyntaxError: Unexpected token ')'` |
| `https://en.wikipedia.org` | `document.readyState === 'complete'` | `SyntaxError: Unexpected token ')'` |
| `https://var.parts` | `document.readyState === 'complete'` | `SyntaxError: Unexpected token ')'` |
| `https://testtrack.org` | `document.readyState === 'complete'` | `SyntaxError: Unexpected token ')'` |

## Fix

Remove the pre-wrap in the Java client's `waitForFunction` dispatch. The engine (post-#163) handles wrapping correctly on its own. The Java client should pass the expression string as-is.

## Workaround

Poll manually using `page.evaluate()`:

```java
long deadline = System.currentTimeMillis() + 10_000;
while (!(Boolean) page.evaluate("document.readyState === 'complete'")) {
    if (System.currentTimeMillis() > deadline) throw new RuntimeException("condition never met");
    Thread.sleep(100);
}
```

## Test suite references

- Regression suite skips: [`VibiumJavaApiTests.java`](https://github.com/lana-20/vibium-java-test/blob/main/src/VibiumJavaApiTests.java)
- Hardening probes (bare + lambda + flag + 6 sites): [`VibiumBugHardening.java`](https://github.com/lana-20/vibium-java-test/blob/main/src/VibiumBugHardening.java)
- Minimal repro: [`B3Repro.java`](https://github.com/lana-20/vibium-java-test/blob/main/src/B3Repro.java)

## Hardening results (v26.5.31 · 2026-06-06)

Reproduced **15 / 15** probes with **0 unexpected passes**.

```
bare: true                                    BUG  SyntaxError: Unexpected token ')'
bare: 1 + 1 === 2                             BUG  SyntaxError: Unexpected token ')'
bare: typeof document !== 'undefined'         BUG  SyntaxError: Unexpected token ')'
bare: document.readyState === 'complete'      BUG  SyntaxError: Unexpected token ')'
bare: window !== undefined                    BUG  SyntaxError: Unexpected token ')'
lambda-wrapped: () => true                    BUG  SyntaxError: Unexpected token ')'
lambda-wrapped: () => document.readyState…   BUG  SyntaxError: Unexpected token ')'
lambda-wrapped: () => window.__wff === true   BUG  SyntaxError: Unexpected token ')'
evaluate sets flag → waitForFunction bare     BUG  SyntaxError: Unexpected token ')'
bare after go [example.com]                   BUG  SyntaxError: Unexpected token ')'
bare after go [books.toscrape.com]            BUG  SyntaxError: Unexpected token ')'
bare after go [httpbin.org]                   BUG  SyntaxError: Unexpected token ')'
bare after go [en.wikipedia.org]              BUG  SyntaxError: Unexpected token ')'
bare after go [var.parts]                     BUG  SyntaxError: Unexpected token ')'
bare after go [testtrack.org]                 BUG  SyntaxError: Unexpected token ')'
```
