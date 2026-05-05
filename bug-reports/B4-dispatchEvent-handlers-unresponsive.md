# Bug: `el.dispatchEvent()` never triggers event handlers — `onclick` and `addEventListener` both unresponsive (Java client)

## Summary

`el.dispatchEvent(String type)` dispatches without throwing, but no registered event handler is ever invoked — neither `onclick` attribute handlers nor `addEventListener` listeners. This applies to standard events (`click`), custom events, and every element type tested. `el.click()` works correctly as a workaround for actual click events.

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

        page.setContent("""
            <html><body>
              <div id="t" onclick="this.dataset.fired='1'">click me</div>
            </body></html>
        """);

        page.find("#t").dispatchEvent("click");

        String fired = page.find("#t").getAttribute("data-fired");
        System.out.println(fired); // null — handler never ran
    }
}
```

**Expected:** the `onclick` handler runs, setting `data-fired="1"` on the element.

**Actual:** `data-fired` is `null`. `dispatchEvent` returns without error but the handler is never invoked.

## All handler types tested

| Variant | Handler type | Result |
|---|---|---|
| `<div onclick="...">` | `onclick` attribute | handler not fired |
| `<button onclick="...">` | `onclick` attribute | handler not fired |
| `div` + `addEventListener('click', ...)` | `addEventListener` | handler not fired |
| `button` + `addEventListener('click', ...)` | `addEventListener` | handler not fired |
| `div` + `addEventListener('custom-evt', ...)` | custom event | handler not fired |

Five handler variants across two element types and two registration mechanisms — all unresponsive. The event is dispatched without error in every case; the failure is silent.

## Contrast with `el.click()`

`el.click()` correctly triggers both `onclick` attributes and `addEventListener('click', ...)` handlers in the same page contexts where `dispatchEvent("click")` fails. The bug is specific to the `dispatchEvent` pathway, not to event handling in general.

## Confirmed across sites

Tested by injecting a `<div onclick="...">` into each live page and calling `dispatchEvent("click")`:

| Site | Result |
|---|---|
| `https://example.com` | handler not fired |
| `https://books.toscrape.com` | handler not fired |
| `https://httpbin.org` | handler not fired |
| `https://en.wikipedia.org` | handler not fired |
| `https://var.parts` | handler not fired |
| `https://testtrack.org` | handler not fired |

## Likely root cause

`el.dispatchEvent()` likely synthesizes the event via a BiDi command that does not go through the browser's full event dispatch pipeline. The event may be constructed and dispatched in an isolated context, or the element reference may not resolve to the live DOM node correctly — meaning the event fires on a detached or incorrect target rather than the element with registered handlers.

## Workaround

Use `el.click()` for click events:

```java
page.find("#t").click(); // correctly triggers onclick and addEventListener('click') handlers
```

No workaround exists for custom events or non-click event types.

## Test suite references

- Regression suite skips: [`VibiumJavaApiTests.java#L335`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumJavaApiTests.java#L335)
- Hardening probes (5 handler variants + 6 sites): [`VibiumBugHardening.java#L170`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumBugHardening.java#L170)

## Hardening results

Reproduced 11 / 11 probes with 0 unexpected passes.

```
div onclick attribute                                    BUG  onclick on div did not fire
button onclick attribute                                 BUG  onclick on button did not fire
div addEventListener('click')                            BUG  addEventListener not triggered on div
button addEventListener('click')                         BUG  addEventListener not triggered on button
custom event + addEventListener                          BUG  custom event listener not triggered
dispatchEvent on real page element [example.com]         BUG  onclick on injected div not fired on https://example.com
dispatchEvent on real page element [books.toscrape.com]  BUG  onclick on injected div not fired on https://books.toscrape.com
dispatchEvent on real page element [httpbin.org]         BUG  onclick on injected div not fired on https://httpbin.org
dispatchEvent on real page element [en.wikipedia.org]    BUG  onclick on injected div not fired on https://en.wikipedia.org
dispatchEvent on real page element [var.parts]           BUG  onclick on injected div not fired on https://var.parts
dispatchEvent on real page element [testtrack.org]       BUG  onclick on injected div not fired on https://testtrack.org
```
