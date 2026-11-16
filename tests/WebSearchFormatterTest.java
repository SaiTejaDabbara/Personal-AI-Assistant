import buddy.web.WebSearchFormatter;

public class WebSearchFormatterTest {

    public static void main(String[] args) {
        String rawResults = "1. First headline\nPublished: 13 Nov 2026, 12:57 AM"
                + " 2. Second headline\nPublished: 14 Nov 2026, 09:30 PM";

        String formatted = WebSearchFormatter.formatForReading(rawResults);
        String expected = "Here are the results in a cleaner list:\n\n"
                + "1. First headline\n Published: 13 Nov 2026, 12:57 AM\n\n"
                + "2. Second headline\n Published: 14 Nov 2026, 09:30 PM";

        if (!expected.equals(formatted)) {
            throw new IllegalStateException(
                    "Expected search results to be separated and readable, but got:\n"
                            + formatted);
        }

        if (!"No results found.".equals(
                WebSearchFormatter.formatForReading("No results found."))) {
            throw new IllegalStateException(
                    "Unnumbered search messages should remain unchanged.");
        }

        System.out.println("WEB_SEARCH_FORMATTER_OK");
    }
}
