# Bug: `el.dragTo(Element)` throws "dragTo requires 'target' parameter" despite correct `Element` argument (Java client)

## Summary

`el.dragTo(Element target)` always throws `"dragTo requires 'target' parameter"` even when called with a valid, live `Element` reference obtained from the same page. The Java client accepts the argument without complaint, but the server rejects the request because the target element is not being serialized into the BiDi request body correctly.

Tested on `draggable="true"` elements in synthetic pages and on 4 dedicated drag-and-drop demo sites with native draggable widgets — all fail identically.

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
              <div id="src" draggable="true" style="width:50px;height:50px">A</div>
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

## Synthetic page variants

Both elements resolved via `page.find()` immediately before the call; source has `draggable="true"`:

| Source | Target | Result |
|---|---|---|
| `#a` (`draggable="true"`) | `#b` | `dragTo requires 'target' parameter` |
| `div.src` (`draggable="true"`) | `div.dst` | `dragTo requires 'target' parameter` |

## Dedicated drag-and-drop demo sites

Four different drag-and-drop implementations tested with the correct semantic source and drop-target elements for each page:

| Site | Source | Target | Result |
|---|---|---|---|
| `testtrack.org/drag-drop-demo` | `#draggable-1` | `#container1` | `dragTo requires 'target' parameter` |
| `the-internet.herokuapp.com/drag_and_drop` | `#column-a` | `#column-b` | `dragTo requires 'target' parameter` |
| `demoqa.com/droppable` | `#draggable` | `#droppable` | `dragTo requires 'target' parameter` |
| `www.w3schools.com` (HTML5 drag-drop demo) | `#img1` | `#div2` | `dragTo requires 'target' parameter` |

The error is identical across HTML5 native drag-and-drop, jQuery UI draggable/droppable, and custom implementations. It fires before any drag mechanics are attempted, confirming this is a parameter serialization failure in the Java client, not a page-compatibility issue.

## Likely root cause

The error originates from the server. The Java client calls `dragTo(Element target)` and constructs the BiDi request, but the `target` field is either missing from the request body, sent as `null`, or encoded under the wrong key. The server receives the request, validates that `target` is required, finds it absent, and rejects the call.

This is the same class of serialization bug as B1 (`waitForURL` — `"pattern is required"`): the Java client has the correct method signature but fails to include the argument in the outgoing request.

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
- Hardening probes: [`VibiumBugHardening.java#L264`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumBugHardening.java#L264)

## Hardening results

Reproduced 6 / 6 targeted probes with 0 unexpected passes.

```
setContent draggable: #a → #b                                   BUG  dragTo requires 'target' parameter
setContent draggable: div.src → div.dst                         BUG  dragTo requires 'target' parameter
dragTo demo [testtrack.org] #draggable-1 → #container1          BUG  dragTo requires 'target' parameter
dragTo demo [the-internet.herokuapp.com] #column-a → #column-b  BUG  dragTo requires 'target' parameter
dragTo demo [demoqa.com] #draggable → #droppable                BUG  dragTo requires 'target' parameter
dragTo demo [www.w3schools.com] #img1 → #div2                   BUG  dragTo requires 'target' parameter
```
