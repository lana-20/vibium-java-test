import com.vibium.Vibium;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class NetworkDemo {

    public static void main(String[] args) throws Exception {
        // Fetch a random kitten image and encode it as a data URL
        System.out.println("Fetching kitten...");
        byte[] kitten = URI.create("https://cataas.com/cat").toURL().openStream().readAllBytes();
        String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(kitten);
        System.out.println("Kitten fetched (" + kitten.length + " bytes)");

        var bro = Vibium.start();
        var page = bro.page();

        page.onResponse(response -> {
            String url = response.url();
            if (url.matches("(?i).*\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$")) {
                System.out.println("Image response: " + response.status() + " " + url);
            }
        });

        page.go("https://books.toscrape.com");
        System.out.println("Page loaded");

        page.evaluate(
            "var url = '" + dataUrl + "';" +
            "document.querySelectorAll('img').forEach(img => {" +
            "  img.src = url; img.srcset = '';" +
            "});"
        );
        System.out.println("Images injected");

        byte[] png = page.screenshot();
        Files.write(Path.of("injected.png"), png);
        System.out.println("Screenshot saved: injected.png");

        Thread.sleep(4000);
        bro.stop();
    }
}
