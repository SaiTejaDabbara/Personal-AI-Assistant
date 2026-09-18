package buddy.web;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WebSearchManager {

    private static final String LOCAL_SEARXNG_URL =
            "http://127.0.0.1:8080/search";

    private static final String GOOGLE_NEWS_RSS_URL =
            "https://news.google.com/rss/search";

    private static final int LOCAL_CONNECT_TIMEOUT_MS = 400;
    private static final int LOCAL_READ_TIMEOUT_MS = 1200;

    private static final int FALLBACK_CONNECT_TIMEOUT_MS = 2500;
    private static final int FALLBACK_READ_TIMEOUT_MS = 4000;

    private static final int MAX_RESULTS = 8;

    private static final DateTimeFormatter OUTPUT_DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy, hh:mm a",
                    Locale.ENGLISH
            );

    /**
     * Main search entry point.
     */
    public String searchNews(String query) {

        long start = System.currentTimeMillis();

        if (query == null || query.isBlank()) {
            return "I need something to search for.";
        }

        String cleanQuery = normalizeQuery(query);

        if (cleanQuery.isBlank()) {
            return "I need something to search for.";
        }

        boolean todayOnly = isTodayQuery(cleanQuery);

        /*
         * IMPORTANT:
         *
         * For "today" requests, do NOT use SearXNG.
         *
         * SearXNG can return older indexed results and therefore
         * cannot guarantee strict "today only" behavior.
         *
         * Google News RSS gives us publication timestamps that
         * we can explicitly filter.
         */
        if (todayOnly) {

            System.out.println(
                    "Today-only search detected. "
                            + "Using Google News RSS directly."
            );

            String result = tryGoogleNewsRss(
                    cleanQuery,
                    true
            );

            System.out.println(
                    "Web search time: "
                            + (System.currentTimeMillis() - start)
                            + " ms"
            );

            return result;
        }

        /*
         * Normal search:
         *
         * Try local SearXNG first because it is faster when available.
         */
        String localResult = tryLocalSearxng(cleanQuery);

        if (localResult != null && !localResult.isBlank()) {

            System.out.println(
                    "Using local SearXNG results."
            );

            System.out.println(
                    "Web search time: "
                            + (System.currentTimeMillis() - start)
                            + " ms"
            );

            return localResult;
        }

        /*
         * SearXNG unavailable.
         * Fall back to Google News RSS.
         */
        String googleResult = tryGoogleNewsRss(
                cleanQuery,
                false
        );

        System.out.println(
                "Web search time: "
                        + (System.currentTimeMillis() - start)
                        + " ms"
        );

        return googleResult;
    }

    // ============================================================
    // LOCAL SEARXNG
    // ============================================================

    private String tryLocalSearxng(String query) {

        long start = System.currentTimeMillis();

        try {

            System.out.println(
                    "Trying local SearXNG: "
                            + LOCAL_SEARXNG_URL
            );

            String encodedQuery =
                    URLEncoder.encode(
                            query,
                            StandardCharsets.UTF_8
                    );

            String url =
                    LOCAL_SEARXNG_URL
                            + "?q="
                            + encodedQuery
                            + "&format=json";

            System.out.println(
                    "SearXNG request: " + url
            );

            HttpURLConnection connection =
                    (HttpURLConnection)
                            URI.create(url)
                                    .toURL()
                                    .openConnection();

            connection.setRequestMethod("GET");

            connection.setConnectTimeout(
                    LOCAL_CONNECT_TIMEOUT_MS
            );

            connection.setReadTimeout(
                    LOCAL_READ_TIMEOUT_MS
            );

            connection.setRequestProperty(
                    "User-Agent",
                    "Buddy-AI/1.0"
            );

            int status =
                    connection.getResponseCode();

            long httpTime =
                    System.currentTimeMillis() - start;

            System.out.println(
                    "SearXNG HTTP time: "
                            + httpTime
                            + " ms"
            );

            System.out.println(
                    "SearXNG HTTP status: "
                            + status
            );

            if (status != HttpURLConnection.HTTP_OK) {

                connection.disconnect();

                System.out.println(
                        "Local SearXNG unavailable."
                );

                return null;
            }

            String body =
                    readResponse(
                            connection.getInputStream()
                    );

            connection.disconnect();

            if (body == null || body.isBlank()) {

                System.out.println(
                        "SearXNG returned empty results."
                );

                return null;
            }

            String parsed =
                    parseSearchResults(body);

            if (parsed == null || parsed.isBlank()) {

                System.out.println(
                        "SearXNG returned no usable results."
                );

                return null;
            }

            return parsed;

        } catch (Exception e) {

            long elapsed =
                    System.currentTimeMillis() - start;

            System.out.println(
                    "Local SearXNG unavailable after "
                            + elapsed
                            + " ms: "
                            + e.getClass().getSimpleName()
            );

            System.out.println(
                    "Local SearXNG unavailable."
            );

            return null;
        }
    }

    // ============================================================
    // GOOGLE NEWS RSS
    // ============================================================

    private String tryGoogleNewsRss(
            String query,
            boolean todayOnly
    ) {

        long start = System.currentTimeMillis();

        try {

            System.out.println(
                    "Trying Google News RSS fallback..."
            );

            String encodedQuery =
                    URLEncoder.encode(
                            query,
                            StandardCharsets.UTF_8
                    );

            String url =
                    GOOGLE_NEWS_RSS_URL
                            + "?q="
                            + encodedQuery
                            + "&hl=en-US"
                            + "&gl=US"
                            + "&ceid=US:en";

            System.out.println(
                    "Google News RSS request: "
                            + url
            );

            HttpURLConnection connection =
                    (HttpURLConnection)
                            URI.create(url)
                                    .toURL()
                                    .openConnection();

            connection.setRequestMethod("GET");

            connection.setConnectTimeout(
                    FALLBACK_CONNECT_TIMEOUT_MS
            );

            connection.setReadTimeout(
                    FALLBACK_READ_TIMEOUT_MS
            );

            connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 Buddy-AI/1.0"
            );

            int status =
                    connection.getResponseCode();

            long httpTime =
                    System.currentTimeMillis() - start;

            System.out.println(
                    "Google News HTTP time: "
                            + httpTime
                            + " ms"
            );

            System.out.println(
                    "Google News HTTP status: "
                            + status
            );

            if (status != HttpURLConnection.HTTP_OK) {

                connection.disconnect();

                return "I could not retrieve information from the web right now.";
            }

            String body =
                    readResponse(
                            connection.getInputStream()
                    );

            connection.disconnect();

            if (body == null || body.isBlank()) {

                return "I could not retrieve information from the web right now.";
            }

            String parsed =
                    parseGoogleNewsRss(
                            body,
                            todayOnly
                    );

            if (parsed == null || parsed.isBlank()) {

                if (todayOnly) {

                    return "I could not find any news published today for that topic.";

                }

                return "I could not find useful information from the web.";
            }

            return parsed;

        } catch (Exception e) {

            System.out.println(
                    "Google News RSS failed: "
                            + e.getClass().getSimpleName()
                            + ": "
                            + e.getMessage()
            );

            return "I could not retrieve information from the web right now.";
        }
    }

    // ============================================================
    // GOOGLE NEWS RSS PARSER
    // ============================================================

    private String parseGoogleNewsRss(
            String xml,
            boolean todayOnly
    ) {

        List<String> results =
                new ArrayList<>();

        try {

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            /*
             * Basic XML hardening.
             */
            try {
                factory.setFeature(
                        "http://apache.org/xml/features/disallow-doctype-decl",
                        true
                );
            } catch (Exception ignored) {
            }

            try {
                factory.setFeature(
                        "http://xml.org/sax/features/external-general-entities",
                        false
                );
            } catch (Exception ignored) {
            }

            try {
                factory.setFeature(
                        "http://xml.org/sax/features/external-parameter-entities",
                        false
                );
            } catch (Exception ignored) {
            }

            factory.setExpandEntityReferences(false);

            Document document =
                    factory.newDocumentBuilder()
                            .parse(
                                    new ByteArrayInputStream(
                                            xml.getBytes(
                                                    StandardCharsets.UTF_8
                                            )
                                    )
                            );

            NodeList items =
                    document.getElementsByTagName("item");

            LocalDate today =
                    LocalDate.now(
                            ZoneId.systemDefault()
                    );

            for (int i = 0;
                 i < items.getLength()
                         && results.size() < MAX_RESULTS;
                 i++) {

                Node node =
                        items.item(i);

                if (!(node instanceof Element)) {
                    continue;
                }

                Element item =
                        (Element) node;

                String title =
                        getElementText(
                                item,
                                "title"
                        );

                String published =
                        getElementText(
                                item,
                                "pubDate"
                        );

                if (title == null
                        || title.isBlank()) {

                    continue;
                }

                ZonedDateTime publishedTime =
                        parseRssDate(published);

                /*
                 * Strict TODAY behavior:
                 *
                 * If user explicitly requested "today",
                 * an article MUST have a valid publication
                 * date and that date MUST equal today's
                 * local date.
                 */
                if (todayOnly) {

                    if (publishedTime == null) {
                        continue;
                    }

                    LocalDate articleDate =
                            publishedTime
                                    .withZoneSameInstant(
                                            ZoneId.systemDefault()
                                    )
                                    .toLocalDate();

                    if (!articleDate.equals(today)) {
                        continue;
                    }
                }

                String formattedDate;

                if (publishedTime != null) {

                    formattedDate =
                            publishedTime
                                    .withZoneSameInstant(
                                            ZoneId.systemDefault()
                                    )
                                    .format(
                                            OUTPUT_DATE_FORMAT
                                    );

                } else {

                    formattedDate =
                            published != null
                                    ? published
                                    : "Unknown date";
                }

                /*
                 * Intentionally DO NOT output:
                 *
                 * link
                 * URL
                 * source URL
                 *
                 * User asked for date/time instead.
                 */
                StringBuilder result =
                        new StringBuilder();

                result.append(
                        results.size() + 1
                );

                result.append(". ");

                result.append(
                        cleanText(title)
                );

                result.append(
                        "\nPublished: "
                );

                result.append(
                        formattedDate
                );

                results.add(
                        result.toString()
                );
            }

            if (results.isEmpty()) {
                return "";
            }

            return String.join(
                    "\n\n",
                    results
            );

        } catch (Exception e) {

            System.out.println(
                    "Google News RSS parsing failed: "
                            + e.getMessage()
            );

            return "";
        }
    }

    // ============================================================
    // SEARXNG JSON PARSER
    // ============================================================

    private String parseSearchResults(
            String json
    ) {

        if (json == null || json.isBlank()) {
            return "";
        }

        List<String> results =
                new ArrayList<>();

        /*
         * This parser intentionally remains dependency-free.
         *
         * It extracts:
         * title
         * content
         * publishedDate
         *
         * URLs are intentionally omitted from the
         * user-facing output.
         */

        Pattern resultPattern =
                Pattern.compile(
                        "\\{(.*?)\\}",
                        Pattern.DOTALL
                );

        Matcher matcher =
                resultPattern.matcher(json);

        while (matcher.find()
                && results.size() < MAX_RESULTS) {

            String object =
                    matcher.group(1);

            String title =
                    extractJsonValue(
                            object,
                            "title"
                    );

            String content =
                    extractJsonValue(
                            object,
                            "content"
                    );

            String publishedDate =
                    extractJsonValue(
                            object,
                            "publishedDate"
                    );

            if ((title == null || title.isBlank())
                    && (content == null || content.isBlank())) {

                continue;
            }

            StringBuilder result =
                    new StringBuilder();

            result.append(
                    results.size() + 1
            );

            result.append(". ");

            if (title != null
                    && !title.isBlank()) {

                result.append(
                        cleanText(title)
                );
            } else {

                result.append(
                        cleanText(content)
                );
            }

            if (publishedDate != null
                    && !publishedDate.isBlank()) {

                result.append(
                        "\nPublished: "
                );

                result.append(
                        cleanText(publishedDate)
                );
            }

            /*
             * NO URL.
             */

            results.add(
                    result.toString()
            );
        }

        return String.join(
                "\n\n",
                results
        );
    }

    // ============================================================
    // QUERY NORMALIZATION
    // ============================================================

    private String normalizeQuery(
            String query
    ) {

        String clean =
                query.trim();

        clean =
                clean.replaceFirst(
                        "(?i)^search\\s+for\\s+",
                        ""
                );

        clean =
                clean.replaceFirst(
                        "(?i)^search\\s+",
                        ""
                );

        clean =
                clean.replaceFirst(
                        "(?i)^look\\s+up\\s+",
                        ""
                );

        clean =
                clean.replaceFirst(
                        "(?i)^find\\s+",
                        ""
                );

        clean =
                clean.replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        /*
         * If the user explicitly mentions TechCrunch,
         * focus Google News on TechCrunch.
         *
         * Example:
         *
         * today news in techcrunch
         *
         * becomes:
         *
         * site:techcrunch.com today news
         */
        if (containsTechCrunch(clean)) {

            clean =
                    clean.replaceAll(
                            "(?i)\\bin\\s+techcrunch\\b",
                            " "
                    );

            clean =
                    clean.replaceAll(
                            "(?i)\\bon\\s+techcrunch\\b",
                            " "
                    );

            clean =
                    clean.replaceAll(
                            "(?i)\\btechcrunch\\b",
                            " "
                    );

            clean =
                    clean.replaceAll(
                                    "\\s+",
                                    " "
                            )
                            .trim();

            clean =
                    "site:techcrunch.com "
                            + clean;
        }

        return clean.trim();
    }

    private boolean containsTechCrunch(
            String query
    ) {

        return query != null
                && query.matches(
                "(?is).*\\btechcrunch\\b.*"
        );
    }

    // ============================================================
    // TODAY DETECTION
    // ============================================================

    private boolean isTodayQuery(
            String query
    ) {

        if (query == null) {
            return false;
        }

        return query.matches(
                "(?is).*\\b(today|today's|todays)\\b.*"
        );
    }

    // ============================================================
    // RSS DATE PARSING
    // ============================================================

    private ZonedDateTime parseRssDate(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        String clean =
                value.trim();

        /*
         * Google News normally uses RFC-1123:
         *
         * Wed, 10 Sep 2026 05:30:00 GMT
         *
         * ZonedDateTime.parse can handle many RFC-1123
         * formats directly.
         */
        try {

            return ZonedDateTime.parse(
                    clean,
                    DateTimeFormatter.RFC_1123_DATE_TIME
            );

        } catch (DateTimeParseException ignored) {
        }

        /*
         * Some feeds may use ISO-8601.
         */
        try {

            return ZonedDateTime.parse(
                    clean
            );

        } catch (DateTimeParseException ignored) {
        }

        /*
         * Try Instant.
         */
        try {

            return ZonedDateTime.ofInstant(
                    Instant.parse(clean),
                    ZoneId.systemDefault()
            );

        } catch (DateTimeParseException ignored) {
        }

        return null;
    }

    // ============================================================
    // XML HELPERS
    // ============================================================

    private String getElementText(
            Element parent,
            String tagName
    ) {

        NodeList nodes =
                parent.getElementsByTagName(
                        tagName
                );

        if (nodes.getLength() == 0) {
            return "";
        }

        Node node =
                nodes.item(0);

        if (node == null) {
            return "";
        }

        String text =
                node.getTextContent();

        return text == null
                ? ""
                : text.trim();
    }

    // ============================================================
    // JSON HELPERS
    // ============================================================

    private String extractJsonValue(
            String jsonObject,
            String key
    ) {

        if (jsonObject == null
                || key == null) {

            return "";
        }

        String pattern =
                "\"" + Pattern.quote(key)
                        + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"";

        Matcher matcher =
                Pattern.compile(
                        pattern,
                        Pattern.DOTALL
                ).matcher(jsonObject);

        if (!matcher.find()) {
            return "";
        }

        return unescapeJson(
                matcher.group(1)
        );
    }

    private String unescapeJson(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ");
    }

    // ============================================================
    // TEXT CLEANUP
    // ============================================================

    private String cleanText(
            String text
    ) {

        if (text == null) {
            return "";
        }

        String clean =
                text
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        return clean;
    }

    // ============================================================
    // HTTP RESPONSE
    // ============================================================

    private String readResponse(
            InputStream inputStream
    ) throws Exception {

        if (inputStream == null) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        inputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {

                result.append(line);
                result.append('\n');
            }
        }

        return result.toString();
    }
}