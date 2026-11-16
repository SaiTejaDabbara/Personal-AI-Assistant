package buddy.web;

public class WebServerJsonEscapeTest {

    public static void main(String[] args) {
        String input = "first line\nsecond line\r\nthird line\t\"quoted\"";
        String expected =
                "first line\\nsecond line\\r\\nthird line\\t\\\"quoted\\\"";
        String escaped = BuddyWebServer.jsonEscape(input);

        if (!expected.equals(escaped)) {
            throw new IllegalStateException(
                    "Expected JSON escaping to preserve whitespace, but got: "
                            + escaped);
        }

        System.out.println("WEB_SERVER_JSON_ESCAPE_OK");
    }
}
