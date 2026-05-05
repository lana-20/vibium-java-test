# Bug: `clock.setFixedTime()`, `pauseAt()`, `setSystemTime()` reject all string formats with "time is required"; `ClockOptions.time()` silently ignored (Java client)

## Summary

Four clock time-setting methods are broken in two distinct ways:

1. **`clock.setFixedTime(String)`, `clock.pauseAt(String)`, `clock.setSystemTime(String)`** — throw `"time is required"` for every string format tried: ISO 8601, date-only, natural language, and Unix milliseconds as a string. The server rejects every value.

2. **`clock.install(ClockOptions.time(String))`** — accepts the call without error but silently ignores the time value; the clock starts at the real system time instead of the specified date.

The remaining clock methods — `install()`, `fastForward()`, `runFor()`, `pauseAt()+resume()`, `setTimezone()` — all work correctly.

## Environment

- Vibium Java client: `com.vibium:vibium:26.3.18`
- Java: 21
- OS: macOS 15.3
- Chrome for Testing: 147.0.7727.56

## Minimal reproduction

### Failure mode 1 — "time is required"

```java
import com.vibium.Vibium;

public class Repro {
    public static void main(String[] args) throws Exception {
        var bro = Vibium.start();
        var page = bro.page();

        page.setContent("<html><body></body></html>");
        page.clock().install();
        page.clock().setFixedTime("2024-06-15T12:00:00Z"); // throws
    }
}
```

**Actual:**
```
com.vibium.errors.VibiumException: time is required
```

### Failure mode 2 — ClockOptions.time() silently ignored

```java
page.setContent("<html><body></body></html>");
page.clock().install(new ClockOptions().time("2024-01-01T00:00:00Z"));
Object year = page.evaluate("new Date().getFullYear()");
System.out.println(year); // 2026 — real system year, not 2024
```

**Expected:** `2024`. **Actual:** current system year (`2026`).

## All string formats tested

Every format a date API might reasonably accept was tried. All fail:

### `setFixedTime(String)` — 6 formats

| Format | Result |
|---|---|
| `"2024-06-15T12:00:00Z"` | `time is required` |
| `"2024-06-15T12:00:00.000Z"` | `time is required` |
| `"2024-06-15"` | `time is required` |
| `"June 15, 2024"` | `time is required` |
| `"01/15/2024"` | `time is required` |
| `"1718445600000"` (Unix ms as string) | `time is required` |

### `pauseAt(String)` — 3 formats

| Format | Result |
|---|---|
| `"2025-01-01T00:00:00Z"` | `time is required` |
| `"January 1, 2025"` | `time is required` |
| `"2025-01-01"` | `time is required` |

### `setSystemTime(String)` — 2 formats

| Format | Result |
|---|---|
| `"2024-12-31T23:59:59Z"` | `time is required` |
| `"December 31, 2024"` | `time is required` |

### `ClockOptions.time(String)` — 2 formats

| Format | Result |
|---|---|
| `"2024-01-01T00:00:00Z"` | year = 2026 (expected 2024) |
| `"January 1, 2024"` | year = 2026 (expected 2024) |

## What works

`clock.install()` (no time), `clock.fastForward(ms)`, `clock.runFor(ms)`, `clock.setTimezone(String)`, and `clock.pauseAt()` + `clock.resume()` all function correctly. The bug is isolated to methods that accept a time value.

## Likely root cause

The server-side implementation of the time-setting commands likely expects a numeric Unix timestamp (milliseconds since epoch as a `long` or `number`) but the Java client serializes the `String` argument directly. The server validates that `time` is present as a number, finds a string instead, and rejects it with `"time is required"`.

For `ClockOptions.time()`, the value is likely sent under the wrong field name or omitted from the serialized options object entirely — hence no error but also no effect.

## Workaround

None currently available for pinning the clock to a specific date via the Java string API. `fastForward()` can advance time relative to the current instant, but absolute time cannot be set.

## Test suite references

- Regression suite skips: [`VibiumJavaApiTests.java#L695`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumJavaApiTests.java#L695)
- Hardening probes (6 + 3 + 2 + 2 formats): [`VibiumBugHardening.java#L483`](https://github.com/lana-20/vibium-java-tests/blob/main/VibiumBugHardening.java#L483)

## Hardening results

Reproduced 13 / 13 probes with 0 unexpected passes.

```
setFixedTime("2024-06-15T12:00:00Z")                          BUG  time is required
setFixedTime("2024-06-15T12:00:00.000Z")                      BUG  time is required
setFixedTime("2024-06-15")                                    BUG  time is required
setFixedTime("June 15, 2024")                                 BUG  time is required
setFixedTime("01/15/2024")                                    BUG  time is required
setFixedTime("1718445600000")                                 BUG  time is required
pauseAt("2025-01-01T00:00:00Z")                               BUG  time is required
pauseAt("January 1, 2025")                                    BUG  time is required
pauseAt("2025-01-01")                                         BUG  time is required
setSystemTime("2024-12-31T23:59:59Z")                         BUG  time is required
setSystemTime("December 31, 2024")                            BUG  time is required
clock.install(ClockOptions.time("2024-01-01T00:00:00Z")) → year BUG  year = 2026 (expected 2024)
clock.install(ClockOptions.time("January 1, 2024")) → year    BUG  year = 2026 (expected 2024)
```
