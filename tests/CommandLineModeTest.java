import java.util.Locale;

public class CommandLineModeTest {
    public static void main(String[] args) {
        String reply = buddy.Main.runCommandOnce("what is the date");

        if (reply == null || reply.trim().isEmpty()) {
            throw new IllegalStateException("runCommandOnce returned an empty response.");
        }

        String normalized = reply.toLowerCase(Locale.ROOT);
        if (!normalized.contains("today is")) {
            throw new IllegalStateException("Expected a date response but got: " + reply);
        }

        System.out.println("COMMAND_MODE_OK: " + reply);
    }
}
