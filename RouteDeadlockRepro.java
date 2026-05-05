import com.vibium.Vibium;
import com.vibium.types.FulfillOptions;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RouteDeadlockRepro {

    static final List<String> SITES = List.of(
        "https://example.com",
        "https://wikipedia.org",
        "https://books.toscrape.com",
        "https://httpbin.org"
    );

    public static void main(String[] args) throws Exception {
        int passed = 0;

        for (String site : SITES) {
            System.out.printf("%-35s ", site);
            var result = reproduce(site);
            System.out.println(result);
            if (result.startsWith("DEADLOCK")) passed++;
        }

        System.out.println("\n" + passed + "/" + SITES.size() + " sites confirmed deadlock");
    }

    static String reproduce(String url) throws Exception {
        var bro = Vibium.start();
        var page = bro.page();

        page.route("**/*.jpg", route ->
            new Thread(() -> route.fulfill(new FulfillOptions()
                .status(200).contentType("image/svg+xml").body("<svg/>"))).start()
        );

        var navigated = new AtomicBoolean(false);
        var error = new AtomicBoolean(false);

        Thread nav = new Thread(() -> {
            try {
                page.go(url);
                navigated.set(true);
            } catch (Exception e) {
                error.set(true);
            }
        });
        nav.setDaemon(true);
        nav.start();

        // Give it 5 seconds — plenty for a real navigation, fatal for a deadlock
        nav.join(5000);

        String result;
        if (navigated.get()) {
            result = "OK (no deadlock — unexpected)";
        } else if (error.get()) {
            result = "ERROR (threw before timeout)";
        } else {
            result = "DEADLOCK confirmed (still blocked after 5s)";
        }

        // Clean up without waiting for nav thread
        try { bro.stop(); } catch (Exception ignored) {}
        return result;
    }
}
