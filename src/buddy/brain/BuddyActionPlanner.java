package buddy.brain;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuddyActionPlanner {

    private static final String OLLAMA_URL =
            "http://localhost:11434/api/chat";

    private static final String MODEL =
            "qwen2.5:0.5b";

    private final HttpClient httpClient;

    public BuddyActionPlanner() {

        httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(10)
                        )
                        .build();
    }

    // ============================================================
    // PLAN
    // ============================================================

    public List<BuddyActionPlan> plan(
            String userMessage,
            ConversationContext context
    ) {

        List<BuddyActionPlan> plans =
                new ArrayList<>();

        try {

            String currentMessage =
                    userMessage == null
                            ? ""
                            : userMessage.trim();

            if (currentMessage.isBlank()) {
                return plans;
            }

            // Deterministic external-information route. Explicit latest/current/news
            // requests do not need the 0.5B model to choose web_search.
            if (isDirectWebSearchRequest(currentMessage)) {
                plans.add(
                        new BuddyActionPlan(
                                "web_search",
                                currentMessage,
                                "none",
                                "none",
                                "",
                                "",
                                false,
                                ""
                        )
                );

                System.out.println(
                        "Planner bypass: deterministic web_search"
                );

                return plans;
            }

            String contextText =
                    buildCompactContext(context);

            /*
             * Ollama is responsible for understanding the user's
             * meaning and choosing the intent.
             *
             * Java is responsible for:
             * - parsing
             * - validation
             * - safety
             * - execution
             *
             * Previous conversation is only supporting context.
             */

            String systemPrompt =
                    """
                    You are Buddy's action planner.

                    Read the CURRENT USER REQUEST and return ONLY valid JSON.

                    SINGLE ACTION:
                    {"intent":"","target":"","targetType":""}

                    MULTIPLE ACTIONS:
                    {"actions":[
                      {"intent":"","target":"","targetType":""},
                      {"intent":"","target":"","targetType":""}
                    ]}

                    Use multiple actions only when the user clearly requested
                    multiple executable operations. Preserve their order.

                    Allowed intents:
                    chat, open, close, create, locate, search, web_search,
                    download, move, copy, rename, play, delete, time, date, unknown.

                    Use chat for normal conversation.
                    Use open for local files, folders, apps, or websites.
                    Use close for local applications.
                    Use create for local files or folders.
                    Use locate for local item locations.
                    Use search for local Windows/files.
                    Use web_search for internet, external, current, latest, recent,
                    news, or factual information.
                    Use download for explicit downloads or URLs.
                    Use move/copy/rename/delete for local file operations.
                    Use play for media.
                    Use time for the current time.
                    Use date for the current date/day.
                    Use unknown only when the request cannot reasonably be understood.

                    TARGET:
                    Put the main subject of the CURRENT USER REQUEST in target.
                    Preserve the user's meaning. Do not invent objects, paths, URLs,
                    applications, or facts.

                    CONTEXT:
                    Previous context is supporting information only. Use it only
                    to resolve references such as it, that, this, them, there,
                    that file, or this folder. The CURRENT USER REQUEST wins.

                    Examples:
                    User: tell me a joke
                    {"intent":"chat","target":"tell me a joke"}

                    User: What is the Java Virtual Machine?
                    {"intent":"web_search","target":"What is the Java Virtual Machine?"}

                    User: open Spotify
                    {"intent":"open","target":"Spotify","targetType":"application"}

                    User: delete Test
                    {"intent":"delete","target":"Test"}

                    User: open Notepad and open Calculator
                    {"actions":[
                      {"intent":"open","target":"Notepad","targetType":"application"},
                      {"intent":"open","target":"Calculator","targetType":"application"}
                    ]}

                    Return JSON only. No explanation.
                    """;

            String userPrompt =
                    "CONTEXT:\n"
                            + (
                            contextText.isBlank()
                                    ? "none"
                                    : contextText
                    )
                            + "\n\nCURRENT USER REQUEST:\n"
                            + currentMessage;

            String json =
                    "{"
                            + "\"model\":\""
                            + MODEL
                            + "\","
                            + "\"messages\":["
                            + "{\"role\":\"system\",\"content\":\""
                            + escapeJson(systemPrompt)
                            + "\"},"
                            + "{\"role\":\"user\",\"content\":\""
                            + escapeJson(userPrompt)
                            + "\"}"
                            + "],"
                            + "\"stream\":false,"
                            + "\"format\":\"json\","
                            + "\"keep_alive\":\"60m\","
                            + "\"options\":{"
                            + "\"temperature\":0,"
                            + "\"num_ctx\":512,"
                            + "\"num_predict\":32"
                            + "}"
                            + "}";

            long plannerStart =
                    System.currentTimeMillis();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            OLLAMA_URL
                                    )
                            )
                            .timeout(
                                    Duration.ofSeconds(30)
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            json
                                    )
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            System.out.println(
                    "Ollama planner HTTP time: "
                            + (
                            System.currentTimeMillis()
                                    - plannerStart
                    )
                            + " ms"
            );

            if (response.statusCode() != 200) {

                System.out.println(
                        "Planner HTTP error: "
                                + response.statusCode()
                );

                return fallbackChat(currentMessage);
            }

            String aiResponse =
                    extractJsonString(
                            response.body(),
                            "content"
                    );

            if (
                    aiResponse == null
                            || aiResponse.isBlank()
            ) {

                System.out.println(
                        "Planner returned an empty response."
                );

                return fallbackChat(currentMessage);
            }

            System.out.println(
                    "Planner: "
                            + aiResponse
            );

            List<BuddyActionPlan> parsedPlans =
                    parseActions(
                            aiResponse
                    );

            return sanitizePlans(
                    parsedPlans,
                    currentMessage
            );

        } catch (Exception e) {

            System.out.println(
                    "Planner error: "
                            + e.getMessage()
            );

            return fallbackChat(
                    userMessage == null
                            ? ""
                            : userMessage.trim()
            );
        }
    }

    // ============================================================
    // FALLBACK
    // ============================================================

    private List<BuddyActionPlan> fallbackChat(
            String userMessage
    ) {

        List<BuddyActionPlan> plans =
                new ArrayList<>();

        if (
                userMessage == null
                        || userMessage.isBlank()
        ) {
            return plans;
        }

        plans.add(
                new BuddyActionPlan(
                        "chat",
                        userMessage,
                        "none",
                        "none",
                        "",
                        "",
                        false,
                        ""
                )
        );

        return plans;
    }

    // ============================================================
    // PLAN SAFETY / VALIDATION
    // ============================================================

    private List<BuddyActionPlan> sanitizePlans(
            List<BuddyActionPlan> plans,
            String userMessage
    ) {

        List<BuddyActionPlan> safePlans =
                new ArrayList<>();

        if (plans == null) {
            return safePlans;
        }

        for (BuddyActionPlan plan : plans) {

            if (plan == null) {
                continue;
            }

            String intent =
                    safeValue(
                            plan.getIntent()
                    ).toLowerCase();

            String target =
                    safeValue(
                            plan.getTarget()
                    );

            boolean targetRequired =
                    intent.equals("open")
                            || intent.equals("close")
                            || intent.equals("create")
                            || intent.equals("locate")
                            || intent.equals("download")
                            || intent.equals("move")
                            || intent.equals("copy")
                            || intent.equals("rename")
                            || intent.equals("play")
                            || intent.equals("delete");

            if (
                    targetRequired
                            && target.isBlank()
            ) {
                continue;
            }

            /*
             * The planner now only needs to decide intent + target.
             *
             * Java supplies safe defaults for the remaining fields.
             */

            String targetType =
                    safeValue(
                            plan.getTargetType()
                    );

            String location =
                    normalizeLocation(
                            plan.getLocation()
                    );

            // ------------------------------------------------------------
            // Deterministic repair for common folder-creation phrasing.
            // The 0.5B planner can confuse the destination (for example,
            // "desktop") with targetType. Java repairs the shape from the
            // original user request before execution.
            // ------------------------------------------------------------
            if (intent.equals("create")
                    && userMessage != null
                    && !userMessage.isBlank()
                    && userMessage.toLowerCase(Locale.ROOT)
                    .matches(".*\\bfolder\\b.*")) {

                Pattern folderPattern = Pattern.compile(
                        "(?i)^.*?(?:create|make|new)\\s+(?:a\\s+)?folder\\s+(?:called\\s+|named\\s+)?(.+?)(?:\\s+on\\s+(?:my\\s+)?(desktop|downloads|documents|pictures|music|videos))?\\s*$"
                );

                Matcher folderMatcher =
                        folderPattern.matcher(userMessage.trim());

                if (folderMatcher.matches()) {
                    String repairedTarget =
                            folderMatcher.group(1) == null
                                    ? ""
                                    : folderMatcher.group(1).trim();

                    String repairedLocation =
                            folderMatcher.group(2) == null
                                    ? "none"
                                    : folderMatcher.group(2).trim().toLowerCase(Locale.ROOT);

                    repairedTarget = repairedTarget.replaceFirst(
                            "(?i)\\s+on\\s+(?:my\\s+)?(?:desktop|downloads|documents|pictures|music|videos)\\s*$",
                            ""
                    ).trim();

                    if (!repairedTarget.isBlank()) {
                        target = repairedTarget;
                        targetType = "folder";
                        if (!repairedLocation.equals("none")) {
                            location = repairedLocation;
                        }
                    }
                }
            }

            String destination =
                    normalizeDestination(
                            plan.getDestination(),
                            intent
                    );

            String source =
                    safeValue(
                            plan.getSource()
                    );

            if (
                    !source.startsWith("http://")
                            && !source.startsWith("https://")
            ) {
                source = "";
            }

            boolean requiresConfirmation =
                    intent.equals("delete")
                            || intent.equals("move")
                            || intent.equals("copy")
                            || intent.equals("rename");

            String message =
                    intent.equals("chat")
                            ? safeValue(
                            plan.getMessage()
                    )
                            : "";

            safePlans.add(
                    new BuddyActionPlan(
                            intent,
                            target,
                            targetType,
                            location,
                            destination,
                            source,
                            requiresConfirmation,
                            message
                    )
            );
        }

        if (safePlans.isEmpty()) {

            return fallbackChat(
                    userMessage
            );
        }

        return safePlans;
    }

    // ============================================================
    // COMPACT CONTEXT
    // ============================================================

    private String buildCompactContext(
            ConversationContext context
    ) {

        if (context == null) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        addContext(
                result,
                "lastAction",
                context.getLastAction()
        );

        addContext(
                result,
                "lastObject",
                context.getLastObject()
        );

        addContext(
                result,
                "lastFileName",
                context.getLastFileName()
        );

        addContext(
                result,
                "lastFilePath",
                context.getLastFilePath()
        );

        addContext(
                result,
                "lastFolderName",
                context.getLastFolderName()
        );

        addContext(
                result,
                "lastLocation",
                context.getLastLocation()
        );

        if (context.isWaitingForConfirmation()) {

            addContext(
                    result,
                    "pendingAction",
                    context.getPendingAction()
            );

            addContext(
                    result,
                    "pendingDetails",
                    context.getPendingDetails()
            );
        }

        return result.toString().trim();
    }

    private void addContext(
            StringBuilder result,
            String key,
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return;
        }

        if (result.length() > 0) {
            result.append("\n");
        }

        result
                .append(key)
                .append("=")
                .append(value.trim());
    }

    // ============================================================
    // PARSE ACTIONS
    // ============================================================

    private List<BuddyActionPlan> parseActions(
            String json
    ) {

        List<BuddyActionPlan> plans =
                new ArrayList<>();

        if (
                json == null
                        || json.isBlank()
        ) {
            return plans;
        }

        /*
         * New planner format:
         *
         * {"intent":"chat","target":"tell me a joke"}
         *
         * Also accept the old:
         *
         * {"actions":[{...}]}
         *
         * so the planner remains tolerant during transition.
         */

        if (json.contains("\"actions\"")) {

            return parseActionArray(
                    json
            );
        }

        String intent =
                safe(
                        extractJsonString(
                                json,
                                "intent"
                        )
                );

        String target =
                safe(
                        extractJsonString(
                                json,
                                "target"
                        )
                );

        String targetType =
                safe(
                        extractJsonString(
                                json,
                                "targetType"
                        )
                );

        if (
                intent.isBlank()
                        && target.isBlank()
        ) {
            return plans;
        }

        // Repair the common small-model shape:
        // {"intent":"find","target":"OpenAI","targetType":"latest"}
        if (
                intent.equalsIgnoreCase("find")
                        && isWebSearchTargetType(targetType)
        ) {
            intent = "web_search";
        }

        intent =
                normalizeIntent(
                        intent
                );

        if (targetType.isBlank()) {
            targetType =
                    inferTargetType(
                            intent
                    );
        } else {
            targetType =
                    normalizeTargetType(
                            targetType
                    );
        }

        boolean requiresConfirmation =
                intent.equals("delete")
                        || intent.equals("move")
                        || intent.equals("copy")
                        || intent.equals("rename");

        String location =
                inferLocation(
                        target
                );

        String source =
                "";

        String destination =
                "";

        plans.add(
                new BuddyActionPlan(
                        intent,
                        target,
                        targetType,
                        location,
                        destination,
                        source,
                        requiresConfirmation,
                        ""
                )
        );

        return plans;
    }

    private List<BuddyActionPlan> parseActionArray(
            String json
    ) {

        List<BuddyActionPlan> plans =
                new ArrayList<>();

        int actionsKey =
                json.indexOf("\"actions\"");

        if (actionsKey < 0) {
            return plans;
        }

        int arrayStart =
                json.indexOf(
                        '[',
                        actionsKey
                );

        if (arrayStart < 0) {
            return plans;
        }

        int arrayEnd =
                findMatchingBracket(
                        json,
                        arrayStart,
                        '[',
                        ']'
                );

        if (arrayEnd < 0) {
            return plans;
        }

        String actionsJson =
                json.substring(
                        arrayStart + 1,
                        arrayEnd
                );

        int index = 0;

        while (
                index < actionsJson.length()
        ) {

            while (
                    index < actionsJson.length()
                            && (
                            Character.isWhitespace(
                                    actionsJson.charAt(index)
                            )
                                    || actionsJson.charAt(index) == ','
                    )
            ) {
                index++;
            }

            if (
                    index >= actionsJson.length()
            ) {
                break;
            }

            if (
                    actionsJson.charAt(index) != '{'
            ) {
                index++;
                continue;
            }

            int objectEnd =
                    findMatchingBracket(
                            actionsJson,
                            index,
                            '{',
                            '}'
                    );

            if (objectEnd < 0) {
                break;
            }

            String object =
                    actionsJson.substring(
                            index,
                            objectEnd + 1
                    );

            String intent =
                    safe(
                            extractJsonString(
                                    object,
                                    "intent"
                            )
                    );

            String target =
                    safe(
                            extractJsonString(
                                    object,
                                    "target"
                            )
                    );

            String targetType =
                    safe(
                            extractJsonString(
                                    object,
                                    "targetType"
                            )
                    );

            String location =
                    safe(
                            extractJsonString(
                                    object,
                                    "location"
                            )
                    );

            String destination =
                    safe(
                            extractJsonString(
                                    object,
                                    "destination"
                            )
                    );

            String source =
                    safe(
                            extractJsonString(
                                    object,
                                    "source"
                            )
                    );

            String message =
                    safe(
                            extractJsonString(
                                    object,
                                    "message"
                            )
                    );

            boolean requiresConfirmation =
                    extractJsonBoolean(
                            object,
                            "requiresConfirmation"
                    );

            // Repair the common small-model shape:
            // {"intent":"find","target":"OpenAI","targetType":"latest"}
            if (
                    intent.equalsIgnoreCase("find")
                            && isWebSearchTargetType(targetType)
            ) {
                intent = "web_search";
            }

            intent =
                    normalizeIntent(
                            intent
                    );

            if (targetType.isBlank()) {
                targetType =
                        inferTargetType(
                                intent
                        );
            } else {
                targetType =
                        normalizeTargetType(
                                targetType
                        );
            }

            location =
                    normalizeLocation(
                            location
                    );

            if (location.equals("none")) {
                location =
                        inferLocation(
                                target
                        );
            }

            destination =
                    normalizeDestination(
                            destination,
                            intent
                    );

            if (
                    !source.startsWith("http://")
                            && !source.startsWith("https://")
            ) {
                source = "";
            }

            if (
                    intent.equals("delete")
                            || intent.equals("move")
                            || intent.equals("copy")
                            || intent.equals("rename")
            ) {
                requiresConfirmation = true;
            }

            if (!intent.equals("chat")) {
                message = "";
            }

            plans.add(
                    new BuddyActionPlan(
                            intent,
                            target,
                            targetType,
                            location,
                            destination,
                            source,
                            requiresConfirmation,
                            message
                    )
            );

            index =
                    objectEnd + 1;
        }

        return plans;
    }

    // ============================================================
    // DETERMINISTIC WEB SEARCH DETECTION
    // ============================================================

    private boolean isDirectWebSearchRequest(String message) {

        if (message == null || message.isBlank()) {
            return false;
        }

        String value =
                message.trim()
                        .toLowerCase();

        if (value.matches(
                ".*\\b(latest|breaking|news|headline|headlines)\\b.*"
        )) {
            return true;
        }

        if (value.matches(
                ".*\\b(current|recent)\\b.*\\b(information|news|update|updates|status|developments)\\b.*"
        )) {
            return true;
        }

        if (value.matches(
                ".*\\b(today|todays|today's)\\b.*\\b(news|headlines|updates)\\b.*"
        )) {
            return true;
        }

        return value.matches(
                ".*\\b(search|search for|google|look up|lookup|find online|find on the web|browse for)\\b.*"
        );
    }

    private boolean isWebSearchTargetType(String targetType) {

        if (targetType == null || targetType.isBlank()) {
            return false;
        }

        String value =
                targetType.trim().toLowerCase();

        return value.equals("latest")
                || value.equals("current")
                || value.equals("recent")
                || value.equals("news")
                || value.equals("headline")
                || value.equals("headlines")
                || value.equals("live")
                || value.equals("internet")
                || value.equals("online")
                || value.equals("web")
                || value.equals("external");
    }

    // ============================================================
    // TARGET TYPE
    // ============================================================

    private String inferTargetType(
            String intent
    ) {

        if (
                intent.equals("open")
                        || intent.equals("close")
                        || intent.equals("locate")
        ) {
            return "application";
        }

        if (
                intent.equals("create")
                        || intent.equals("search")
                        || intent.equals("move")
                        || intent.equals("copy")
                        || intent.equals("rename")
                        || intent.equals("delete")
        ) {
            return "file";
        }

        if (intent.equals("download")) {
            return "url";
        }

        if (intent.equals("play")) {
            return "media";
        }

        return "none";
    }

    // ============================================================
    // LOCATION
    // ============================================================

    private String inferLocation(
            String target
    ) {

        if (
                target == null
                        || target.isBlank()
        ) {
            return "none";
        }

        String value =
                target
                        .trim()
                        .toLowerCase();

        if (
                value.contains("desktop")
        ) {
            return "desktop";
        }

        if (
                value.contains("download")
        ) {
            return "downloads";
        }

        if (
                value.contains("document")
        ) {
            return "documents";
        }

        if (
                value.contains("picture")
        ) {
            return "pictures";
        }

        if (
                value.contains("music")
        ) {
            return "music";
        }

        if (
                value.contains("video")
        ) {
            return "videos";
        }

        return "none";
    }

    // ============================================================
    // INTENT NORMALIZATION
    // ============================================================

    private String normalizeIntent(
            String intent
    ) {

        String value =
                safe(intent)
                        .toLowerCase();

        String[] allowed = {
                "chat",
                "open",
                "close",
                "create",
                "locate",
                "search",
                "web_search",
                "download",
                "move",
                "copy",
                "rename",
                "play",
                "delete",
                "time",
                "date",
                "unknown"
        };

        for (String candidate : allowed) {

            if (
                    value.equals(candidate)
            ) {
                return candidate;
            }
        }

        /*
         * Small tolerance for an imperfect model response.
         * This is not natural-language interpretation.
         */

        if (
                value.equals("web")
                        || value.equals("internet")
                        || value.equals("online")
        ) {
            return "web_search";
        }

        // Small local models sometimes emit "find" as a generic verb.
        if (value.equals("find")) {
            return "search";
        }

        // Some small local models may emit "ask" for ordinary conversation.
        // Buddy has no separate ask action; conversation belongs to chat.
        if (value.equals("ask") || value.equals("conversation")) {
            return "chat";
        }

        return "unknown";
    }

    // ============================================================
    // TARGET TYPE NORMALIZATION
    // ============================================================

    private String normalizeTargetType(
            String targetType
    ) {

        String value =
                safe(targetType)
                        .toLowerCase();

        if (
                value.equals("song")
                        || value.equals("audio")
                        || value.equals("video")
                        || value.equals("photo")
                        || value.equals("image")
                        || value.equals("object")
        ) {
            return "file";
        }

        return value;
    }

    // ============================================================
    // DESTINATION NORMALIZATION
    // ============================================================

    private String normalizeDestination(
            String destination,
            String intent
    ) {

        if (
                destination == null
                        || destination.isBlank()
        ) {
            return "";
        }

        String value =
                destination.trim();

        if (
                "rename".equalsIgnoreCase(
                        intent
                )
        ) {
            return value;
        }

        String normalized =
                normalizeLocation(
                        value
                );

        if (
                !normalized.equals("none")
        ) {
            return normalized;
        }

        return value;
    }

    // ============================================================
    // LOCATION NORMALIZATION
    // ============================================================

    private String normalizeLocation(
            String location
    ) {

        if (
                location == null
                        || location.isBlank()
        ) {
            return "none";
        }

        String value =
                location
                        .trim()
                        .toLowerCase();

        if (
                value.equals("desktop")
        ) {
            return "desktop";
        }

        if (
                value.equals("downloads")
                        || value.equals("download")
        ) {
            return "downloads";
        }

        if (
                value.equals("documents")
                        || value.equals("document")
        ) {
            return "documents";
        }

        if (
                value.equals("pictures")
                        || value.equals("picture")
        ) {
            return "pictures";
        }

        if (
                value.equals("music")
        ) {
            return "music";
        }

        if (
                value.equals("videos")
                        || value.equals("video")
        ) {
            return "videos";
        }

        return "none";
    }

    // ============================================================
    // JSON STRING
    // ============================================================

    private String extractJsonString(
            String json,
            String key
    ) {

        String pattern =
                "\""
                        + key
                        + "\"";

        int keyIndex =
                json.indexOf(
                        pattern
                );

        if (keyIndex < 0) {
            return "";
        }

        int colon =
                json.indexOf(
                        ':',
                        keyIndex + pattern.length()
                );

        if (colon < 0) {
            return "";
        }

        int quoteStart =
                json.indexOf(
                        '"',
                        colon + 1
                );

        if (quoteStart < 0) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        boolean escaped =
                false;

        for (
                int i = quoteStart + 1;
                i < json.length();
                i++
        ) {

            char c =
                    json.charAt(i);

            if (escaped) {

                switch (c) {

                    case 'n' ->
                            result.append('\n');

                    case 'r' ->
                            result.append('\r');

                    case 't' ->
                            result.append('\t');

                    case '"' ->
                            result.append('"');

                    case '\\' ->
                            result.append('\\');

                    case '/' ->
                            result.append('/');

                    default ->
                            result.append(c);
                }

                escaped =
                        false;

                continue;
            }

            if (c == '\\') {

                escaped =
                        true;

                continue;
            }

            if (c == '"') {
                break;
            }

            result.append(c);
        }

        return result.toString();
    }

    // ============================================================
    // JSON BOOLEAN
    // ============================================================

    private boolean extractJsonBoolean(
            String json,
            String key
    ) {

        String pattern =
                "\""
                        + key
                        + "\"";

        int keyIndex =
                json.indexOf(
                        pattern
                );

        if (keyIndex < 0) {
            return false;
        }

        int colon =
                json.indexOf(
                        ':',
                        keyIndex + pattern.length()
                );

        if (colon < 0) {
            return false;
        }

        String value =
                json.substring(
                                colon + 1
                        )
                        .trim()
                        .toLowerCase();

        return value.startsWith("true");
    }

    // ============================================================
    // MATCHING BRACKET
    // ============================================================

    private int findMatchingBracket(
            String text,
            int start,
            char opening,
            char closing
    ) {

        int depth = 0;

        boolean insideString =
                false;

        boolean escaped =
                false;

        for (
                int i = start;
                i < text.length();
                i++
        ) {

            char c =
                    text.charAt(i);

            if (insideString) {

                if (escaped) {

                    escaped =
                            false;

                } else if (c == '\\') {

                    escaped =
                            true;

                } else if (c == '"') {

                    insideString =
                            false;
                }

                continue;
            }

            if (c == '"') {

                insideString =
                        true;

                continue;
            }

            if (c == opening) {

                depth++;

            } else if (c == closing) {

                depth--;

                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    // ============================================================
    // ESCAPE JSON
    // ============================================================

    private String escapeJson(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return text
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\n",
                        "\\n"
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\t",
                        "\\t"
                );
    }

    // ============================================================
    // SAFE
    // ============================================================

    private String safe(
            String value
    ) {

        return value == null
                ? ""
                : value.trim();
    }

    private String safeValue(
            String value
    ) {

        return value == null
                ? ""
                : value.trim();
    }
}
