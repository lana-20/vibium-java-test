# Bug: `page.waitForFunction()` always fails with `SyntaxError: Unexpected token ')'` — Java client double-wraps every expression

## Background

This issue is related to #131 (original report) and #163 (engine-side fix), but represents a **distinct, unresolved problem on the Java client layer** that was not addressed by #167.

#163 fixed the engine's wrapping behavior. However, the Java client independently wraps every expression into `() => <expr>` before sending it to the engine. Since the engine also wraps, both bare strings and arrow functions arrive double-wrapped and always produce a `SyntaxError`. The method is completely non-functional in the Java client as of v26.5.31.

---

## Environment

- `com.vibium:vibium:26.5.31`
- Java 21
- macOS 15.3
- Chrome for Testing 147.0.7727.56

---

## What happens

`page.waitForFunction()` always times out. The timeout message includes `last error: SyntaxError: Unexpected token ')'`, which is the actual failure — the expression never runs because it is syntactically broken before evaluation begins.

This happens for every expression form: bare strings, arrow functions, flag-via-evaluate patterns, across all pages and sites tested.

---

## Minimal repro

```java
import com.vibium.Vibium;
import com.vibium.types.WaitOptions;

public class B3Repro {
    public static void main(String[] args) throws Exception {
        var bro = Vibium.start();
        var page = bro.page();
        page.setContent("<html><body></body></html>");

        // Form 1: bare expression
        page.waitForFunction("true", new WaitOptions().timeout(3000));

        // Form 2: arrow function — same result
        page.waitForFunction("() => true", new WaitOptions().timeout(3000));
    }
}
```

**Expected:** returns immediately — both expressions are truthy before the call.

**Actual (both forms):**
```
com.vibium.errors.VibiumTimeoutException: timeout waiting for function to return truthy
  (last error: SyntaxError: Unexpected token ')')
```

Compile and run:
```sh
cd ~/vibium-java-test
javac -cp ".:vibium-26.5.31.jar:gson-2.11.0.jar" -d out src/B3Repro.java
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" B3Repro
```

Source: [`src/B3Repro.java`](https://github.com/lana-20/vibium-java-test/blob/main/src/B3Repro.java)

---

## Root cause

The Java client unconditionally wraps the caller's expression into an arrow function before dispatching the BiDi command. The engine (post-#163) also wraps. Both layers wrap independently, so the engine receives and attempts to execute a double-wrapped expression:

| Caller passes | Java client sends | Engine executes | Result |
|---|---|---|---|
| `"true"` | `"() => true"` | `() => () => true` | `SyntaxError: Unexpected token ')'` |
| `"() => true"` | `"() => () => true"` | `() => () => () => true` | `SyntaxError: Unexpected token ')'` |

Arrow function syntax does not chain this way — `() => () => true` is valid (returns a function), but when the engine additionally wraps that, the resulting call structure breaks. The `SyntaxError` is thrown before the expression is ever evaluated.

The engine fix in #163 was necessary but not sufficient. The Java client's pre-wrap must also be removed.

---

## All expression forms tested

| Expression | Form | Result |
|---|---|---|
| `"true"` | bare literal | `SyntaxError: Unexpected token ')'` |
| `"1 + 1 === 2"` | bare expression | `SyntaxError: Unexpected token ')'` |
| `"typeof document !== 'undefined'"` | bare typeof check | `SyntaxError: Unexpected token ')'` |
| `"document.readyState === 'complete'"` | bare DOM access | `SyntaxError: Unexpected token ')'` |
| `"window !== undefined"` | bare global check | `SyntaxError: Unexpected token ')'` |
| `"() => true"` | arrow function | `SyntaxError: Unexpected token ')'` |
| `"() => document.readyState === 'complete'"` | arrow function | `SyntaxError: Unexpected token ')'` |
| `"() => window.__wff === true"` | arrow function | `SyntaxError: Unexpected token ')'` |
| `"window.__wff === true"` (flag set via `evaluate()` first) | bare | `SyntaxError: Unexpected token ')'` |

The flag-via-evaluate variant is the most diagnostic: `page.evaluate("window.__wff = true")` succeeds — the JS context is live and `evaluate()` works — but `waitForFunction("window.__wff === true")` still fails with `SyntaxError`. The expression never reaches the evaluation step.

Arrow function forms (`() => ...`) were tested specifically to check whether passing a pre-wrapped expression would bypass the client's wrapping. They fail identically, confirming the client wraps unconditionally regardless of what it receives.

---

## Confirmed across 6 sites

Tested after `page.go()` returns (page fully loaded) on 6 different sites and stacks:

| Site | Expression | Result |
|---|---|---|
| `https://example.com` | `document.readyState === 'complete'` | `SyntaxError: Unexpected token ')'` |
| `https://books.toscrape.com` | `document.readyState === 'complete'` | `SyntaxError: Unexpected token ')'` |
| `https://httpbin.org` | `document.readyState === 'complete'` | `SyntaxError: Unexpected token ')'` |
| `https://en.wikipedia.org` | `document.readyState === 'complete'` | `SyntaxError: Unexpected token ')'` |
| `https://var.parts` | `document.readyState === 'complete'` | `SyntaxError: Unexpected token ')'` |
| `https://testtrack.org` | `document.readyState === 'complete'` | `SyntaxError: Unexpected token ')'` |

`document.readyState === 'complete'` is guaranteed true after `page.go()` returns. The consistent `SyntaxError` across all sites rules out page state, site-specific JS, or network conditions as contributing factors.

---

## Suggested fix

Remove the pre-wrap in the Java client's `waitForFunction` implementation. The engine (post-#163) handles wrapping on its own. The client should pass the expression string as-is.

If the client needs to distinguish bare expressions from arrow functions for other reasons, detect the `() =>` prefix and skip wrapping when it is already present. But the simplest correct fix is to remove the client-side wrap entirely.

---

## Workaround

`page.evaluate()` is unaffected. Poll manually:

```java
long deadline = System.currentTimeMillis() + 10_000;
while (!(Boolean) page.evaluate("document.readyState === 'complete'")) {
    if (System.currentTimeMillis() > deadline) throw new RuntimeException("timed out");
    Thread.sleep(100);
}
```

---

## Reproducing with the Java test suite

The full reproduction suite is available at **[github.com/lana-20/vibium-java-test](https://github.com/lana-20/vibium-java-test)**.

### Setup

```sh
git clone https://github.com/lana-20/vibium-java-test ~/vibium-java-test
cd ~/vibium-java-test

# Place vibium-26.5.31.jar and gson-2.11.0.jar in this directory, then:
javac -cp ".:vibium-26.5.31.jar:gson-2.11.0.jar" -d out \
  src/VibiumJavaApiTests.java src/VibiumBugHardening.java src/B3Repro.java
```

### Run the minimal repro (fastest)

```sh
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" B3Repro
```

### Run the full B3 hardening suite (15 probes — bare, lambda, flag, 6 sites)

```sh
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" VibiumBugHardening B3
```

`VibiumBugHardening` accepts a bug number argument (`B1`–`B10`) to run only that section. Omit it to run all 10 bugs.

### Expected output

```
╔══ B3: page.waitForFunction() … ══╗
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

========================================================================
  Bug reproductions confirmed: 15
  Unexpected results:          0
========================================================================
```

Results on v26.5.31: **15 confirmed / 0 unexpected** (2026-06-06).
