import buddy.brain.BuddyBrain;

import java.util.Locale;

public class BuddyUISmokeTest {
    public static void main(String[] args) {
        BuddyBrain brain = new BuddyBrain();

        String dateResult = brain.processCommand("what is the date");
        String timeResult = brain.processCommand("what is the time");

        assertContains(dateResult, "today is", "what is the date");
        assertContains(timeResult, "current time is", "what is the time");

        System.out.println("BUDDY_PROCESS_COMMAND_OK: " + dateResult + " | " + timeResult);
    }

    private static void assertContains(String reply, String expectedPhrase, String originalInput) {
        if (reply == null || reply.trim().isEmpty()) {
            throw new IllegalStateException(
                    "BuddyBrain.processCommand returned an empty response for '" + originalInput + "'.");
        }

        String normalized = reply.toLowerCase(Locale.ROOT);
        if (!normalized.contains(expectedPhrase.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Expected a response containing '" + expectedPhrase + "' for '"
                    + originalInput + "', but got: " + reply);
        }
    }
}
