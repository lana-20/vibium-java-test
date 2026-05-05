import com.vibium.*;
import com.vibium.types.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * Probes page.expose() beyond typeof — actually invokes the exposed function
 * from JS and checks whether the Java callback fires, args are received,
 * and return values propagate back to JS.
 */
public class ExposeProbe {

    @FunctionalInterface interface Thunk { void run() throws Exception; }

    static int passed, failed;

    public static void main(String[] args) throws Exception {
        var bro = Vibium.start(new StartOptions().headless(true));
        var page = bro.page();
        try {
            // ── 1. typeof check (baseline) ──────────────────────────────────
            probe("expose → setContent → typeof", page, () -> {
                page.expose("fn1", a -> "ok");
                page.setContent("<html><body></body></html>");
                Object t = page.evaluate("typeof fn1");
                if (!"function".equals(t)) throw new AssertionError("typeof: " + t);
            });

            // ── 2. Call from JS → Java callback fires ────────────────────────
            probe("expose → setContent → call → callback fires", page, () -> {
                AtomicBoolean fired = new AtomicBoolean();
                page.expose("fn2", a -> { fired.set(true); return "ok"; });
                page.setContent("<html><body></body></html>");
                page.evaluate("fn2()");
                Thread.sleep(400);
                if (!fired.get()) throw new AssertionError("callback never fired");
            });

            // ── 3. JS receives return value ──────────────────────────────────
            probe("expose → setContent → return value reaches JS", page, () -> {
                page.expose("fn3", a -> "hello-from-java");
                page.setContent("<html><body></body></html>");
                Object result = page.evaluate("fn3()");
                if (!"hello-from-java".equals(result))
                    throw new AssertionError("got: " + result);
            });

            // ── 4. JS passes args → Java receives them ───────────────────────
            probe("expose → setContent → args passed to Java callback", page, () -> {
                AtomicReference<Object[]> received = new AtomicReference<>();
                page.expose("fn4", a -> { received.set(a); return null; });
                page.setContent("<html><body></body></html>");
                page.evaluate("fn4('hello', 42)");
                Thread.sleep(400);
                Object[] got = received.get();
                if (got == null) throw new AssertionError("callback never fired");
                if (!Arrays.asList(got).contains("hello"))
                    throw new AssertionError("args not received: " + Arrays.toString(got));
            });

            // ── 5. expose → go → call ────────────────────────────────────────
            probe("expose → go(example.com) → typeof + call", page, () -> {
                AtomicBoolean fired = new AtomicBoolean();
                page.expose("fn5", a -> { fired.set(true); return "ok"; });
                page.go("https://example.com");
                Object t = page.evaluate("typeof fn5");
                if (!"function".equals(t)) throw new AssertionError("typeof: " + t);
                page.evaluate("fn5()");
                Thread.sleep(400);
                if (!fired.get()) throw new AssertionError("callback never fired");
            });

            // ── 6. go → expose → call (same page) ───────────────────────────
            probe("go(example.com) → expose → typeof + call", page, () -> {
                AtomicBoolean fired = new AtomicBoolean();
                page.go("https://example.com");
                page.expose("fn6", a -> { fired.set(true); return "ok"; });
                Object t = page.evaluate("typeof fn6");
                if (!"function".equals(t)) throw new AssertionError("typeof: " + t);
                page.evaluate("fn6()");
                Thread.sleep(400);
                if (!fired.get()) throw new AssertionError("callback never fired");
            });

            // ── 7. Call from inline script in setContent HTML ────────────────
            probe("expose → setContent with inline <script> call → callback fires", page, () -> {
                AtomicBoolean fired = new AtomicBoolean();
                page.expose("fn7", a -> { fired.set(true); return "ok"; });
                page.setContent("<html><body><script>fn7();</script></body></html>");
                Thread.sleep(400);
                if (!fired.get()) throw new AssertionError("callback never fired from inline script");
            });

        } finally {
            bro.stop();
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.printf("  passed: %d  failed: %d  total: %d%n", passed, failed, passed + failed);
        System.out.println("=".repeat(60));
    }

    static void probe(String name, Page page, Thunk t) {
        System.out.printf("%-60s", name);
        try {
            page.removeAllListeners("error");
            t.run();
            System.out.println("PASS");
            passed++;
        } catch (Throwable e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            System.out.println("FAIL  " + msg);
            failed++;
        }
    }
}
