# Comment draft — issue #129 (waitForURL partial fix)

---

The fix in [#167](https://github.com/VibiumDev/vibium/pull/167) resolved the original `"pattern is required"` error for most pattern forms — exact URLs and simple globs now work. However, two cases still fail on `v26.5.31`:

**Path-separator glob (`**/*.html`):**
```java
page.go("https://example.com/index.html");
page.waitForURL("**/*.html", new WaitOptions().timeout(4000));
// VibiumTimeoutException: timed out waiting for URL to match pattern
```
The URL is `https://example.com/index.html`, which should match `**/*.html`. It does not — the wait times out.

**Regex pattern (`.*example.*`):**
```java
page.go("https://example.com");
page.waitForURL(".*example.*", new WaitOptions().timeout(4000));
// VibiumTimeoutException: timed out waiting for URL to match pattern
```
`https://example.com` contains "example" and should match `.*example.*`. It does not.

Both patterns are accepted without error (no `"pattern is required"`), so the parameter plumbing from [#129](https://github.com/VibiumDev/vibium/issues/129) is fixed. The remaining issue is in match evaluation: path-separator globbing (`/` boundary) and regex-style patterns are not evaluated correctly.

**Pattern status after [#167](https://github.com/VibiumDev/vibium/pull/167):**

| Pattern | Status |
|---|---|
| `"https://example.com"` | ✅ PASS |
| `"https://example.com/"` | ✅ PASS |
| `"*example*"` | ✅ PASS |
| `"**example**"` | ✅ PASS |
| `"https://**"` | ✅ PASS |
| `"**/*.html"` | ❌ still fails (timeout) |
| `".*example.*"` | ❌ still fails (timeout) |

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

**Reproduce B1:**
```
/vibium-java-test harden B1
```

This runs all 7 pattern variants and prints a result line per probe. Any pattern that still fails will show `BUG timeout`.

**Option 2 — direct Java:**
```sh
git clone https://github.com/lana-20/vibium-java-test ~/vibium-java-test
cd ~/vibium-java-test
# place vibium-26.5.31.jar and gson-2.11.0.jar here, then:
javac -cp ".:vibium-26.5.31.jar:gson-2.11.0.jar" -d out src/VibiumJavaApiTests.java src/VibiumBugHardening.java
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" VibiumBugHardening B1
```
