import com.vibium.*;
import com.vibium.types.*;

public class B3Repro {
    public static void main(String[] args) throws Exception {
        Browser bro = Vibium.start(new StartOptions().headless(true));
        Page page = bro.page();
        try {
            page.setContent("<html><body></body></html>");

            System.out.println("=== bare: true ===");
            try {
                page.waitForFunction("true", new WaitOptions().timeout(3000));
                System.out.println("PASS (bare expression works)");
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
            }

            System.out.println("=== bare: document.readyState === 'complete' ===");
            try {
                page.waitForFunction("document.readyState === 'complete'", new WaitOptions().timeout(3000));
                System.out.println("PASS");
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
            }

            System.out.println("=== lambda: () => true ===");
            try {
                page.waitForFunction("() => true", new WaitOptions().timeout(3000));
                System.out.println("PASS (lambda works)");
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
            }

            System.out.println("=== lambda: () => document.readyState === 'complete' ===");
            try {
                page.waitForFunction("() => document.readyState === 'complete'", new WaitOptions().timeout(3000));
                System.out.println("PASS");
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
            }

        } finally {
            bro.stop();
        }
    }
}
