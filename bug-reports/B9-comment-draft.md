# Comment draft — issue #137 (clock partial fix)

---

The fix in [#167](https://github.com/VibiumDev/vibium/pull/167) resolved the `"time is required"` error for ISO-8601 and epoch-millisecond strings — `clock.setFixedTime()`, `clock.pauseAt()`, and `clock.setSystemTime()` now accept those formats without throwing. However, `ClockOptions.time()` is still broken on `v26.5.31`: the value is accepted without error but silently ignored, and the clock starts at the real system time instead of the specified date.

**`ClockOptions.time()` silently ignored:**
```java
page.setContent("<html><body></body></html>");
page.clock().install(new ClockOptions().time("2024-01-01T00:00:00Z"));
Object year = page.evaluate("new Date().getFullYear()");
System.out.println(year); // 2025 — expected 2024
```

No exception is thrown. The `install()` call returns successfully. But `new Date().getFullYear()` returns the real system year, not `2024`. The `time` value in `ClockOptions` has no effect.

**Status of clock time methods on v26.5.31:**

| Method | Format | Status |
|---|---|---|
| `clock.setFixedTime("2024-06-15T12:00:00Z")` | ISO-8601 | ✅ PASS (fixed in [#167](https://github.com/VibiumDev/vibium/pull/167)) |
| `clock.setFixedTime("1718445600000")` | epoch-ms string | ✅ PASS (fixed in [#167](https://github.com/VibiumDev/vibium/pull/167)) |
| `clock.pauseAt("2025-01-01T00:00:00Z")` | ISO-8601 | ✅ PASS (fixed in [#167](https://github.com/VibiumDev/vibium/pull/167)) |
| `clock.setSystemTime("2024-12-31T23:59:59Z")` | ISO-8601 | ✅ PASS (fixed in [#167](https://github.com/VibiumDev/vibium/pull/167)) |
| `clock.install(new ClockOptions().time("2024-01-01T00:00:00Z"))` | ISO-8601 | ❌ ignored — clock starts at system time |

The `ClockOptions.time()` field is likely not being serialized into the `clock.install` BiDi command payload, or is sent under a key the server does not recognize. The server accepts the command (no error) but the time value never reaches the clock implementation.

**Environment:** `com.vibium:vibium:26.5.31` · Java 21 · macOS 15.3 · Chrome for Testing 147.0.7727.56

---

## Reproducing

**Option 1 — Claude Code skill** (recommended): install the [vibium-java-test](https://github.com/lana-20/vibium-java-test) regression skill and run:

```
/vibium-java-test harden B9
```

This runs all B9 clock-time probes and prints a result line per probe. See the [skill README](https://github.com/lana-20/vibium-java-test/blob/main/README.md) for setup instructions.

**Option 2 — direct Java:**
```sh
git clone https://github.com/lana-20/vibium-java-test ~/vibium-java-test
cd ~/vibium-java-test
# place vibium-26.5.31.jar and gson-2.11.0.jar in this directory, then:
javac -cp ".:vibium-26.5.31.jar:gson-2.11.0.jar" -d out src/VibiumJavaApiTests.java src/VibiumBugHardening.java
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" VibiumBugHardening B9
```
