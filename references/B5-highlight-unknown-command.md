# Bug: `el.highlight()` throws "Unknown command 'vibium:element.highlight'" (Java client)

## Summary

`el.highlight()` fails on every element type and every page context with `"Unknown command 'vibium:element.highlight'"`. The command is not recognized by the underlying BiDi layer, indicating either a missing server-side implementation or a command name mismatch. The method is completely unusable.

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

        page.go("https://example.com");
        page.findAll("a").get(0).highlight(); // throws
    }
}
```

**Expected:** the element is visually highlighted in the browser (e.g. outlined or overlaid).

**Actual:**
```
com.vibium.errors.VibiumException: Unknown command 'vibium:element.highlight'
```

## All element types tested

| Element | Context | Result |
|---|---|---|
| `<p>` | `setContent` | `Unknown command 'vibium:element.highlight'` |
| `<div>` | `setContent` | `Unknown command 'vibium:element.highlight'` |
| `<button>` | `setContent` | `Unknown command 'vibium:element.highlight'` |
| `<a>` | `setContent` | `Unknown command 'vibium:element.highlight'` |
| `<input>` | `setContent` | `Unknown command 'vibium:element.highlight'` |

## Confirmed across sites

Tested on the first `<a>` element of each live page:

| Site | Result |
|---|---|
| `https://example.com` | `Unknown command 'vibium:element.highlight'` |
| `https://books.toscrape.com` | `Unknown command 'vibium:element.highlight'` |
| `https://httpbin.org` | `Unknown command 'vibium:element.highlight'` |
| `https://en.wikipedia.org` | `Unknown command 'vibium:element.highlight'` |
| `https://var.parts` | `Unknown command 'vibium:element.highlight'` |
| `https://testtrack.org` | `Unknown command 'vibium:element.highlight'` |

## Likely root cause

The error `"Unknown command 'vibium:element.highlight'"` is a BiDi protocol error — the server received the command name but has no handler registered for it. This suggests `el.highlight()` was added to the Java client API but the corresponding server-side command was never implemented, or was implemented under a different name.

## Workaround

No direct workaround. A visual highlight can be approximated via `page.evaluate()`:

```java
page.evaluate("""
    (function() {
        var el = document.querySelector('a');
        el.style.outline = '3px solid red';
        setTimeout(() => el.style.outline = '', 2000);
    })()
""");
```

## Test suite references

- Regression suite skip: [`VibiumJavaApiTests.java#L342`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumJavaApiTests.java#L342)
- Hardening probes (5 element types + 6 sites): [`VibiumBugHardening.java#L231`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumBugHardening.java#L231)

## Hardening results

Reproduced 11 / 11 probes with 0 unexpected passes.

```
highlight <p>                                BUG  Unknown command 'vibium:element.highlight'
highlight <div>                              BUG  Unknown command 'vibium:element.highlight'
highlight <button>                           BUG  Unknown command 'vibium:element.highlight'
highlight <a>                                BUG  Unknown command 'vibium:element.highlight'
highlight <input>                            BUG  Unknown command 'vibium:element.highlight'
highlight on real page [example.com]         BUG  Unknown command 'vibium:element.highlight'
highlight on real page [books.toscrape.com]  BUG  Unknown command 'vibium:element.highlight'
highlight on real page [httpbin.org]         BUG  Unknown command 'vibium:element.highlight'
highlight on real page [en.wikipedia.org]    BUG  Unknown command 'vibium:element.highlight'
highlight on real page [var.parts]           BUG  Unknown command 'vibium:element.highlight'
highlight on real page [testtrack.org]       BUG  Unknown command 'vibium:element.highlight'
```
