import com.vibium.*;
import com.vibium.types.*;

public class DragDropInspect {
    public static void main(String[] args) throws Exception {
        var bro = Vibium.start(new StartOptions().headless(true));
        var page = bro.page();
        try {
            // demoqa drop target
            System.out.println("=== demoqa.com/droppable ===");
            page.go("https://demoqa.com/droppable");
            Thread.sleep(1000);
            Object demoqa = page.evaluate("""
                JSON.stringify(
                  Array.from(document.querySelectorAll('[id]'))
                    .filter(e => e.id && (e.className.includes('drop') || e.className.includes('Drop') || e.id.includes('drop') || e.id.includes('Drop')))
                    .map(e => ({ id: e.id, cls: e.className.slice(0,60), text: e.textContent.trim().slice(0,30) }))
                )
            """);
            System.out.println(demoqa);

            // w3schools drop zones
            System.out.println("\n=== w3schools drag-drop ===");
            page.go("https://www.w3schools.com/html/html5_draganddrop.asp");
            Thread.sleep(1000);
            Object w3 = page.evaluate("""
                JSON.stringify(
                  Array.from(document.querySelectorAll('[ondrop],[ondragover]'))
                    .map(e => ({ tag: e.tagName, id: e.id, cls: e.className.slice(0,40) }))
                )
            """);
            System.out.println(w3);

            // testtrack drop zones
            System.out.println("\n=== testtrack drop zones ===");
            page.go("https://testtrack.org/drag-drop-demo");
            Thread.sleep(1000);
            Object tt = page.evaluate("""
                JSON.stringify(
                  Array.from(document.querySelectorAll('[id]'))
                    .filter(e => e.id && !e.id.startsWith('draggable'))
                    .filter(e => {
                      var s = window.getComputedStyle(e);
                      return e.className.includes('drop') || e.className.includes('zone') || e.className.includes('target') || e.className.includes('border');
                    })
                    .slice(0,6)
                    .map(e => ({ id: e.id, cls: e.className.slice(0,60) }))
                )
            """);
            System.out.println(tt);
        } finally {
            bro.stop();
        }
    }
}
