# Comment draft — issue #130 (addScript partial fix)

---

The fix in [#167](https://github.com/VibiumDev/vibium/pull/167) resolved one of the two failure modes from [#130](https://github.com/VibiumDev/vibium/issues/130): `setContent → addScript → evaluate` now works on most pages. However, cross-navigation persistence — the primary use case for `addScript` — is still broken on `v26.5.31`:

**`addScript → go() → evaluate` still returns `null`:**
```java
page.addScript("window.__test = 'hello';");
page.go("https://example.com");
Object val = page.evaluate("window.__test");
System.out.println(val); // null — expected "hello"
```

The script is accepted without error, the navigation completes, but the value is never visible after `go()`. This is the core use case: injecting a script that persists across navigations. It does not work.

**What works after [#167](https://github.com/VibiumDev/vibium/pull/167):**
```java
page.setContent("<html><body></body></html>");
page.addScript("window.__test = 'hello';");
Object val = page.evaluate("window.__test"); // "hello" ✓
```
This works only when `addScript` is called after `setContent` and the page is not navigated away. As soon as `go()` is called after `addScript`, the value is lost.

**Failure summary on v26.5.31:**

| Ordering | Status |
|---|---|
| `setContent → addScript → evaluate` | ✅ PASS (fixed in [#167](https://github.com/VibiumDev/vibium/pull/167)) |
| `addScript → go() → evaluate` | ❌ still null |
| `go() → addScript → reload() → evaluate` | ❌ still null |

The broken cases cover the scenario where `addScript` is expected to behave like `addInitScript` — injecting into every page context including after navigation. `context.addInitScript()` correctly handles this and can be used as a workaround, but `page.addScript()` should work the same way.

**Environment:** `com.vibium:vibium:26.5.31` · Java 21 · macOS 15.3 · Chrome for Testing 147.0.7727.56

---

## Reproducing

**Option 1 — Claude Code skill** (recommended)

[vibium-java-test](https://github.com/lana-20/vibium-java-test) is a Claude Code skill — a slash command that handles compiling, running, and reporting in a single invocation.

**Setup (once):**
```sh
git clone https://github.com/lana-20/vibium-java-test ~/vibium-java-test
cp ~/vibium-java-test/SKILL.md ~/.claude/skills/vibium-java-test/SKILL.md
# place vibium-26.5.31.jar and gson-2.11.0.jar in ~/vibium-java-test/
```

**Reproduce B2:**
```
/vibium-java-test harden B2
```

This runs all 3 invocation orderings across 6 sites and prints a result line per probe. Broken orderings show `BUG got: null`.

**Option 2 — direct Java:**
```sh
git clone https://github.com/lana-20/vibium-java-test ~/vibium-java-test
cd ~/vibium-java-test
# place vibium-26.5.31.jar and gson-2.11.0.jar here, then:
javac -cp ".:vibium-26.5.31.jar:gson-2.11.0.jar" -d out src/VibiumJavaApiTests.java src/VibiumBugHardening.java
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" VibiumBugHardening B2
```
