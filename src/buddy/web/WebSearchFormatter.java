package buddy.web;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebSearchFormatter {

    private static final Pattern NUMBERED_RESULT =
            Pattern.compile(
                    "(?s)(?:^|\\s)(\\d+)\\.\\s+(.*?)(?=\\s+\\d+\\.\\s+|$)");

    private WebSearchFormatter() {
    }

    public static String formatForReading(String results) {

        if (results == null || results.isBlank()) {
            return "";
        }

        String normalized = results.trim().replaceAll("\\s+", " ");
        Matcher matcher = NUMBERED_RESULT.matcher(normalized);
        List<String> formattedResults = new ArrayList<>();

        while (matcher.find()) {
            String details = matcher.group(2).trim()
                    .replaceAll("(?i)\\s+Published:\\s*", "\n   Published: ")
                    .replaceAll("[\\t ]+", " ");

            formattedResults.add(matcher.group(1) + ". " + details);
        }

        if (formattedResults.isEmpty()) {
            return normalized;
        }

        return "Here are the results in a cleaner list:\n\n"
                + String.join("\n\n", formattedResults);
    }
}
