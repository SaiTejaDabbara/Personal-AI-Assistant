package buddy.web;

import java.time.ZonedDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class WebSearchManagerTest {

    public static void main(String[] args) {
        ZonedDateTime now =
                ZonedDateTime.parse("2026-11-16T21:54:00+05:30[Asia/Kolkata]");

        String normalized =
                new WebSearchManager().normalizeQuery(
                        "can u search for latest news on techcrunch");
        if (!"site:techcrunch.com latest news".equals(normalized)) {
            throw new IllegalStateException(
                    "Conversational prefixes should not pollute the search query: "
                            + normalized);
        }

        assertTodayWindowQuery("latest news on TechCrunch", true);
        assertTodayWindowQuery("today's headlines", true);
        assertTodayWindowQuery("Java tutorial", false);

        assertPublishedDuringToday(
                ZonedDateTime.parse("2026-11-15T18:30:00Z"),
                now,
                true,
                "local midnight"
        );
        assertPublishedDuringToday(
                ZonedDateTime.parse("2026-11-15T18:29:59Z"),
                now,
                false,
                "one second before local midnight"
        );
        assertPublishedDuringToday(
                now.plusSeconds(1),
                now,
                false,
                "future publication time"
        );
        assertPublishedDuringToday(null, now, false, "missing publication time");
        assertNewsSortedByPublicationTime();

        System.out.println("WEB_SEARCH_MANAGER_OK");
    }

    private static void assertNewsSortedByPublicationTime() {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime startOfToday =
                LocalDate.now(zone).atStartOfDay(zone);
        String xml = "<rss><channel>"
                + item("Oldest", startOfToday.plusHours(1))
                + item("Newest", startOfToday.plusHours(3))
                + item("Middle", startOfToday.plusHours(2))
                + "</channel></rss>";

        String results =
                new WebSearchManager().parseGoogleNewsRss(xml, false);
        int newestIndex = results.indexOf("1. Newest");
        int middleIndex = results.indexOf("2. Middle");
        int oldestIndex = results.indexOf("3. Oldest");

        if (newestIndex < 0
                || middleIndex <= newestIndex
                || oldestIndex <= middleIndex) {
            throw new IllegalStateException(
                    "News items should be ordered newest to oldest by publication time:\n"
                            + results);
        }
    }

    private static String item(String title, ZonedDateTime published) {
        return "<item><title>"
                + title
                + "</title><pubDate>"
                + DateTimeFormatter.RFC_1123_DATE_TIME.format(published)
                + "</pubDate></item>";
    }

    private static void assertTodayWindowQuery(String query, boolean expected) {
        if (WebSearchManager.shouldUseTodayWindow(query) != expected) {
            throw new IllegalStateException(
                    "Unexpected today-window classification for: " + query);
        }
    }

    private static void assertPublishedDuringToday(
            ZonedDateTime published,
            ZonedDateTime now,
            boolean expected,
            String description
    ) {
        if (WebSearchManager.isPublishedDuringToday(published, now) != expected) {
            throw new IllegalStateException(
                    "Unexpected today-window result for " + description);
        }
    }
}
