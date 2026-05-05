# Bug: `el.dragTo(Element)` throws "dragTo requires 'target' parameter" despite correct `Element` argument (Java client)

## Summary

`el.dragTo(Element target)` always throws `"dragTo requires 'target' parameter"` even when called with a valid, live `Element` reference obtained from the same page. The Java client accepts the argument without complaint, but the server rejects the request because the target element is not being serialized into the BiDi request body correctly.

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
              <div id="src" style="width:50px;height:50px">A</div>
              <div id="dst" style="width:50px;height:50px">B</div>
            </body></html>
        """);

        var src = page.find("#src");
        var dst = page.find("#dst");
        src.dragTo(dst); // throws
    }
}
```

**Expected:** the `src` element is dragged to the position of `dst`.

**Actual:**
```
com.vibium.errors.VibiumException: dragTo requires 'target' parameter
```

## All element pair variants tested

Both elements are resolved via `page.find()` on the same page immediately before the call:

| Source | Target | Result |
|---|---|---|
| `#a` | `#b` | `dragTo requires 'target' parameter` |
| `button:first-child` | `button:last-child` | `dragTo requires 'target' parameter` |
| `div.src` | `div.dst` | `dragTo requires 'target' parameter` |

## Confirmed across sites

Tested by dragging the first `<a>` element to the second on each live page:

| Site | Result |
|---|---|
| `https://books.toscrape.com` | `dragTo requires 'target' parameter` |
| `https://httpbin.org` | `dragTo requires 'target' parameter` |
| `https://en.wikipedia.org` | `dragTo requires 'target' parameter` |
| `https://var.parts` | `dragTo requires 'target' parameter` |
| `https://testtrack.org` | `dragTo requires 'target' parameter` |

(`example.com` was excluded — it only has one `<a>` element.)

## Likely root cause

The error comes from the server, not the client. The Java client calls `dragTo(Element target)` and constructs the BiDi request, but the `target` field is either missing from the request body, sent as `null`, or encoded under the wrong key. The server receives the request, validates that `target` is required, finds it absent, and rejects the call.

This is the same class of serialization bug as B1 (`waitForURL` — `pattern is required`): the Java client has the right method signature but fails to include the argument in the outgoing request.

## Workaround

None available via the Java API. A drag can be approximated with mouse primitives if coordinates are known:

```java
var bounds = src.boundingBox();
var dstBounds = dst.boundingBox();
page.mouse().move(bounds.x() + bounds.width() / 2, bounds.y() + bounds.height() / 2);
page.mouse().down();
page.mouse().move(dstBounds.x() + dstBounds.width() / 2, dstBounds.y() + dstBounds.height() / 2);
page.mouse().up();
```

## Test suite references

- Regression suite skip: [`VibiumJavaApiTests.java#L343`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumJavaApiTests.java#L343)
- Hardening probes (3 element pairs + 5 real sites): [`VibiumBugHardening.java#L264`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumBugHardening.java#L264)

## Hardening results

Reproduced 9 / 9 probes with 0 unexpected passes.

```
dragTo #a → #b                                 BUG  dragTo requires 'target' parameter
dragTo button:first-child → button:last-child  BUG  dragTo requires 'target' parameter
dragTo div.src → div.dst                       BUG  dragTo requires 'target' parameter
dragTo on real page links [books.toscrape.com] BUG  dragTo requires 'target' parameter
dragTo on real page links [httpbin.org]        BUG  dragTo requires 'target' parameter
dragTo on real page links [en.wikipedia.org]   BUG  dragTo requires 'target' parameter
dragTo on real page links [var.parts]          BUG  dragTo requires 'target' parameter
dragTo on real page links [testtrack.org]      BUG  dragTo requires 'target' parameter
```
