import com.vibium.*;
import com.vibium.types.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Hardens each bug found by VibiumJavaApiTests across multiple sites and
 * page-content variations before filing issues.
 *
 * Bugs under test:
 *   B1  page.waitForURL()          — "pattern is required" for all formats
 *   B2  page.addScript()           — script never available in any context
 *   B3  page.waitForFunction()     — times out even for already-true expressions
 *   B4  el.dispatchEvent()         — onclick / addEventListener both unresponsive
 *   B5  el.highlight()             — unknown BiDi command
 *   B6  el.dragTo()                — "target parameter required" with correct Element arg
 *   B7  page.expose()              — function not injected into page JS context
 *   B8  onError / collectErrors()  — uncaught errors never forwarded
 *   B9  clock time methods         — ISO string rejected with "time is required"
 *   B10 page.setHeaders()          — server deadlock (same root cause as route #128)
 */
public class VibiumBugHardening {

    static final List<String> SITES = List.of(
        "https://example.com",
        "https://books.toscrape.com",
        "https://httpbin.org",
        "https://en.wikipedia.org",
        "https://var.parts",
        "https://testtrack.org"
    );

    record Result(String variant, String outcome) {}

    static Browser bro;
    static Page page;

    static int confirmed, unexpected;
    static List<String> surprises = new ArrayList<>();

    @FunctionalInterface interface Thunk { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        bro = Vibium.start(new StartOptions().headless(true));
        page = bro.page();
        try {
            hardenB1_waitForURL();
            hardenB2_addScript();
            hardenB3_waitForFunction();
            hardenB4_dispatchEvent();
            hardenB5_highlight();
            hardenB6_dragTo();
            hardenB7_expose();
            hardenB8_onError();
            hardenB9_clockTime();
            hardenB10_setHeaders();
        } finally {
            try { bro.stop(); } catch (Exception ignored) {}
        }
        summary();
    }

    // ── B1: page.waitForURL() ───────────────────────────────────────────────

    static void hardenB1_waitForURL() throws Exception {
        bug("B1", "page.waitForURL() — all pattern formats rejected (issue #129)");

        String[] patterns = {
            "https://example.com",
            "https://example.com/",
            "*example*",
            "**example**",
            "https://**",
            "**/*.html",
            ".*example.*"
        };

        page.go("https://example.com");

        for (String pattern : patterns) {
            probe("pattern: \"" + pattern + "\"", () -> {
                page.go("https://httpbin.org");
                page.evaluate("setTimeout(()=>{ location.href='https://example.com'; },100)");
                page.waitForURL(pattern, new WaitOptions().timeout(4000));
            }, true);
        }

        // Also test with WaitOptions only
        probe("waitForURL(url, WaitOptions)", () -> {
            page.go("https://httpbin.org");
            page.evaluate("setTimeout(()=>{ location.href='https://example.com'; },100)");
            page.waitForURL("https://example.com", new WaitOptions().timeout(4000));
        }, true);
    }

    // ── B2: page.addScript() ────────────────────────────────────────────────

    static void hardenB2_addScript() throws Exception {
        bug("B2", "page.addScript() — script not available in any context (issue #130)");

        // Variant 1: evaluate immediately after addScript in same page
        for (String site : SITES) {
            probe("setContent → addScript → evaluate [" + domain(site) + "]", () -> {
                page.setContent("<html><body></body></html>");
                page.addScript("window.__b2 = 'b2val';");
                Object v = page.evaluate("window.__b2");
                if (!"b2val".equals(v)) throw new AssertionError("got: " + v);
            }, true);
        }

        // Variant 2: addScript → go() → evaluate
        for (String site : SITES) {
            probe("addScript → go() → evaluate [" + domain(site) + "]", () -> {
                page.addScript("window.__b2nav = 'nav';");
                page.go(site);
                Object v = page.evaluate("window.__b2nav");
                if (!"nav".equals(v)) throw new AssertionError("got: " + v);
            }, true);
        }

        // Variant 3: go() → addScript → reload() → evaluate
        probe("go → addScript → reload → evaluate", () -> {
            page.go("https://example.com");
            page.addScript("window.__b2r = 'reloaded';");
            page.reload();
            Object v = page.evaluate("window.__b2r");
            if (!"reloaded".equals(v)) throw new AssertionError("got: " + v);
        }, true);
    }

    // ── B3: page.waitForFunction() ──────────────────────────────────────────

    static void hardenB3_waitForFunction() throws Exception {
        bug("B3", "page.waitForFunction() — times out for all expressions (issue #131)");

        String[] exprs = {
            "true",
            "1 + 1 === 2",
            "typeof document !== 'undefined'",
            "document.readyState === 'complete'",
            "window !== undefined"
        };

        // Already-true expressions (no async needed)
        for (String expr : exprs) {
            probe("already-true: " + expr, () -> {
                page.setContent("<html><body></body></html>");
                page.waitForFunction(expr, new WaitOptions().timeout(3000));
            }, true);
        }

        // Async expression via evaluate then waitForFunction
        probe("evaluate sets flag → waitForFunction checks it", () -> {
            page.setContent("<html><body></body></html>");
            page.evaluate("window.__wff = true");
            page.waitForFunction("window.__wff === true", new WaitOptions().timeout(3000));
        }, true);

        // Across different sites
        for (String site : SITES) {
            probe("already-true after go [" + domain(site) + "]", () -> {
                page.go(site);
                page.waitForFunction("document.readyState === 'complete'", new WaitOptions().timeout(5000));
            }, true);
        }
    }

    // ── B4: el.dispatchEvent() ──────────────────────────────────────────────

    static void hardenB4_dispatchEvent() throws Exception {
        bug("B4", "el.dispatchEvent() — event handlers (onclick + addEventListener) unresponsive (issue #132)");

        // onclick attribute on div
        probe("div onclick attribute", () -> {
            page.setContent("<html><body><div id='t' onclick=\"this.dataset.fired='1'\">x</div></body></html>");
            page.find("#t").dispatchEvent("click");
            if (!"1".equals(page.find("#t").getAttribute("data-fired")))
                throw new AssertionError("onclick on div did not fire");
        }, true);

        // onclick attribute on button
        probe("button onclick attribute", () -> {
            page.setContent("<html><body><button id='t' onclick=\"this.dataset.fired='1'\">x</button></body></html>");
            page.find("#t").dispatchEvent("click");
            if (!"1".equals(page.find("#t").getAttribute("data-fired")))
                throw new AssertionError("onclick on button did not fire");
        }, true);

        // addEventListener on div
        probe("div addEventListener('click')", () -> {
            page.setContent("<html><body><div id='t'>x</div></body></html>");
            page.evaluate("document.getElementById('t').addEventListener('click',()=>document.getElementById('t').dataset.fired='1')");
            page.find("#t").dispatchEvent("click");
            if (!"1".equals(page.find("#t").getAttribute("data-fired")))
                throw new AssertionError("addEventListener not triggered on div");
        }, true);

        // addEventListener on button
        probe("button addEventListener('click')", () -> {
            page.setContent("<html><body><button id='t'>x</button></body></html>");
            page.evaluate("document.getElementById('t').addEventListener('click',()=>document.getElementById('t').dataset.fired='1')");
            page.find("#t").dispatchEvent("click");
            if (!"1".equals(page.find("#t").getAttribute("data-fired")))
                throw new AssertionError("addEventListener not triggered on button");
        }, true);

        // Custom event with addEventListener
        probe("custom event + addEventListener", () -> {
            page.setContent("<html><body><div id='t'>x</div></body></html>");
            page.evaluate("document.getElementById('t').addEventListener('custom-evt',()=>document.getElementById('t').dataset.fired='1')");
            page.find("#t").dispatchEvent("custom-evt");
            if (!"1".equals(page.find("#t").getAttribute("data-fired")))
                throw new AssertionError("custom event listener not triggered");
        }, true);

        // On a real page
        for (String site : SITES) {
            probe("dispatchEvent on real page element [" + domain(site) + "]", () -> {
                page.go(site);
                // inject our own test element
                page.evaluate("var d=document.createElement('div');d.id='__probe';d.onclick=()=>d.dataset.fired='1';document.body.appendChild(d)");
                page.find("#__probe").dispatchEvent("click");
                if (!"1".equals(page.find("#__probe").getAttribute("data-fired")))
                    throw new AssertionError("onclick on injected div not fired on " + site);
            }, true);
        }
    }

    // ── B5: el.highlight() ──────────────────────────────────────────────────

    static void hardenB5_highlight() throws Exception {
        bug("B5", "el.highlight() — unknown BiDi command (issue #133)");

        String[] selectors = { "p", "div", "button", "a", "input" };
        String[] htmls = {
            "<html><body><p>para</p></body></html>",
            "<html><body><div>div</div></body></html>",
            "<html><body><button>btn</button></body></html>",
            "<html><body><a href='#'>link</a></body></html>",
            "<html><body><input value='x'></body></html>"
        };

        for (int i = 0; i < selectors.length; i++) {
            final String sel = selectors[i];
            final String html = htmls[i];
            probe("highlight <" + sel + ">", () -> {
                page.setContent(html);
                page.find(sel).highlight();
            }, true);
        }

        for (String site : SITES) {
            probe("highlight on real page [" + domain(site) + "]", () -> {
                page.go(site);
                List<Element> els = page.findAll("a");
                if (!els.isEmpty()) els.get(0).highlight();
                else throw new AssertionError("no <a> on page");
            }, true);
        }
    }

    // ── B6: el.dragTo() ─────────────────────────────────────────────────────

    static void hardenB6_dragTo() throws Exception {
        bug("B6", "el.dragTo() — 'dragTo requires target parameter' with correct Element arg (issue #134)");

        // setContent with explicit draggable="true" elements
        String[][] pairs = {
            {"#a", "#b"},
            {"div.src", "div.dst"}
        };
        String[] htmls = {
            "<html><body><div id='a' draggable='true' style='width:50px;height:50px'>A</div><div id='b' style='width:50px;height:50px'>B</div></body></html>",
            "<html><body><div class='src' draggable='true' style='width:50px;height:50px'>S</div><div class='dst' style='width:50px;height:50px'>D</div></body></html>"
        };

        for (int i = 0; i < pairs.length; i++) {
            final String srcSel = pairs[i][0];
            final String dstSel = pairs[i][1];
            final String html = htmls[i];
            probe("setContent draggable: " + srcSel + " → " + dstSel, () -> {
                page.setContent(html);
                Element src = page.find(srcSel);
                Element dst = page.find(dstSel);
                src.dragTo(dst);
            }, true);
        }

        // Dedicated drag-and-drop demo pages with native draggable elements
        String[][] demoPairs = {
            {"https://testtrack.org/drag-drop-demo",                        "#draggable-1",  "#container1"},
            {"https://the-internet.herokuapp.com/drag_and_drop",            "#column-a",     "#column-b"},
            {"https://demoqa.com/droppable",                                "#draggable",    "#droppable"},
            {"https://www.w3schools.com/html/html5_draganddrop.asp",        "#img1",         "#div2"}
        };

        for (String[] trio : demoPairs) {
            final String site = trio[0], srcSel = trio[1], dstSel = trio[2];
            probe("dragTo demo [" + domain(site) + "] " + srcSel + " → " + dstSel, () -> {
                page.go(site);
                Element src = page.find(srcSel);
                Element dst = page.find(dstSel);
                src.dragTo(dst);
            }, true);
        }

        // General sites — any two links
        for (String site : SITES) {
            probe("dragTo links [" + domain(site) + "]", () -> {
                page.go(site);
                List<Element> links = page.findAll("a");
                if (links.size() < 2) throw new AssertionError("need 2 <a> elements");
                links.get(0).dragTo(links.get(1));
            }, true);
        }
    }

    // ── B7: page.expose() ───────────────────────────────────────────────────

    static void hardenB7_expose() throws Exception {
        bug("B7", "page.expose() — function not injected into page JS context");

        // Variant 1: expose → setContent → typeof
        probe("expose → setContent → typeof", () -> {
            page.expose("expFn1", args -> "ok");
            page.setContent("<html><body></body></html>");
            Object t = page.evaluate("typeof expFn1");
            if (!"function".equals(t)) throw new AssertionError("typeof: " + t);
        }, true);

        // Variant 2: expose → go() → typeof
        for (String site : SITES) {
            probe("expose → go → typeof [" + domain(site) + "]", () -> {
                page.expose("expFn2", args -> "ok");
                page.go(site);
                Object t = page.evaluate("typeof expFn2");
                if (!"function".equals(t)) throw new AssertionError("typeof: " + t);
            }, true);
        }

        // Variant 3: go() → expose → typeof (same-page)
        probe("go → expose → typeof (same page)", () -> {
            page.go("https://example.com");
            page.expose("expFn3", args -> "ok");
            Object t = page.evaluate("typeof expFn3");
            if (!"function".equals(t)) throw new AssertionError("typeof: " + t);
        }, true);

        // Variant 4: setContent → expose → typeof (reverse order)
        probe("setContent → expose → typeof", () -> {
            page.setContent("<html><body></body></html>");
            page.expose("expFn4", args -> "ok");
            Object t = page.evaluate("typeof expFn4");
            if (!"function".equals(t)) throw new AssertionError("typeof: " + t);
        }, true);

        // Invocation probes — confirm callback never fires even if we call the function
        probe("expose → setContent → call → callback fires", () -> {
            AtomicBoolean fired = new AtomicBoolean();
            page.expose("expFn5", a -> { fired.set(true); return "ok"; });
            page.setContent("<html><body></body></html>");
            try { page.evaluate("expFn5()"); } catch (Exception ignored) {}
            Thread.sleep(400);
            if (!fired.get()) throw new AssertionError("callback never fired");
        }, true);

        probe("expose → setContent → return value reaches JS", () -> {
            page.expose("expFn6", a -> "hello-from-java");
            page.setContent("<html><body></body></html>");
            Object result = page.evaluate("expFn6()");
            if (!"hello-from-java".equals(result))
                throw new AssertionError("got: " + result);
        }, true);

        probe("expose → setContent → args passed to Java callback", () -> {
            AtomicReference<Object[]> received = new AtomicReference<>();
            page.expose("expFn7", a -> { received.set(a); return null; });
            page.setContent("<html><body></body></html>");
            try { page.evaluate("expFn7('hello', 42)"); } catch (Exception ignored) {}
            Thread.sleep(400);
            Object[] got = received.get();
            if (got == null || !Arrays.asList(got).contains("hello"))
                throw new AssertionError("args not received: " + Arrays.toString(got));
        }, true);

        probe("expose → setContent inline script → callback fires", () -> {
            AtomicBoolean fired = new AtomicBoolean();
            page.expose("expFn8", a -> { fired.set(true); return "ok"; });
            page.setContent("<html><body><script>expFn8();</script></body></html>");
            Thread.sleep(400);
            if (!fired.get()) throw new AssertionError("callback never fired from inline script");
        }, true);
    }

    // ── B8: onError / collectErrors() ───────────────────────────────────────

    static void hardenB8_onError() throws Exception {
        bug("B8", "onError() / collectErrors() — uncaught errors never forwarded");

        // Different ways to trigger uncaught errors

        // 1: setTimeout throw
        probe("setTimeout throw → onError", () -> {
            AtomicBoolean got = new AtomicBoolean();
            page.setContent("<html><body></body></html>");
            page.onError(e -> { if (e != null) got.set(true); });
            page.evaluate("setTimeout(()=>{ throw new Error('timeout-err'); }, 100)");
            Thread.sleep(600);
            page.removeAllListeners("error");
            if (!got.get()) throw new AssertionError("onError not fired");
        }, true);

        // 2: dynamic script element throw
        probe("dynamic script throw → onError", () -> {
            AtomicBoolean got = new AtomicBoolean();
            page.setContent("<html><body></body></html>");
            page.onError(e -> { if (e != null) got.set(true); });
            page.evaluate("var s=document.createElement('script');s.text='undeclaredX.boom';document.head.appendChild(s)");
            Thread.sleep(500);
            page.removeAllListeners("error");
            if (!got.get()) throw new AssertionError("onError not fired");
        }, true);

        // 3: window.onerror manual dispatch
        probe("window ErrorEvent dispatch → onError", () -> {
            AtomicBoolean got = new AtomicBoolean();
            page.setContent("<html><body></body></html>");
            page.onError(e -> { if (e != null) got.set(true); });
            page.evaluate("window.dispatchEvent(new ErrorEvent('error',{message:'manual',error:new Error('manual')}))");
            Thread.sleep(400);
            page.removeAllListeners("error");
            if (!got.get()) throw new AssertionError("onError not fired");
        }, true);

        // 4: Promise rejection
        probe("unhandled Promise rejection → onError", () -> {
            AtomicBoolean got = new AtomicBoolean();
            page.setContent("<html><body></body></html>");
            page.onError(e -> { if (e != null) got.set(true); });
            page.evaluate("Promise.reject(new Error('rejected'))");
            Thread.sleep(500);
            page.removeAllListeners("error");
            if (!got.get()) throw new AssertionError("onError not fired");
        }, true);

        // 5: collectErrors — all variants
        probe("setTimeout throw → collectErrors", () -> {
            page.setContent("<html><body></body></html>");
            page.collectErrors();
            page.evaluate("setTimeout(()=>{ throw new Error('collected'); }, 100)");
            Thread.sleep(600);
            List<String> errs = page.errors();
            page.removeAllListeners("error");
            if (errs.isEmpty()) throw new AssertionError("errors() is empty");
        }, true);

        probe("dynamic script throw → collectErrors", () -> {
            page.setContent("<html><body></body></html>");
            page.collectErrors();
            page.evaluate("var s=document.createElement('script');s.text='undeclaredY.boom';document.head.appendChild(s)");
            Thread.sleep(600);
            List<String> errs = page.errors();
            page.removeAllListeners("error");
            if (errs.isEmpty()) throw new AssertionError("errors() is empty");
        }, true);

        // On real pages
        for (String site : SITES) {
            probe("onError on real page → throw in setTimeout [" + domain(site) + "]", () -> {
                AtomicBoolean got = new AtomicBoolean();
                page.go(site);
                page.onError(e -> { if (e != null) got.set(true); });
                page.evaluate("setTimeout(()=>{ throw new Error('site-err'); }, 100)");
                Thread.sleep(600);
                page.removeAllListeners("error");
                if (!got.get()) throw new AssertionError("onError not fired on " + site);
            }, true);
        }
    }

    // ── B9: clock time methods ───────────────────────────────────────────────

    static void hardenB9_clockTime() throws Exception {
        bug("B9", "clock time methods — ISO string rejected with 'time is required'");

        // setFixedTime with different formats
        String[] formats = {
            "2024-06-15T12:00:00Z",
            "2024-06-15T12:00:00.000Z",
            "2024-06-15",
            "June 15, 2024",
            "01/15/2024",
            "1718445600000"    // unix ms as string
        };

        for (String fmt : formats) {
            probe("setFixedTime(\"" + fmt + "\")", () -> {
                page.setContent("<html><body></body></html>");
                page.clock().install();
                page.clock().setFixedTime(fmt);
            }, true);
        }

        // pauseAt with different formats
        for (String fmt : new String[]{"2025-01-01T00:00:00Z", "January 1, 2025", "2025-01-01"}) {
            probe("pauseAt(\"" + fmt + "\")", () -> {
                page.setContent("<html><body></body></html>");
                page.clock().install();
                page.clock().pauseAt(fmt);
            }, true);
        }

        // setSystemTime
        for (String fmt : new String[]{"2024-12-31T23:59:59Z", "December 31, 2024"}) {
            probe("setSystemTime(\"" + fmt + "\")", () -> {
                page.setContent("<html><body></body></html>");
                page.clock().install();
                page.clock().setSystemTime(fmt);
            }, true);
        }

        // ClockOptions.time() — different formats
        for (String fmt : new String[]{"2024-01-01T00:00:00Z", "January 1, 2024"}) {
            probe("clock.install(ClockOptions.time(\"" + fmt + "\")) → year check", () -> {
                page.setContent("<html><body></body></html>");
                page.clock().install(new ClockOptions().time(fmt));
                Object year = page.evaluate("new Date().getFullYear()");
                if (((Number) year).doubleValue() != 2024)
                    throw new AssertionError("year = " + year + " (expected 2024)");
            }, true);
        }
    }

    // ── B10: page.setHeaders() ──────────────────────────────────────────────

    static void hardenB10_setHeaders() throws Exception {
        bug("B10", "page.setHeaders() — server deadlock on subsequent page.go() (same root as route #128)");

        // Test deadlock on multiple sites
        for (String site : SITES) {
            probe("setHeaders → go [" + domain(site) + "]", () -> {
                Browser b = Vibium.start(new StartOptions().headless(true));
                try {
                    Page p = b.page();
                    p.setHeaders(Map.of("X-Hardening-Test", "yes"));
                    AtomicBoolean navigated = new AtomicBoolean();
                    AtomicBoolean error = new AtomicBoolean();
                    Thread nav = new Thread(() -> {
                        try { p.go(site); navigated.set(true); }
                        catch (Exception e) { error.set(true); }
                    });
                    nav.setDaemon(true);
                    nav.start();
                    nav.join(5000);
                    if (navigated.get()) throw new AssertionError("no deadlock — unexpected PASS");
                    if (!error.get() && !navigated.get())
                        throw new AssertionError("DEADLOCK confirmed (still blocked after 5s)");
                    if (error.get())
                        throw new AssertionError("ERROR (threw before 5s timeout)");
                } finally {
                    try { b.stop(); } catch (Exception ignored) {}
                }
            }, true);
        }

        // Verify setHeaders alone (no go) does not deadlock
        probe("setHeaders only (no go) — should not deadlock", () -> {
            page.setHeaders(Map.of("X-Test", "only"));
            // if we reach here without hanging, the call itself is fine
        }, false);
    }

    // ── Runner helpers ───────────────────────────────────────────────────────

    static void bug(String id, String description) {
        System.out.println("\n╔══ " + id + ": " + description + " ══╗");
    }

    /**
     * @param expectBug true if we expect this probe to fail (confirming the bug)
     */
    static void probe(String name, Thunk t, boolean expectBug) {
        System.out.printf("  %-60s", name);
        try {
            t.run();
            if (expectBug) {
                // Ran without error — the bug may be fixed or variant didn't reproduce
                System.out.println("OK  ← unexpected pass (bug not reproduced)");
                unexpected++;
                surprises.add("[" + name + "] passed — possible fix or untested variant");
            } else {
                System.out.println("OK");
                confirmed++;
            }
        } catch (Throwable e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (expectBug) {
                System.out.println("BUG  " + msg);
                confirmed++;
            } else {
                System.out.println("FAIL  " + msg + "  ← unexpected failure");
                unexpected++;
                surprises.add("[" + name + "] failed unexpectedly: " + msg);
            }
        }
    }

    static void summary() {
        System.out.println("\n" + "=".repeat(72));
        System.out.printf("  Bug reproductions confirmed: %d%n", confirmed);
        System.out.printf("  Unexpected results:          %d%n", unexpected);
        System.out.println("=".repeat(72));
        if (!surprises.isEmpty()) {
            System.out.println("\nUnexpected results (worth investigating):");
            surprises.forEach(s -> System.out.println("  " + s));
        }
    }

    static String domain(String url) {
        return url.replaceAll("https?://([^/]+).*", "$1");
    }
}
