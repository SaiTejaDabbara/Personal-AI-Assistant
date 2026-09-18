package buddy.commands;

import buddy.actions.ActionManager;
import buddy.actions.GenericFileDownloader;
import buddy.apps.AppManager;
import buddy.brain.BuddyActionPlan;
import buddy.brain.BuddyActionPlanner;
import buddy.brain.BuddyChat;
import buddy.brain.ConversationContext;
import buddy.files.FileManager;
import buddy.logger.ActivityLogger;
import buddy.memory.MemoryExtractor;
import buddy.memory.MemoryManager;
import buddy.resolver.ResolvedTarget;
import buddy.resolver.TargetResolver;
import buddy.web.WebIntelligence;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Commands {

        private final ActionManager actionManager;
        private final AppManager appManager;
        private final ActivityLogger activityLogger;
        private final TargetResolver targetResolver;
        private final FileManager fileManager;
        private final BuddyChat buddyChat;
        private final MemoryManager memoryManager;
        private final MemoryExtractor memoryExtractor;
        private final BuddyActionPlanner actionPlanner;
        private final ConversationContext conversationContext;
        private final GenericFileDownloader fileDownloader;
        private final WebIntelligence webIntelligence;
        private final Preferences localMemory;

        // ============================================================
        // CONSTRUCTOR
        // ============================================================

        public Commands() {

                actionManager = new ActionManager();
                appManager = new AppManager();
                activityLogger = new ActivityLogger();
                targetResolver = new TargetResolver(appManager);
                fileManager = new FileManager();
                buddyChat = new BuddyChat();
                memoryManager = new MemoryManager();
                memoryExtractor = new MemoryExtractor();
                actionPlanner = new BuddyActionPlanner();
                conversationContext = buddyChat.getConversationContext();
                fileDownloader = new GenericFileDownloader();
                webIntelligence = new WebIntelligence();
                localMemory = Preferences.userNodeForPackage(Commands.class).node("buddy-memory");
        }

        // ============================================================
        // MAIN PROCESS
        // ============================================================

        public String process(String userInput) {

                long totalStart = System.currentTimeMillis();

                if (userInput == null || userInput.trim().isEmpty()) {
                        return "Please tell me what you want me to do.";
                }

                String originalInput = userInput.trim();

                // ========================================================
                // CONFIRMATION RESPONSE
                // ========================================================

                if (conversationContext.isWaitingForConfirmation()) {
                        return handleConfirmationResponse(originalInput);
                }

                // A bare yes/no is only meaningful when Buddy is actually
                // waiting for confirmation. Do not send it to the planner,
                // otherwise old conversation context can produce unrelated
                // answers about a previous file or action.
                if (isBareConfirmationWord(originalInput)) {
                        return "There is no action waiting for confirmation.";
                }

                // ========================================================
                // FAST MEMORY PATH
                // ========================================================

                long memoryStart = System.currentTimeMillis();

                // Reserved for future lightweight / background memory updates.

                long memoryEnd = System.currentTimeMillis();

                System.out.println(
                                "Memory time: "
                                                + (memoryEnd - memoryStart)
                                                + " ms");

                // ========================================================
                // SAVE CURRENT MESSAGE
                // ========================================================

                conversationContext.setLastUserMessage(originalInput);

                // ========================================================
                // DETERMINISTIC FAST PATH
                // ========================================================

                String fastResult = tryFastCommand(originalInput);

                if (fastResult != null) {
                        System.out.println(
                                        "Fast command execution time: "
                                                        + (System.currentTimeMillis() - totalStart)
                                                        + " ms");
                        System.out.println(
                                        "Total processing time: "
                                                        + (System.currentTimeMillis() - totalStart)
                                                        + " ms");
                        return fastResult;
                }

                // ========================================================
                // FAST CASUAL CONVERSATION
                // ========================================================
                // Keep a tiny set of truly common conversational replies
                // local. This is a latency optimization only; real questions
                // and commands still use BuddyChat/planner as before.
                String casualResult = tryFastCasualCommand(
                                normalizeFastCommand(originalInput));

                if (casualResult != null) {
                        System.out.println(
                                        "Fast casual response time: "
                                                        + (System.currentTimeMillis() - totalStart)
                                                        + " ms");
                        System.out.println(
                                        "Total processing time: "
                                                        + (System.currentTimeMillis() - totalStart)
                                                        + " ms");
                        return casualResult;
                }

                // ========================================================
                // GENERIC CHAT GATE
                // ========================================================
                // Do not make Ollama classify every sentence.
                //
                // Normal conversation goes directly to BuddyChat.
                // Real executable requests continue to the planner.
                //
                // This is intentionally based on command structure rather
                // than hardcoding conversational topics.

                if (!isPlannerCandidate(originalInput)) {
                        String result = chatWithContextPolicy(
                                        originalInput,
                                        isContextDependentRequest(originalInput));

                        System.out.println(
                                        "Planner bypass: normal conversation");
                        System.out.println(
                                        "Total processing time: "
                                                        + (System.currentTimeMillis() - totalStart)
                                                        + " ms");
                        return result;
                }

                // ========================================================
                // ASK OLLAMA FOR ACTION PLAN
                // ========================================================

                long plannerStart = System.currentTimeMillis();

                // Only supply old task context when the current message
                // explicitly depends on it.
                ConversationContext plannerContext = isContextDependentRequest(originalInput)
                                ? conversationContext
                                : new ConversationContext();

                List<BuddyActionPlan> plans = actionPlanner.plan(
                                originalInput,
                                plannerContext);

                // --------------------------------------------------------
                // PLANNER SAFETY
                // --------------------------------------------------------

                plans = rejectInventedOpenPlans(plans, originalInput);

                plans = rejectInventedWebSearchPlans(plans, originalInput);

                plans = rejectInventedTimePlans(plans, originalInput);

                plans = rejectInventedDatePlans(plans, originalInput);

                plans = rejectInventedClosePlans(plans, originalInput);

                long plannerEnd = System.currentTimeMillis();

                System.out.println(
                                "Planner time: "
                                                + (plannerEnd - plannerStart)
                                                + " ms");

                if (plans == null || plans.isEmpty()) {
                        String result = chatWithContextPolicy(
                                        originalInput,
                                        isContextDependentRequest(originalInput));

                        System.out.println(
                                        "Total processing time: "
                                                        + (System.currentTimeMillis() - totalStart)
                                                        + " ms");

                        return result;
                }

                // ========================================================
                // EXECUTE EVERY ACTION IN ORDER
                // ========================================================

                List<String> results = new ArrayList<>();

                long executionStart = System.currentTimeMillis();

                for (BuddyActionPlan plan : plans) {

                        if (plan == null) {
                                continue;
                        }

                        if (plan.requiresConfirmation()) {

                                conversationContext.setPendingAction(
                                                plan.getIntent(),
                                                createPendingDetails(plan));

                                String confirmationMessage = buildConfirmationMessage(plan);

                                String response = combineResultsAndConfirmation(
                                                results,
                                                confirmationMessage);

                                long executionEnd = System.currentTimeMillis();

                                System.out.println(
                                                "Action execution time: "
                                                                + (executionEnd - executionStart)
                                                                + " ms");

                                System.out.println(
                                                "Total processing time: "
                                                                + (System.currentTimeMillis() - totalStart)
                                                                + " ms");

                                return response;
                        }

                        String result = executePlan(plan, originalInput);

                        if (result != null && !result.isBlank()) {
                                results.add(result);
                        }
                }

                long executionEnd = System.currentTimeMillis();

                System.out.println(
                                "Action execution time: "
                                                + (executionEnd - executionStart)
                                                + " ms");

                String finalResult;

                if (results.isEmpty()) {
                        finalResult = chatWithContextPolicy(
                                        originalInput,
                                        isContextDependentRequest(originalInput));
                } else {
                        finalResult = String.join(System.lineSeparator(), results);
                }

                System.out.println(
                                "Total processing time: "
                                                + (System.currentTimeMillis() - totalStart)
                                                + " ms");

                return finalResult;
        }

        // ============================================================
        // GENERIC PLANNER ROUTING
        // ============================================================

        private boolean isPlannerCandidate(String input) {

                String command = normalizeFastCommand(input);

                if (command.isBlank()) {
                        return false;
                }

                // --------------------------------------------------------
                // 1. Strong action follow-ups.
                //
                // Examples:
                // open it
                // delete that
                // move this file
                // do the same
                // repeat that
                // --------------------------------------------------------

                if (isActionFollowUp(command)) {
                        return true;
                }

                // --------------------------------------------------------
                // 2. Direct executable commands.
                //
                // Keep "start" and "run" because they are valid application
                // commands in the existing system.
                //
                // "show" is deliberately excluded here because conversational
                // phrases such as "show me how..." should remain chat/knowledge.
                // --------------------------------------------------------

                if (command.matches(
                                "^(open|launch|start|run|visit|close|quit|terminate|kill|create|delete|remove|move|copy|rename|download|search|google|lookup|look up|find online|find on the web|browse|locate|play|stop)\\b.*")) {
                        return true;
                }

                // "show <target>" can still be an action, but don't classify
                // common conversational/question forms as executable.
                if (command.matches("^show\\s+.+$")
                                && !command.matches(
                                                "^show\\s+me\\s+(how|what|why|when|where|whether)\\b.*")) {
                        return true;
                }

                // --------------------------------------------------------
                // 3. Multi-step commands.
                //
                // Examples:
                // create Test and open it
                // open Chrome and close Notepad
                // download X and move it
                // --------------------------------------------------------

                if (command.matches(
                                ".*\\b(and|then|also)\\b\\s*(open|launch|start|run|visit|close|quit|terminate|kill|create|delete|remove|move|copy|rename|download|search|google|lookup|look up|find online|find on the web|browse|locate|play|stop)\\b.*")) {
                        return true;
                }

                // --------------------------------------------------------
                // 4. Explicit requests to perform an operation.
                // --------------------------------------------------------

                if (command.matches(
                                ".*\\b(can you|could you|would you|please|i want you to|i need you to|help me)\\b.*\\b(open|launch|start|run|visit|close|create|delete|remove|move|copy|rename|download|search|google|lookup|look up|find|browse|locate|play|stop)\\b.*")) {
                        return true;
                }

                // --------------------------------------------------------
                // 5. Current / external information.
                //
                // This includes:
                // what's the latest on X
                // latest news about X
                // recent updates on X
                // --------------------------------------------------------

                if (hasExplicitWebSearchIntent(command)
                                || hasExplicitTimeIntent(command)
                                || hasExplicitDateIntent(command)) {
                        return true;
                }

                return false;
        }

        private boolean isActionFollowUp(String command) {

                if (command == null || command.isBlank()) {
                        return false;
                }

                // Strong contextual commands with no explicit object.
                if (command.matches(
                                "^(do the same|do that|repeat that|again|same thing)$")) {
                        return true;
                }

                // Action verb + contextual reference.
                return command.matches(
                                ".*\\b(open|launch|visit|start|run|close|delete|remove|move|copy|rename|download|play|locate)\\b.*\\b(it|this|that|these|those|them|there|file|folder)\\b.*");
        }

        // ============================================================
        // PLANNER SAFETY
        // ============================================================

        private List<BuddyActionPlan> rejectInventedOpenPlans(
                        List<BuddyActionPlan> plans,
                        String originalInput) {

                if (plans == null || plans.isEmpty()) {
                        return plans;
                }

                String command = normalizeFastCommand(originalInput);

                if (hasExplicitOpenCommand(command)) {
                        return plans;
                }

                boolean removedOpenPlan = false;
                List<BuddyActionPlan> filtered = new ArrayList<>();

                for (BuddyActionPlan plan : plans) {

                        if (plan == null) {
                                continue;
                        }

                        String intent = safeLower(plan.getIntent());

                        if (intent.equals("open")) {
                                removedOpenPlan = true;

                                System.out.println(
                                                "Planner safety: ignored invented open action for normal conversation.");

                                continue;
                        }

                        filtered.add(plan);
                }

                if (removedOpenPlan && filtered.isEmpty()) {
                        return new ArrayList<>();
                }

                return filtered;
        }

        private boolean hasExplicitOpenCommand(String command) {

                if (command == null || command.isBlank()) {
                        return false;
                }

                return command.matches(
                                ".*\\b(open|launch|start|run|show|visit)\\b.*");
        }

        // ============================================================
        // WEB SEARCH SAFETY
        // ============================================================

        private List<BuddyActionPlan> rejectInventedWebSearchPlans(
                        List<BuddyActionPlan> plans,
                        String originalInput) {

                if (plans == null || plans.isEmpty()) {
                        return plans;
                }

                String command = normalizeFastCommand(originalInput);

                if (hasExplicitWebSearchIntent(command)) {
                        return plans;
                }

                boolean removedWebSearch = false;
                List<BuddyActionPlan> filtered = new ArrayList<>();

                for (BuddyActionPlan plan : plans) {

                        if (plan == null) {
                                continue;
                        }

                        String intent = safeLower(plan.getIntent());

                        if (intent.equals("web_search")) {

                                removedWebSearch = true;

                                System.out.println(
                                                "Planner safety: ignored invented web search action for normal conversation.");

                                continue;
                        }

                        filtered.add(plan);
                }

                if (removedWebSearch && filtered.isEmpty()) {
                        return new ArrayList<>();
                }

                return filtered;
        }

        private boolean hasExplicitWebSearchIntent(String command) {

                if (command == null || command.isBlank()) {
                        return false;
                }

                // Direct search requests.
                if (command.matches(
                                ".*\\b(search|search for|google|look up|lookup|find online|find on the web|browse for)\\b.*")) {
                        return true;
                }

                // Current / latest / breaking information.
                if (command.matches(
                                ".*\\b(latest|breaking|news|headline|headlines)\\b.*")) {
                        return true;
                }

                // Current / recent information.
                if (command.matches(
                                ".*\\b(current|recent)\\b.*\\b(information|news|update|updates|status|developments)\\b.*")) {
                        return true;
                }

                // Today's news.
                if (command.matches(
                                ".*\\b(today|todays|today's)\\b.*\\b(news|headlines|updates)\\b.*")) {
                        return true;
                }

                return false;
        }

        // ============================================================
        // TIME SAFETY
        // ============================================================

        private List<BuddyActionPlan> rejectInventedTimePlans(
                        List<BuddyActionPlan> plans,
                        String originalInput) {

                if (plans == null || plans.isEmpty()) {
                        return plans;
                }

                String command = normalizeFastCommand(originalInput);

                if (hasExplicitTimeIntent(command)) {
                        return plans;
                }

                boolean removedTime = false;
                List<BuddyActionPlan> filtered = new ArrayList<>();

                for (BuddyActionPlan plan : plans) {

                        if (plan == null) {
                                continue;
                        }

                        String intent = safeLower(plan.getIntent());

                        if (intent.equals("time")) {

                                removedTime = true;

                                System.out.println(
                                                "Planner safety: ignored invented time action for normal conversation.");

                                continue;
                        }

                        filtered.add(plan);
                }

                if (removedTime && filtered.isEmpty()) {
                        return new ArrayList<>();
                }

                return filtered;
        }

        private boolean hasExplicitTimeIntent(String command) {

                if (command == null || command.isBlank()) {
                        return false;
                }

                return command.matches(
                                ".*\\b(what|whats|what's|tell me|can you tell me|give me|show me|current)\\b.*\\btime\\b.*")
                                || command.matches("^what\\s+time\\s+is\\s+it.*$")
                                || command.matches("^current\\s+time.*$")
                                || command.matches("^time\\s+now.*$")
                                || command.matches("^tell\\s+me\\s+the\\s+time.*$");
        }

        // ============================================================
        // DATE SAFETY
        // ============================================================

        private List<BuddyActionPlan> rejectInventedDatePlans(
                        List<BuddyActionPlan> plans,
                        String originalInput) {

                if (plans == null || plans.isEmpty()) {
                        return plans;
                }

                String command = normalizeFastCommand(originalInput);

                if (hasExplicitDateIntent(command)) {
                        return plans;
                }

                boolean removedDate = false;
                List<BuddyActionPlan> filtered = new ArrayList<>();

                for (BuddyActionPlan plan : plans) {

                        if (plan == null) {
                                continue;
                        }

                        String intent = safeLower(plan.getIntent());

                        if (intent.equals("date")) {

                                removedDate = true;

                                System.out.println(
                                                "Planner safety: ignored invented date action for normal conversation.");

                                continue;
                        }

                        filtered.add(plan);
                }

                if (removedDate && filtered.isEmpty()) {
                        return new ArrayList<>();
                }

                return filtered;
        }

        private boolean hasExplicitDateIntent(String command) {

                if (command == null || command.isBlank()) {
                        return false;
                }

                return command.equals("date")
                                || command.equals("today")
                                || command.equals("today date")
                                || command.equals("date today")
                                || command.equals("current date")
                                || command.equals("today's date")
                                || command.equals("todays date")
                                || command.matches(
                                                ".*\\b(what|whats|what's|tell me|show me|give me|current)\\b.*\\bdate\\b.*")
                                || command.matches(
                                                ".*\\bwhat day is it\\b.*");
        }

        // ============================================================
        // CLOSE SAFETY
        // ============================================================

        private List<BuddyActionPlan> rejectInventedClosePlans(
                        List<BuddyActionPlan> plans,
                        String originalInput) {

                if (plans == null || plans.isEmpty()) {
                        return plans;
                }

                String command = normalizeFastCommand(originalInput);

                if (hasExplicitCloseCommand(command)) {
                        return plans;
                }

                boolean removedClose = false;
                List<BuddyActionPlan> filtered = new ArrayList<>();

                for (BuddyActionPlan plan : plans) {

                        if (plan == null) {
                                continue;
                        }

                        String intent = safeLower(plan.getIntent());

                        if (intent.equals("close")) {

                                removedClose = true;

                                System.out.println(
                                                "Planner safety: ignored invented close action for normal conversation.");

                                continue;
                        }

                        filtered.add(plan);
                }

                if (removedClose && filtered.isEmpty()) {
                        return new ArrayList<>();
                }

                return filtered;
        }

        private boolean hasExplicitCloseCommand(String command) {

                if (command == null || command.isBlank()) {
                        return false;
                }

                // "stop" is intentionally excluded because it may mean
                // stopping media rather than closing an application.
                return command.matches(
                                ".*\\b(close|quit|terminate|kill)\\b.*");
        }

        // ============================================================
        // CONTEXT SAFETY
        // ============================================================

        private boolean isContextDependentRequest(String input) {

                String command = normalizeFastCommand(input);

                if (command.isBlank()) {
                        return false;
                }

                // Pronouns / references.
                if (command.matches(
                                ".*\\b(it|this|that|these|those|them|there)\\b.*")) {
                        return true;
                }

                if (command.matches(
                                "^(do the same|do that|repeat that|again|same thing).*$")) {
                        return true;
                }

                if (command.matches(
                                "^(what about|how about|and|also|then)\\b.*")) {
                        return true;
                }

                if (command.contains("previous")
                                || command.contains("last one")
                                || command.contains("last file")
                                || command.contains("last folder")
                                || command.contains("recently created")) {
                        return true;
                }

                return false;
        }

        private String tryFastCasualCommand(String command) {

                if (command == null || command.isBlank()) {
                        return null;
                }

                switch (command) {
                        case "hi":
                        case "hello":
                        case "hey":
                        case "hi buddy":
                        case "hey buddy":
                                return "Hey boss. What can I do for you?";

                        case "how are you":
                        case "how are you doing":
                        case "how are things":
                        case "how are u":
                        case "how ya doing":
                        case "how are you doing today":
                                return "I'm doing great and ready to help.";

                        case "are you there":
                        case "are you listening":
                        case "are ya there":
                        case "are u there":
                        case "you there":
                                return "Yes boss, I'm listening.";

                        case "thank you":
                        case "thanks":
                        case "thank u":
                                return "You're welcome, boss.";

                        case "good morning":
                                return "Good morning, boss.";

                        case "good afternoon":
                                return "Good afternoon, boss.";

                        case "good evening":
                                return "Good evening, boss.";

                        case "what's up":
                        case "whats up":
                                return "I am here and ready to help.";

                        case "who are you":
                                return "I am Buddy, your personal desktop assistant.";

                        default:
                                return null;
                }
        }

        private String chatWithContextPolicy(
                        String input,
                        boolean allowContext) {

                // Reuse the already-warm BuddyChat instance for both contextual
                // and unrelated conversation. Creating a new BuddyChat here
                // can reload/warm the Ollama model and add many seconds of
                // latency to otherwise simple voice commands.
                //
                // Context-sensitive requests already pass through the shared
                // ConversationContext. For unrelated chat, the current message
                // is still sent directly without invoking the action planner.
                return buddyChat.chat(input);
        }

        // ============================================================
        // FAST COMMAND ROUTER
        // ============================================================

        private String tryFastCommand(String input) {

                String command = normalizeFastCommand(input);

                // --------------------------------------------------------
                // FAST MEMORY
                // --------------------------------------------------------

                String memoryResult = tryFastMemoryCommand(command, input);

                if (memoryResult != null) {
                        return memoryResult;
                }

                if (command.isBlank()) {
                        return null;
                }

                if (isDateCommand(command)) {
                        return executeFastDateCommand(input);
                }

                if (isTimeCommand(command)) {
                        return executeFastTimeCommand(input);
                }

                // --------------------------------------------------------
                // FAST CREATE FOLDER
                //
                // Common folder-creation commands are deterministic and do
                // not need Ollama. This also avoids the small-model mistake
                // of treating "desktop" as the target type instead of the
                // location.
                // --------------------------------------------------------
                String fastCreateFolderResult = tryFastCreateFolderCommand(
                                command,
                                input);

                if (fastCreateFolderResult != null) {
                        return fastCreateFolderResult;
                }

                // --------------------------------------------------------
                // FAST VOICE ASR CORRECTION
                //
                // Whisper can occasionally turn a spoken "open Spotify"
                // into "append Spotify". Only treat this as an open command
                // when the remainder resolves to a real application/website/
                // Windows app. That prevents ordinary "append ..." text from
                // being executed as an action.
                // --------------------------------------------------------
                String correctedOpenResult = tryFastVoiceOpenCorrection(
                                command,
                                input);

                if (correctedOpenResult != null) {
                        return correctedOpenResult;
                }

                // --------------------------------------------------------
                // FAST SIMPLE OPEN
                //
                // A single explicit open/start/launch/visit/run command
                // does not need Ollama to understand the intent. This keeps
                // the existing app-opening stack unchanged while avoiding
                // the planner's multi-second latency for obvious commands.
                // Context-dependent phrases such as "open it" are left
                // for the planner. Multi-step commands are also left alone.
                // --------------------------------------------------------
                if (isFastSimpleOpenCommand(command)) {
                        String fastOpenTarget = extractFastOpenTarget(input);

                        if (fastOpenTarget != null
                                        && !fastOpenTarget.isBlank()) {
                                String result = executeFastSimpleOpenCommand(
                                                fastOpenTarget,
                                                input);

                                if (result != null) {
                                        return result;
                                }
                        }
                }

                // --------------------------------------------------------
                // FAST SIMPLE CLOSE
                //
                // Keep close behavior on the existing ActionManager path.
                // --------------------------------------------------------
                if (isFastSimpleCloseCommand(command)) {
                        return executeFastSimpleCloseCommand(
                                        command.substring(command.indexOf(' ') + 1).trim(),
                                        input);
                }

                return null;
        }

        // ============================================================
        // FAST SIMPLE OPEN / CLOSE
        // ============================================================

        private String tryFastVoiceOpenCorrection(
                        String command,
                        String originalInput) {

                if (command == null || command.isBlank()) {
                        return null;
                }

                String prefix = "append ";

                if (!command.startsWith(prefix)) {
                        return null;
                }

                String target = command.substring(prefix.length()).trim();

                if (target.isBlank()
                                || target.contains(" and ")
                                || target.contains(" then ")) {
                        return null;
                }

                ResolvedTarget resolvedTarget = targetResolver.resolve(target);

                if (resolvedTarget == null) {
                        return null;
                }

                String type = safeLower(resolvedTarget.getType());

                if (!type.equals("application")
                                && !type.equals("website")
                                && !type.equals("windows_app")
                                && !type.equals("search")) {
                        return null;
                }

                String result;

                if (type.equals("application")) {
                        result = actionManager.openApplication(
                                        resolvedTarget.getValue());
                } else {
                        result = actionManager.openWebsite(
                                        resolvedTarget.getValue());
                }

                activityLogger.log(
                                originalInput,
                                result);

                conversationContext.setLastAction(
                                "Opened an item");

                conversationContext.setLastObject(
                                target);

                conversationContext.setLastLocation(
                                resolvedTarget.getValue());

                return result;
        }

        private String tryFastCreateFolderCommand(
                        String command,
                        String originalInput) {

                if (command == null || command.isBlank()) {
                        return null;
                }

                if (!command.matches(
                                "^(create|make|new)\\s+(?:a\\s+)?folder\\b.*$")) {
                        return null;
                }

                String source = originalInput == null
                                ? command
                                : originalInput.trim();

                Pattern pattern = Pattern.compile(
                                "(?i)^(?:create|make|new)\\s+(?:a\\s+)?folder\\s+(?:called\\s+|named\\s+)?(.+?)(?:\\s+on\\s+(?:my\\s+)?(desktop|downloads|documents|pictures|music|videos))?\\s*$");

                Matcher matcher = pattern.matcher(source);

                if (!matcher.matches()) {
                        return null;
                }

                String folderName = matcher.group(1) == null
                                ? ""
                                : matcher.group(1).trim();

                String location = matcher.group(2) == null
                                ? "desktop"
                                : matcher.group(2).trim().toLowerCase(Locale.ROOT);

                // Guard against the optional location clause being captured
                // inside the folder name.
                folderName = folderName.replaceFirst(
                                "(?i)\\s+on\\s+(?:my\\s+)?(?:desktop|downloads|documents|pictures|music|videos)\\s*$",
                                "").trim();

                if (folderName.isBlank()) {
                        return "What would you like me to name the folder?";
                }

                String parentPath = resolveCreationParent(location);

                String result;

                if (parentPath != null) {
                        result = fileManager.createFolderAt(
                                        parentPath,
                                        folderName);
                } else {
                        result = fileManager.createFolder(
                                        folderName);
                }

                String actualLocation = determineCreatedLocation(
                                folderName,
                                parentPath);

                updateContextAfterCreate(
                                "Created a folder",
                                folderName,
                                actualLocation);

                activityLogger.log(
                                originalInput,
                                result);

                return result;
        }

        private boolean isFastSimpleOpenCommand(String command) {

                if (command == null || command.isBlank()) {
                        return false;
                }

                // Do not steal context-dependent or multi-step commands.
                if (command.matches(
                                ".*\\b(it|this|that|these|those|them|there)\\b.*")) {
                        return false;
                }

                if (command.matches(
                                ".*\\b(and|then|also)\\b.*")) {
                        return false;
                }

                return command.matches(
                                "^(?:(can you|could you|would you|please|okay|ok)\\s+)*(open|launch|start|run|visit)\\s+.+$");
        }

        private String extractFastOpenTarget(String input) {

                if (input == null || input.isBlank()) {
                        return null;
                }

                String value = input.trim();

                value = value.replaceFirst(
                                "(?i)^(?:okay|ok|please|can you|could you|would you)\\s*[, ]*",
                                "");

                value = value.replaceFirst(
                                "(?i)^(?:open|launch|start|run|visit)\\s+",
                                "");

                value = value.replaceFirst(
                                "(?i)\\s+(?:for me|please)$",
                                "");

                return value.trim();
        }

        private boolean isFastSimpleCloseCommand(String command) {

                if (command == null || command.isBlank()) {
                        return false;
                }

                if (command.matches(
                                ".*\\b(and|then|also)\\b.*")) {
                        return false;
                }

                if (command.matches(
                                ".*\\b(it|this|that|these|those|them|there)\\b.*")) {
                        return false;
                }

                return command.matches(
                                "^(close|quit|terminate|kill)\\s+.+$");
        }

        private String executeFastSimpleOpenCommand(
                        String target,
                        String originalInput) {

                target = safe(target);

                if (target.isBlank()) {
                        return null;
                }

                ResolvedTarget resolvedTarget = targetResolver.resolve(target);

                if (resolvedTarget == null) {
                        return "I could not find " + target + ".";
                }

                String result;

                if (resolvedTarget.getType()
                                .equalsIgnoreCase("application")) {

                        result = actionManager.openApplication(
                                        resolvedTarget.getValue());

                } else if (resolvedTarget.getType()
                                .equalsIgnoreCase("website")) {

                        result = actionManager.openWebsite(
                                        resolvedTarget.getValue());

                } else if (resolvedTarget.getType()
                                .equalsIgnoreCase("windows_app")) {

                        result = actionManager.openWindowsApp(
                                        resolvedTarget.getValue());

                } else if (resolvedTarget.getType()
                                .equalsIgnoreCase("search")) {

                        result = actionManager.openWebsite(
                                        resolvedTarget.getValue());

                } else {

                        return "I could not find " + target + ".";
                }

                activityLogger.log(
                                originalInput,
                                result);

                conversationContext.setLastAction(
                                "Opened an item");

                conversationContext.setLastObject(
                                target);

                conversationContext.setLastLocation(
                                resolvedTarget.getValue());

                return result;
        }

        private String executeFastSimpleCloseCommand(
                        String target,
                        String originalInput) {

                target = safe(target);

                if (target.isBlank()) {
                        return null;
                }

                String result = actionManager.closeWindowsApp(
                                target);

                activityLogger.log(
                                originalInput,
                                result);

                conversationContext.setLastAction(
                                "Closed an application");

                conversationContext.setLastObject(
                                target);

                conversationContext.setLastLocation(
                                null);

                return result;
        }

        // ============================================================
        // ROBUST FAST MEMORY
        // ============================================================

        private String tryFastMemoryCommand(
                        String command,
                        String originalInput) {

                if (command == null || command.isBlank()) {
                        return null;
                }

                // --------------------------------------------------------
                // EXPLICIT MEMORY QUESTIONS
                // --------------------------------------------------------

                if (command.equals("what is my name")
                                || command.equals("what's my name")
                                || command.equals("whats my name")
                                || command.equals("whay is my name")
                                || command.equals("why is my name")
                                || command.equals("do you know my name")
                                || command.equals("do you remember my name")
                                || command.equals("tell me my name")
                                || command.equals("can you tell me my name")) {

                        String name = memoryManager.getUserMemory("name");

                        if (name != null && !name.isBlank()) {

                                String greeting = "Hi, your name is " + name + ".";

                                return finishMemoryResponse(
                                                originalInput,
                                                greeting,
                                                "Recalled user's name");
                        }

                        return finishMemoryResponse(
                                        originalInput,
                                        "Hi, I don't have your name saved yet.",
                                        "Could not recall user's name");
                }

                String questionKey = extractMemoryQuestionKey(command);

                if (questionKey != null) {

                        String value = readLocalMemory(questionKey);

                        if (value == null || value.isBlank()) {

                                return finishMemoryResponse(
                                                originalInput,
                                                "I don't have your "
                                                                + humanizeMemoryKey(questionKey)
                                                                + " saved yet.",
                                                "Could not recall user memory");
                        }

                        return finishMemoryResponse(
                                        originalInput,
                                        "Your "
                                                        + humanizeMemoryKey(questionKey)
                                                        + " is "
                                                        + value
                                                        + ".",
                                        "Recalled user memory");
                }

                // --------------------------------------------------------
                // MEMORY STATEMENTS
                // --------------------------------------------------------

                String statement = originalInput;

                if (statement.startsWith("remember that ")) {

                        statement = statement.substring(
                                        "remember that ".length())
                                        .trim();

                } else if (statement.startsWith("remember ")) {

                        statement = statement.substring(
                                        "remember ".length())
                                        .trim();
                }

                java.util.regex.Matcher myNameIs = java.util.regex.Pattern.compile(
                                "^my\\s+name\\s+is\\s+(.+)$",
                                java.util.regex.Pattern.CASE_INSENSITIVE)
                                .matcher(statement);

                if (myNameIs.matches()) {

                        String value = cleanMemoryValue(
                                        myNameIs.group(1));

                        if (!value.isBlank()) {
                                return saveLocalMemory(
                                                "name",
                                                value,
                                                originalInput);
                        }
                }

                java.util.regex.Matcher myFact = java.util.regex.Pattern.compile(
                                "^my\\s+(.+?)\\s+is\\s+(.+)$",
                                java.util.regex.Pattern.CASE_INSENSITIVE)
                                .matcher(statement);

                if (myFact.matches()) {

                        String rawKey = myFact.group(1).trim();

                        String value = cleanMemoryValue(
                                        myFact.group(2));

                        String key = normalizeMemoryKey(rawKey);

                        boolean explicitRemember = command.startsWith("remember that ")
                                        || command.startsWith("remember ");

                        boolean looksLikeMemoryFact = isLikelyMemoryFactKey(rawKey);

                        if ((explicitRemember
                                        || looksLikeMemoryFact)
                                        && key != null
                                        && !value.isBlank()) {

                                return saveLocalMemory(
                                                key,
                                                value,
                                                originalInput);
                        }
                }

                java.util.regex.Matcher iPrefer = java.util.regex.Pattern.compile(
                                "^i\\s+(?:prefer|like|love)\\s+(.+)$",
                                java.util.regex.Pattern.CASE_INSENSITIVE)
                                .matcher(statement);

                if (iPrefer.matches()) {

                        String value = cleanMemoryValue(
                                        iPrefer.group(1));

                        String key = "preference";

                        if (!value.isBlank()) {

                                return saveLocalMemory(
                                                key,
                                                value,
                                                originalInput);
                        }
                }

                return null;
        }

        private String extractMemoryQuestionKey(
                        String command) {

                if (command == null || command.isBlank()) {
                        return null;
                }

                String raw = null;

                if (command.startsWith("what is my ")) {

                        raw = command.substring(
                                        "what is my ".length());

                } else if (command.startsWith("what's my ")) {

                        raw = command.substring(
                                        "what's my ".length());

                } else if (command.startsWith("whats my ")) {

                        raw = command.substring(
                                        "whats my ".length());

                } else if (command.startsWith("what do you know about my ")) {

                        raw = command.substring(
                                        "what do you know about my ".length());

                } else if (command.startsWith("what do you remember about my ")) {

                        raw = command.substring(
                                        "what do you remember about my ".length());
                }

                if (raw != null && !raw.isBlank()) {
                        return normalizeMemoryKey(raw);
                }

                java.util.regex.Matcher natural = java.util.regex.Pattern.compile(
                                "^(?:what|which)\\s+(.+?)\\s+do\\s+i\\s+(?:like|prefer|love|use)$",
                                java.util.regex.Pattern.CASE_INSENSITIVE)
                                .matcher(command);

                if (natural.matches()) {

                        return normalizeNaturalMemorySubject(
                                        natural.group(1).trim());
                }

                java.util.regex.Matcher asMy = java.util.regex.Pattern.compile(
                                "^what\\s+do\\s+i\\s+(?:like|prefer|love|use)\\s+as\\s+my\\s+(.+)$",
                                java.util.regex.Pattern.CASE_INSENSITIVE)
                                .matcher(command);

                if (asMy.matches()) {

                        return normalizeNaturalMemorySubject(
                                        asMy.group(1).trim());
                }

                return null;
        }

        private String normalizeNaturalMemorySubject(
                        String subject) {

                if (subject == null || subject.isBlank()) {
                        return null;
                }

                String normalized = subject
                                .trim()
                                .toLowerCase(Locale.ROOT)
                                .replaceAll("\\s+", " ")
                                .trim();

                normalized = normalized
                                .replaceAll(
                                                "^(?:my|the)\\s+",
                                                "")
                                .replaceAll(
                                                "\\s+(?:of|for)\\s+me$",
                                                "")
                                .trim();

                String key;

                if (normalized.equals("browser")
                                || normalized.equals("browsers")
                                || normalized.equals("web browser")
                                || normalized.equals("web browsers")) {

                        key = "favorite_browser";

                } else if (normalized.equals("color")
                                || normalized.equals("colour")
                                || normalized.equals("colors")
                                || normalized.equals("colours")) {

                        key = "favorite_color";

                } else if (normalized.equals("app")
                                || normalized.equals("application")) {

                        key = "favorite_app";

                } else if (normalized.equals("food")) {

                        key = "favorite_food";

                } else if (normalized.equals("movie")
                                || normalized.equals("film")) {

                        key = "favorite_movie";

                } else if (normalized.equals("song")
                                || normalized.equals("music")) {

                        key = "favorite_song";

                } else if (normalized.equals("game")) {

                        key = "favorite_game";

                } else if (normalized.equals("language")) {

                        key = "favorite_language";

                } else {

                        key = normalizeMemoryKey(normalized);
                }

                return key;
        }

        private boolean isLikelyMemoryFactKey(
                        String rawKey) {

                if (rawKey == null || rawKey.isBlank()) {
                        return false;
                }

                String key = rawKey
                                .trim()
                                .toLowerCase(Locale.ROOT)
                                .replaceAll("\\s+", "_")
                                .replaceAll("[^a-z0-9_]+", "")
                                .replaceAll("_+", "_")
                                .replaceAll("^_|_$", "");

                if (key.isBlank()) {
                        return false;
                }

                return key.startsWith("favorite_")
                                || key.startsWith("favourite_")
                                || key.startsWith("fav_")
                                || key.startsWith("preferred_")
                                || key.startsWith("prefered_")
                                || key.startsWith("best_");
        }

        private String normalizeMemoryKey(
                        String rawKey) {

                if (rawKey == null || rawKey.isBlank()) {
                        return null;
                }

                String key = rawKey
                                .trim()
                                .toLowerCase(Locale.ROOT)
                                .replaceAll("\\s+", "_")
                                .replaceAll("[^a-z0-9_]+", "")
                                .replaceAll("_+", "_")
                                .replaceAll("^_|_$", "");

                if (key.isBlank()) {
                        return null;
                }

                if (key.equals("favorite_browser")
                                || key.equals("fav_browser")) {
                        return "favorite_browser";
                }

                if (key.equals("favourite_browser")
                                || key.equals("favouritebrowser")) {
                        return "favorite_browser";
                }

                if (key.equals("favorite_color")
                                || key.equals("fav_color")) {
                        return "favorite_color";
                }

                if (key.equals("favourite_color")) {
                        return "favorite_color";
                }

                String fuzzyKey = correctKnownMemoryKey(key);

                return fuzzyKey != null
                                ? fuzzyKey
                                : key;
        }

        private String correctKnownMemoryKey(
                        String key) {

                if (key == null || key.isBlank()) {
                        return null;
                }

                String[] knownKeys = {
                                "favorite_browser",
                                "favorite_color",
                                "name",
                                "preferred_browser",
                                "preferred_app",
                                "favorite_app",
                                "favorite_food",
                                "favorite_movie",
                                "favorite_song",
                                "favorite_game",
                                "favorite_language"
                };

                String best = null;
                int bestDistance = Integer.MAX_VALUE;

                for (String known : knownKeys) {

                        int distance = levenshtein(key, known);

                        int allowed = key.length() >= 10
                                        ? 2
                                        : 1;

                        if (distance <= allowed
                                        && distance < bestDistance) {

                                bestDistance = distance;
                                best = known;
                        }
                }

                return best;
        }

        private String cleanMemoryValue(
                        String value) {

                if (value == null) {
                        return "";
                }

                return value
                                .trim()
                                .replaceAll(
                                                "^[\\\"']+|[\\\"']+$",
                                                "")
                                .replaceAll(
                                                "[.!?]+$",
                                                "")
                                .trim();
        }

        private String readLocalMemory(
                        String key) {

                try {
                        return localMemory.get(key, "");
                } catch (Exception ignored) {
                        return "";
                }
        }

        private String saveLocalMemory(
                        String key,
                        String value,
                        String originalInput) {

                try {

                        localMemory.put(key, value);
                        localMemory.flush();
                        memoryManager.saveUserMemory(key, value);

                } catch (Exception e) {

                        return finishMemoryResponse(
                                        originalInput,
                                        "I understood that, but I could not save it to memory.",
                                        "Memory save failed");
                }

                String result = "Got it. I'll remember that your "
                                + humanizeMemoryKey(key)
                                + " is "
                                + value
                                + ".";

                return finishMemoryResponse(
                                originalInput,
                                result,
                                "Saved user memory");
        }

        private String finishMemoryResponse(
                        String originalInput,
                        String result,
                        String action) {

                activityLogger.log(
                                originalInput,
                                result);

                conversationContext.setLastAction(
                                action);

                conversationContext.setLastObject(
                                null);

                conversationContext.setLastLocation(
                                null);

                return result;
        }

        private String humanizeMemoryKey(
                        String key) {

                if (key == null || key.isBlank()) {
                        return "memory";
                }

                String[] words = key.replace('_', ' ')
                                .split("\\s+");

                StringBuilder result = new StringBuilder();

                for (String word : words) {

                        if (word.isBlank()) {
                                continue;
                        }

                        if (result.length() > 0) {
                                result.append(' ');
                        }

                        result.append(word);
                }

                return result.toString();
        }

        private String normalizeFastCommand(
                        String input) {

                if (input == null) {
                        return "";
                }

                return input
                                .trim()
                                .toLowerCase(Locale.ROOT)
                                .replaceAll("[!?.,]+$", "")
                                .replaceAll("\\s+", " ")
                                .trim();
        }

        // ============================================================
        // FAST DATE
        // ============================================================

        private boolean isDateCommand(
                        String command) {

                if (command == null || command.isBlank()) {
                        return false;
                }

                String normalized = command.trim()
                                .toLowerCase(Locale.ROOT)
                                .replaceAll("[!?.,]+$", "")
                                .replaceAll("\\s+", " ")
                                .trim();

                return normalized.equals("date")
                                || normalized.equals("today")
                                || normalized.equals("today date")
                                || normalized.equals("date today")
                                || normalized.equals("current date")
                                || normalized.equals("today's date")
                                || normalized.equals("todays date")
                                || normalized.equals("what is the date")
                                || normalized.equals("what is the date today")
                                || normalized.equals("what's the date today")
                                || normalized.equals("what is today's date")
                                || normalized.equals("what's today's date")
                                || normalized.equals("what is todays date")
                                || normalized.equals("what's todays date")
                                || normalized.equals("what is date today")
                                || normalized.equals("what's date today")
                                || normalized.equals("whats the date today")
                                || normalized.equals("whats todays date")
                                || normalized.equals("what date is today")
                                || normalized.equals("tell me today's date")
                                || normalized.equals("tell me todays date")
                                || normalized.equals("tell me the date today")
                                || normalized.equals("tell me what date it is today")
                                || normalized.matches(
                                                "^(?:what(?:\\s+(?:is|s)|'s)|whats)\\s+(?:the\\s+)?(?:date|today's date|todays date)$")
                                || normalized.matches(
                                                "^(?:what\\s+date\\s+is\\s+today|tell\\s+me\\s+(?:the\\s+)?(?:date|today's date|todays date)|tell\\s+me\\s+what\\s+date\\s+it\\s+is\s+today)$");
        }

        // ============================================================
        // FAST TIME
        // ============================================================

        private boolean isTimeCommand(
                        String command) {

                if (command == null || command.isBlank()) {
                        return false;
                }

                String normalized = command.trim()
                                .toLowerCase(Locale.ROOT)
                                .replaceAll("[!?.,]+$", "")
                                .replaceAll("\\s+", " ")
                                .trim();

                return normalized.equals("time")
                                || normalized.equals("current time")
                                || normalized.equals("what time is it")
                                || normalized.equals("what is the time")
                                || normalized.equals("what's the time")
                                || normalized.equals("what time is it now")
                                || normalized.equals("what is the current time")
                                || normalized.equals("what's the current time")
                                || normalized.equals("tell me the time")
                                || normalized.equals("tell me current time")
                                || normalized.equals("tell me the current time")
                                || normalized.equals("time now")
                                || normalized.matches(
                                                "^(?:what(?:\\s+(?:is|s)|'s)|whats|tell\\s+me)\\s+(?:the\\s+)?(?:current\\s+)?time(?:\\s+now)?$")
                                || normalized.matches("^what\\s+time\\s+is\\s+it(?:\\s+now)?$");
        }

        private String executeFastDateCommand(
                        String originalInput) {

                LocalDate today = LocalDate.now();

                String date = today.format(
                                DateTimeFormatter.ofPattern(
                                                "MMMM d, yyyy",
                                                Locale.ROOT));

                String result = "Today is " + date + ".";

                conversationContext.setLastAction(
                                "Checked the current date");

                conversationContext.setLastObject(
                                "current date");

                conversationContext.setLastLocation(
                                null);

                activityLogger.log(
                                originalInput,
                                result);

                return result;
        }

        private String executeFastTimeCommand(
                        String originalInput) {

                LocalTime now = LocalTime.now();

                String time = now.format(
                                DateTimeFormatter.ofPattern(
                                                "h:mm a",
                                                Locale.ROOT));

                String result = "The current time is "
                                + time
                                + ".";

                conversationContext.setLastAction(
                                "Checked the current time");

                conversationContext.setLastObject(
                                "current time");

                conversationContext.setLastLocation(
                                null);

                activityLogger.log(
                                originalInput,
                                result);

                return result;
        }

        // ============================================================
        // BARE CONFIRMATION CHECK
        // ============================================================

        private boolean isBareConfirmationWord(
                        String input) {

                if (input == null) {
                        return false;
                }

                String normalized = input.trim()
                                .toLowerCase();

                return normalized.equals("yes")
                                || normalized.equals("y")
                                || normalized.equals("yeah")
                                || normalized.equals("yep")
                                || normalized.equals("sure")
                                || normalized.equals("okay")
                                || normalized.equals("ok")
                                || normalized.equals("no")
                                || normalized.equals("n")
                                || normalized.equals("nope")
                                || normalized.equals("cancel")
                                || normalized.equals("stop");
        }

        // ============================================================
        // CONFIRMATION HANDLER
        // ============================================================

        private String handleConfirmationResponse(
                        String input) {

                String normalized = input.trim()
                                .toLowerCase();

                if (!normalized.isBlank()) {

                        if (levenshtein(
                                        normalized,
                                        "yes") <= 1
                                        && normalized.length() <= 4) {

                                normalized = "yes";

                        } else if (levenshtein(
                                        normalized,
                                        "no") <= 1
                                        && normalized.length() <= 3) {

                                normalized = "no";
                        }
                }

                if (normalized.equals("yes")
                                || normalized.equals("y")
                                || normalized.equals("yeah")
                                || normalized.equals("yep")
                                || normalized.equals("sure")
                                || normalized.equals("okay")
                                || normalized.equals("ok")
                                || normalized.equals("do it")
                                || normalized.equals("go ahead")) {

                        String pendingAction = conversationContext.getPendingAction();

                        String pendingDetails = conversationContext.getPendingDetails();

                        conversationContext.clearPendingAction();

                        if (pendingAction == null
                                        || pendingAction.isBlank()) {

                                return "There is no pending action.";
                        }

                        String[] parts = pendingDetails == null
                                        ? new String[0]
                                        : pendingDetails.split(
                                                        "\\|\\|\\|",
                                                        -1);

                        String target = parts.length > 0
                                        ? parts[0]
                                        : "";

                        String targetType = parts.length > 1
                                        ? parts[1]
                                        : "";

                        String location = parts.length > 2
                                        ? parts[2]
                                        : "";

                        String source = parts.length > 3
                                        ? parts[3]
                                        : "";

                        BuddyActionPlan confirmedPlan = new BuddyActionPlan(
                                        pendingAction,
                                        target,
                                        targetType,
                                        location,
                                        source,
                                        false);

                        return executePlan(
                                        confirmedPlan,
                                        "Confirmed: " + input);
                }

                if (normalized.equals("no")
                                || normalized.equals("n")
                                || normalized.equals("nope")
                                || normalized.equals("cancel")
                                || normalized.equals("stop")
                                || normalized.equals("don't")
                                || normalized.equals("do not")) {

                        conversationContext.clearPendingAction();

                        return "Okay, I cancelled that action.";
                }

                return "Please say yes to continue or no to cancel.";
        }

        // ============================================================
        // BUILD PENDING DETAILS
        // ============================================================

        private String createPendingDetails(
                        BuddyActionPlan plan) {

                return safe(plan.getTarget())
                                + "|||"
                                + safe(plan.getTargetType())
                                + "|||"
                                + safe(plan.getLocation())
                                + "|||"
                                + safe(plan.getSource());
        }

        // ============================================================
        // CONFIRMATION MESSAGE
        // ============================================================

        private String buildConfirmationMessage(
                        BuddyActionPlan plan) {

                String intent = safeLower(plan.getIntent());

                String target = safe(plan.getTarget());

                if (intent.equals("download")) {

                        if (target.isBlank()) {
                                return "Do you want me to download the file?";
                        }

                        return "Do you want me to download "
                                        + target
                                        + "?";
                }

                if (intent.equals("move")) {

                        return "Do you want me to move "
                                        + target
                                        + "?";
                }

                if (intent.equals("delete")) {

                        return "Do you want me to delete "
                                        + target
                                        + "?";
                }

                return "Do you want me to perform this action?";
        }

        // ============================================================
        // COMBINE PREVIOUS RESULTS + CONFIRMATION
        // ============================================================

        private String combineResultsAndConfirmation(
                        List<String> results,
                        String confirmation) {

                if (results == null
                                || results.isEmpty()) {

                        return confirmation;
                }

                List<String> all = new ArrayList<>(results);

                all.add(confirmation);

                return String.join(
                                System.lineSeparator(),
                                all);
        }

        // ============================================================
        // EXECUTE ONE PLAN
        // ============================================================

        private String executePlan(
                        BuddyActionPlan plan,
                        String originalInput) {

                String intent = safeLower(plan.getIntent());

                if (intent.equals("chat")
                                || intent.equals("unknown")) {

                        return buddyChat.chat(
                                        originalInput);
                }

                if (intent.equals("time")) {

                        String result = actionManager.getCurrentTime();

                        conversationContext.setLastAction(
                                        "Checked the current time");

                        conversationContext.setLastObject(
                                        "current time");

                        conversationContext.setLastLocation(
                                        null);

                        activityLogger.log(
                                        originalInput,
                                        result);

                        return result;
                }

                if (intent.equals("locate")) {
                        return executeLocatePlan(
                                        plan,
                                        originalInput);
                }

                if (intent.equals("open")) {
                        return executeOpenPlan(
                                        plan,
                                        originalInput);
                }

                if (intent.equals("close")) {
                        return executeClosePlan(
                                        plan,
                                        originalInput);
                }

                if (intent.equals("create")) {
                        return executeCreatePlan(
                                        plan,
                                        originalInput);
                }

                if (intent.equals("search")) {
                        return executeSearchPlan(
                                        plan,
                                        originalInput);
                }

                if (intent.equals("download")) {
                        return executeDownloadPlan(
                                        plan,
                                        originalInput);
                }

                if (intent.equals("move")) {
                        return executeMovePlan(
                                        plan,
                                        originalInput);
                }

                if (intent.equals("copy")) {
                        return executeCopyPlan(
                                        plan,
                                        originalInput);
                }

                if (intent.equals("rename")) {
                        return executeRenamePlan(
                                        plan,
                                        originalInput);
                }

                if (intent.equals("play")) {
                        return executeOpenPlan(
                                        plan,
                                        originalInput);
                }

                if (intent.equals("delete")) {
                        return executeDeletePlan(
                                        plan,
                                        originalInput);
                }

                // ========================================================
                // LIVE WEB SEARCH
                // ========================================================

                if (intent.equals("web_search")) {

                        return executeWebSearchPlan(
                                        plan,
                                        originalInput);
                }

                if (intent.equals("date")) {

                        return executeDatePlan(
                                        plan,
                                        originalInput);
                }

                return buddyChat.chat(
                                originalInput);
        }

        // ============================================================
        // LOCATE
        // ============================================================

        private String executeLocatePlan(
                        BuddyActionPlan plan,
                        String originalInput) {

                String target = safe(plan.getTarget());

                String contextObject = conversationContext.getLastObject();

                String contextLocation = conversationContext.getLastLocation();

                if (contextObject != null
                                && contextLocation != null
                                && !target.isBlank()
                                && contextObject.equalsIgnoreCase(target)) {

                        String result = target
                                        + " is located at "
                                        + contextLocation
                                        + ".";

                        activityLogger.log(
                                        originalInput,
                                        result);

                        conversationContext.setLastAction(
                                        "Located an item");

                        conversationContext.setLastObject(
                                        target);

                        conversationContext.setLastLocation(
                                        contextLocation);

                        return result;
                }

                String commonPath = resolveCommonLocation(target);

                if (commonPath != null
                                && Files.exists(Path.of(commonPath))) {

                        String result = target
                                        + " is located at "
                                        + commonPath
                                        + ".";

                        activityLogger.log(
                                        originalInput,
                                        result);

                        conversationContext.setLastAction(
                                        "Located a folder");

                        conversationContext.setLastObject(
                                        target);

                        conversationContext.setLastLocation(
                                        commonPath);

                        return result;
                }

                String location = safeLower(plan.getLocation());

                String path = resolveFolderPath(
                                target,
                                location);

                if (path != null
                                && Files.exists(Path.of(path))) {

                        String result = target
                                        + " is located at "
                                        + path
                                        + ".";

                        activityLogger.log(
                                        originalInput,
                                        result);

                        conversationContext.setLastAction(
                                        "Located an item");

                        conversationContext.setLastObject(
                                        target);

                        conversationContext.setLastLocation(
                                        path);

                        return result;
                }

                return "I could not find "
                                + target
                                + ".";
        }

        // ============================================================
        // DATE PLAN
        // ============================================================

        private String executeDatePlan(
                        BuddyActionPlan plan,
                        String originalInput) {

                String resolvedDate = safe(plan.getTarget());

                if (resolvedDate.isBlank()) {

                        return "I could not resolve the requested date.";
                }

                java.time.LocalDate date;

                try {

                        date = java.time.LocalDate.parse(
                                        resolvedDate);

                } catch (Exception ignored) {

                        return "I could not resolve the requested date: "
                                        + resolvedDate
                                        + ".";
                }

                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern(
                                "EEEE, MMMM d, yyyy");

                String result = "The requested date is "
                                + date.format(formatter)
                                + ".";

                conversationContext.setLastAction(
                                "date");

                conversationContext.setLastObject(
                                date.toString());

                conversationContext.setLastLocation(
                                null);

                activityLogger.log(
                                originalInput,
                                result);

                return result;
        }

        // ============================================================
        // OPEN
        // ============================================================

        private String executeOpenPlan(
                        BuddyActionPlan plan,
                        String originalInput) {

                String target = safe(plan.getTarget());

                String targetType = safeLower(plan.getTargetType());

                String location = safeLower(plan.getLocation());

                if (targetType.equals("file")
                                || targetType.equals("unknown")) {

                        String filePath = resolveReferencedFile(
                                        target,
                                        location);

                        if (filePath != null
                                        && Files.isRegularFile(
                                                        Path.of(filePath))) {

                                try {

                                        Desktop.getDesktop().open(
                                                        Path.of(filePath).toFile());

                                        activityLogger.log(
                                                        originalInput,
                                                        "Opened " + target);

                                        conversationContext.setLastAction(
                                                        "Opened a file");

                                        conversationContext.setLastObject(
                                                        target);

                                        conversationContext.setLastLocation(
                                                        filePath);

                                        return "Opening "
                                                        + target
                                                        + ".";

                                } catch (Exception e) {

                                        e.printStackTrace();

                                        return "I could not open "
                                                        + target
                                                        + ".";
                                }
                        }
                }

                if (targetType.equals("folder")) {

                        String folderPath = resolveReferencedFolder(
                                        target,
                                        location);

                        if (folderPath == null
                                        || !Files.isDirectory(
                                                        Path.of(folderPath))) {

                                return "I could not find the folder "
                                                + target
                                                + ".";
                        }

                        String result = fileManager.openFolderPath(
                                        folderPath);

                        activityLogger.log(
                                        originalInput,
                                        result);

                        conversationContext.setLastAction(
                                        "Opened a folder");

                        conversationContext.setLastObject(
                                        target);

                        conversationContext.setLastLocation(
                                        folderPath);

                        return result;
                }

                // --------------------------------------------------------
                // EXISTING APP / WEBSITE RESOLUTION
                // DO NOT CHANGE
                // --------------------------------------------------------

                ResolvedTarget resolvedTarget = targetResolver.resolve(target);

                if (resolvedTarget == null) {

                        return "I could not find "
                                        + target
                                        + ".";
                }

                String result;

                if (resolvedTarget.getType()
                                .equalsIgnoreCase("application")) {

                        result = actionManager.openApplication(
                                        resolvedTarget.getValue());

                } else if (resolvedTarget.getType()
                                .equalsIgnoreCase("website")) {

                        result = actionManager.openWebsite(
                                        resolvedTarget.getValue());

                } else if (resolvedTarget.getType()
                                .equalsIgnoreCase("windows_app")) {

                        result = actionManager.openWindowsApp(
                                        resolvedTarget.getValue());

                } else if (resolvedTarget.getType()
                                .equalsIgnoreCase("search")) {

                        result = actionManager.openWebsite(
                                        resolvedTarget.getValue());

                } else {

                        return "I could not find "
                                        + target
                                        + ".";
                }

                activityLogger.log(
                                originalInput,
                                result);

                conversationContext.setLastAction(
                                "Opened an item");

                conversationContext.setLastObject(
                                target);

                conversationContext.setLastLocation(
                                resolvedTarget.getValue());

                return result;
        }

        // ============================================================
        // LIVE WEB SEARCH
        // ============================================================

        private String buildWebSearchQuery(String input) {

                if (input == null || input.isBlank()) {
                        return "";
                }

                String query = input.trim()
                                .replaceAll("\\s+", " ")
                                .replaceAll("(?i)^[,\\s]+", "")
                                .replaceAll("(?i)^(?:okay|ok|alright|please|hey buddy|buddy)\\s*[, ]*", "")
                                .replaceAll("(?i)^(?:can you|could you|would you)\\s+", "")
                                .replaceAll("(?i)^(?:search(?: for)?|look up|lookup|google|find online|find on the web|browse for)\\s+",
                                                "")
                                .replaceAll("(?i)^the\\s+", "")
                                .trim();

                return query;
        }

        private String executeWebSearchPlan(
                        BuddyActionPlan plan,
                        String originalInput) {

                String query = buildWebSearchQuery(originalInput);

                if (query.isBlank()) {
                        query = safe(plan.getTarget());
                }

                System.out.println(
                                "Buddy is checking the latest information...");

                String result = webIntelligence.getCurrentInformation(
                                query);

                activityLogger.log(
                                "WEB SEARCH " + query,
                                result);

                conversationContext.setLastAction(
                                "web_search");

                conversationContext.setLastObject(
                                query);

                return result;
        }

        // ============================================================
        // CLOSE
        // ============================================================

        private String executeClosePlan(
                        BuddyActionPlan plan,
                        String originalInput) {

                String target = safe(plan.getTarget());

                String result = actionManager.closeWindowsApp(
                                target);

                activityLogger.log(
                                originalInput,
                                result);

                conversationContext.setLastAction(
                                "Closed an application");

                conversationContext.setLastObject(
                                target);

                conversationContext.setLastLocation(
                                null);

                return result;
        }

        // ============================================================
        // CREATE
        // ============================================================

        private String executeCreatePlan(
                        BuddyActionPlan plan,
                        String originalInput) {

                String target = safe(plan.getTarget());

                String targetType = safeLower(plan.getTargetType());

                String location = safeLower(plan.getLocation());

                if (target.isBlank()) {
                        return "What would you like me to create?";
                }

                if (targetType.equals("folder")) {

                        String parentPath = resolveCreationParent(
                                        location);

                        String result;

                        if (parentPath != null) {

                                result = fileManager.createFolderAt(
                                                parentPath,
                                                target);

                        } else {

                                result = fileManager.createFolder(
                                                target);
                        }

                        String actualLocation = determineCreatedLocation(
                                        target,
                                        parentPath);

                        updateContextAfterCreate(
                                        "Created a folder",
                                        target,
                                        actualLocation);

                        activityLogger.log(
                                        originalInput,
                                        result);

                        return result;
                }

                if (targetType.equals("file")) {

                        String parentPath = resolveCreationParent(
                                        location);

                        String result;

                        if (parentPath != null) {

                                result = fileManager.createFileAt(
                                                parentPath,
                                                target);

                        } else {

                                result = fileManager.createFile(
                                                target);
                        }

                        String actualLocation = determineCreatedLocation(
                                        target,
                                        parentPath);

                        updateContextAfterCreate(
                                        "Created a file",
                                        target,
                                        actualLocation);

                        activityLogger.log(
                                        originalInput,
                                        result);

                        return result;
                }

                return "I do not know how to create that yet.";
        }

        // ============================================================
        // UPDATE CONTEXT AFTER CREATE
        // ============================================================

        private void updateContextAfterCreate(
                        String action,
                        String target,
                        String actualLocation) {

                if (actualLocation != null
                                && !actualLocation.isBlank()) {

                        try {

                                Path createdPath = Path.of(actualLocation);

                                if (Files.exists(createdPath)) {

                                        updateContextForPath(
                                                        action,
                                                        createdPath);

                                        return;
                                }

                        } catch (Exception ignored) {
                        }
                }

                conversationContext.setLastAction(
                                action);

                conversationContext.setLastObject(
                                target);

                conversationContext.setLastLocation(
                                actualLocation);

                if (action.equalsIgnoreCase(
                                "Created a folder")) {

                        conversationContext.setLastFolderName(
                                        target);

                        conversationContext.setLastFileName(
                                        null);

                        conversationContext.setLastFilePath(
                                        null);

                } else {

                        conversationContext.setLastFileName(
                                        target);

                        conversationContext.setLastFilePath(
                                        actualLocation);
                }
        }

        // ============================================================
        // SEARCH
        // ============================================================

        private String executeSearchPlan(
                        BuddyActionPlan plan,
                        String originalInput) {

                String query = safe(plan.getTarget());

                if (query.isBlank()) {

                        return "What would you like me to search for?";
                }

                String result = actionManager.searchGoogle(
                                query);

                activityLogger.log(
                                originalInput,
                                result);

                conversationContext.setLastAction(
                                "Performed a web search");

                conversationContext.setLastObject(
                                query);

                conversationContext.setLastLocation(
                                "Google");

                return result;
        }

        // ============================================================
        // DOWNLOAD
        // ============================================================

        private String executeDownloadPlan(
                        BuddyActionPlan plan,
                        String originalInput) {

                String target = safe(plan.getTarget());

                String source = safe(plan.getSource());

                String location = safe(plan.getLocation());

                if ((location.isBlank()
                                || location.equalsIgnoreCase("none"))
                                && originalInput
                                                .toLowerCase()
                                                .contains(" in ")) {

                        location = originalInput.substring(
                                        originalInput
                                                        .toLowerCase()
                                                        .lastIndexOf(" in ")
                                                        + 4)
                                        .trim();
                }

                // ========================================================
                // DETERMINE DESTINATION
                // ========================================================

                String destination = null;

                if (!location.isBlank()
                                && !location.equalsIgnoreCase("none")) {

                        destination = resolveLocationPath(
                                        location);
                }

                if (destination == null
                                && !location.isBlank()
                                && !location.equalsIgnoreCase("none")) {

                        destination = resolveNamedFolder(
                                        location);
                }

                if (destination == null) {

                        String contextLocation = conversationContext.getLastLocation();

                        if (contextLocation != null
                                        && !contextLocation.isBlank()) {

                                try {

                                        Path contextPath = Path.of(contextLocation);

                                        if (Files.isDirectory(
                                                        contextPath)) {

                                                destination = contextPath
                                                                .toAbsolutePath()
                                                                .normalize()
                                                                .toString();
                                        }

                                } catch (Exception ignored) {
                                }
                        }
                }

                if (destination == null
                                || destination.isBlank()) {

                        return "I could not determine where to save the file.";
                }

                // ========================================================
                // DOWNLOAD
                // ========================================================

                try {

                        String result = fileDownloader.download(
                                        source,
                                        target,
                                        destination);

                        activityLogger.log(
                                        originalInput,
                                        result);

                        conversationContext.setLastAction(
                                        "Downloaded a file");

                        conversationContext.setLastObject(
                                        target);

                        conversationContext.setLastLocation(
                                        destination);

                        String downloadedPath = fileDownloader.getLastDownloadedPath();

                        if (downloadedPath != null
                                        && !downloadedPath.isBlank()) {

                                Path downloaded = Path.of(downloadedPath);

                                conversationContext.setLastFileName(
                                                downloaded.getFileName()
                                                                .toString());

                                conversationContext.setLastFilePath(
                                                downloaded
                                                                .toAbsolutePath()
                                                                .normalize()
                                                                .toString());

                                conversationContext.setLastObject(
                                                downloaded.getFileName()
                                                                .toString());

                                conversationContext.setLastLocation(
                                                downloaded.getParent()
                                                                .toAbsolutePath()
                                                                .normalize()
                                                                .toString());
                        }

                        return result;

                } catch (Exception e) {

                        e.printStackTrace();

                        return "I could not download "
                                        + target
                                        + ".";
                }
        }

        // ============================================================
        // RESOLVE NAMED FOLDER
        // ============================================================

        private String resolveNamedFolder(
                        String folderReference) {

                if (folderReference == null
                                || folderReference.isBlank()) {

                        return null;
                }

                String reference = cleanFolderReference(
                                folderReference);

                String rememberedName = conversationContext.getLastFolderName();

                String rememberedLocation = conversationContext.getLastLocation();

                if (rememberedName != null
                                && !rememberedName.isBlank()
                                && rememberedLocation != null
                                && !rememberedLocation.isBlank()) {

                        try {

                                Path rememberedPath = Path.of(
                                                rememberedLocation);

                                if (Files.isDirectory(
                                                rememberedPath)
                                                && looksLikeSameFolder(
                                                                reference,
                                                                rememberedName)) {

                                        return rememberedPath
                                                        .toAbsolutePath()
                                                        .normalize()
                                                        .toString();
                                }

                        } catch (Exception ignored) {
                        }
                }

                try {

                        String desktop = fileManager.getDesktopPath();

                        if (desktop == null
                                        || desktop.isBlank()) {

                                return null;
                        }

                        Path desktopPath = Path.of(desktop);

                        if (!Files.isDirectory(
                                        desktopPath)) {

                                return null;
                        }

                        try (var stream = Files.walk(
                                        desktopPath,
                                        3)) {

                                Path match = stream
                                                .filter(
                                                                Files::isDirectory)
                                                .filter(
                                                                p -> p.getFileName() != null)
                                                .filter(
                                                                p -> looksLikeSameFolder(
                                                                                p.getFileName()
                                                                                                .toString(),
                                                                                reference))
                                                .findFirst()
                                                .orElse(null);

                                if (match != null) {

                                        return match
                                                        .toAbsolutePath()
                                                        .normalize()
                                                        .toString();
                                }
                        }

                } catch (Exception ignored) {
                }

                return null;
        }

        private String cleanFolderReference(
                        String value) {

                return value
                                .replaceAll(
                                                "(?i)\\bfolder\\b",
                                                " ")
                                .replaceAll(
                                                "(?i)\\bthe\\b",
                                                " ")
                                .replaceAll(
                                                "(?i)\\bmy\\b",
                                                " ")
                                .trim()
                                .replaceAll(
                                                "\\s+",
                                                " ");
        }

        private boolean looksLikeSameFolder(
                        String first,
                        String second) {

                String a = cleanFolderReference(first)
                                .toLowerCase();

                String b = cleanFolderReference(second)
                                .toLowerCase();

                if (a.isBlank()
                                || b.isBlank()) {

                        return false;
                }

                if (a.equals(b)) {
                        return true;
                }

                for (String token : a.split("\\s+")) {

                        if (token.isBlank()) {
                                continue;
                        }

                        int distance = levenshtein(
                                        token,
                                        b);

                        int allowed = Math.max(
                                        1,
                                        Math.min(
                                                        3,
                                                        b.length() / 3));

                        if (distance <= allowed) {
                                return true;
                        }
                }

                int distance = levenshtein(a, b);

                int allowed = Math.max(
                                1,
                                Math.min(
                                                3,
                                                b.length() / 3));

                return distance <= allowed;
        }

        private int levenshtein(
                        String a,
                        String b) {

                int[] previous = new int[b.length() + 1];

                int[] current = new int[b.length() + 1];

                for (int j = 0; j <= b.length(); j++) {

                        previous[j] = j;
                }

                for (int i = 1; i <= a.length(); i++) {

                        current[0] = i;

                        for (int j = 1; j <= b.length(); j++) {

                                int insert = current[j - 1] + 1;

                                int delete = previous[j] + 1;

                                int replace = previous[j - 1]
                                                + (a.charAt(i - 1) == b.charAt(j - 1)
                                                                ? 0
                                                                : 1);

                                current[j] = Math.min(
                                                Math.min(
                                                                insert,
                                                                delete),
                                                replace);
                        }

                        int[] temp = previous;

                        previous = current;

                        current = temp;
                }

                return previous[b.length()];
        }

        // ============================================================
        // MOVE
        // ============================================================

        private String executeMovePlan(
                        BuddyActionPlan plan,
                        String originalInput) {

                String target = safe(plan.getTarget());

                String destinationLocation = safeLower(plan.getDestination());

                if (destinationLocation.isBlank()) {

                        destinationLocation = safeLower(
                                        plan.getLocation());
                }

                if (target.isBlank()) {
                        return "What would you like me to move?";
                }

                String sourcePath = findContextObjectPath(
                                target);

                if (sourcePath == null) {

                        sourcePath = findInCommonLocations(
                                        target);
                }

                if (sourcePath == null
                                || !Files.exists(
                                                Path.of(sourcePath))) {

                        return "I could not find "
                                        + target
                                        + ".";
                }

                String destinationBase = resolveLocationPath(
                                destinationLocation);

                if (destinationBase == null
                                || destinationBase.isBlank()) {

                        return "I could not determine the destination.";
                }

                try {

                        Path source = Path.of(sourcePath);

                        Path destinationFolder = Path.of(destinationBase);

                        if (!Files.isDirectory(
                                        destinationFolder)) {

                                return "The destination folder does not exist.";
                        }

                        Path finalDestination = destinationFolder.resolve(
                                        source.getFileName());

                        Files.move(
                                        source,
                                        finalDestination,
                                        StandardCopyOption.REPLACE_EXISTING);

                        String result = "Moved "
                                        + source.getFileName()
                                        + " to "
                                        + finalDestination
                                        + ".";

                        activityLogger.log(
                                        originalInput,
                                        result);

                        updateContextForPath(
                                        "Moved an item",
                                        finalDestination);

                        return result;

                } catch (Exception e) {

                        e.printStackTrace();

                        return "Sorry, I could not move "
                                        + target
                                        + ".";
                }
        }

        // ============================================================
        // COPY
        // ============================================================

        private String executeCopyPlan(
                        BuddyActionPlan plan,
                        String originalInput) {

                String target = safe(plan.getTarget());

                String destinationReference = safe(plan.getLocation());

                if (target.isBlank()) {
                        return "What would you like me to copy?";
                }

                String sourcePath = findContextObjectPath(
                                target);

                if (sourcePath == null) {

                        sourcePath = findInCommonLocations(
                                        target);
                }

                if (sourcePath == null
                                || !Files.exists(
                                                Path.of(sourcePath))) {

                        return "I could not find "
                                        + target
                                        + ".";
                }

                String destinationBase = resolveLocationPath(
                                destinationReference);

                if (destinationBase == null) {

                        destinationBase = resolveNamedFolder(
                                        destinationReference);
                }

                if (destinationBase == null
                                || destinationBase.isBlank()) {

                        return "I could not determine the destination.";
                }

                try {

                        Path source = Path.of(sourcePath);

                        Path destinationFolder = Path.of(destinationBase);

                        if (!Files.isDirectory(
                                        destinationFolder)) {

                                return "The destination folder does not exist.";
                        }

                        Path destination = destinationFolder.resolve(
                                        source.getFileName());

                        if (Files.isDirectory(
                                        source)) {

                                copyDirectory(
                                                source,
                                                destination);

                        } else {

                                Files.copy(
                                                source,
                                                destination,
                                                StandardCopyOption.REPLACE_EXISTING);
                        }

                        String result = "Copied "
                                        + source.getFileName()
                                        + " to "
                                        + destinationFolder
                                        + ".";

                        activityLogger.log(
                                        originalInput,
                                        result);

                        updateContextForPath(
                                        "Copied an item",
                                        destination);

                        return result;

                } catch (Exception e) {

                        e.printStackTrace();

                        return "I could not copy "
                                        + target
                                        + ".";
                }
        }

        private void copyDirectory(
                        Path source,
                        Path destination)
                        throws java.io.IOException {

                try (var stream = Files.walk(source)) {

                        List<Path> paths = stream.toList();

                        for (Path item : paths) {

                                Path relative = source.relativize(
                                                item);

                                Path target = destination.resolve(
                                                relative);

                                if (Files.isDirectory(
                                                item)) {

                                        Files.createDirectories(
                                                        target);

                                } else {

                                        Files.createDirectories(
                                                        target.getParent());

                                        Files.copy(
                                                        item,
                                                        target,
                                                        StandardCopyOption.REPLACE_EXISTING);
                                }
                        }
                }
        }

        // ============================================================
        // RENAME
        // ============================================================

        private String executeRenamePlan(
                        BuddyActionPlan plan,
                        String originalInput) {

                String target = safe(plan.getTarget());

                String newName = safe(plan.getLocation());

                if (target.isBlank()
                                || newName.isBlank()
                                || newName.equalsIgnoreCase("none")) {

                        return "Please tell me what you want to rename and the new name.";
                }

                String sourcePath = findContextObjectPath(
                                target);

                if (sourcePath == null) {

                        sourcePath = findInCommonLocations(
                                        target);
                }

                if (sourcePath == null
                                || !Files.exists(
                                                Path.of(sourcePath))) {

                        return "I could not find "
                                        + target
                                        + ".";
                }

                try {

                        Path source = Path.of(sourcePath);

                        Path destination = source.resolveSibling(
                                        newName);

                        Files.move(
                                        source,
                                        destination,
                                        StandardCopyOption.REPLACE_EXISTING);

                        String result = "Renamed "
                                        + source.getFileName()
                                        + " to "
                                        + destination.getFileName()
                                        + ".";

                        activityLogger.log(
                                        originalInput,
                                        result);

                        updateContextForPath(
                                        "Renamed an item",
                                        destination);

                        return result;

                } catch (Exception e) {

                        e.printStackTrace();

                        return "I could not rename "
                                        + target
                                        + ".";
                }
        }

        // ============================================================
        // UPDATE CONTEXT FROM REAL PATH
        // ============================================================

        private void updateContextForPath(
                        String action,
                        Path path) {

                Path absolute = path.toAbsolutePath()
                                .normalize();

                conversationContext.setLastAction(
                                action);

                conversationContext.setLastObject(
                                absolute.getFileName() == null
                                                ? absolute.toString()
                                                : absolute.getFileName().toString());

                conversationContext.setLastLocation(
                                absolute.toString());

                if (Files.isDirectory(
                                absolute)) {

                        conversationContext.setLastFolderName(
                                        absolute.getFileName()
                                                        .toString());

                        conversationContext.setLastFileName(
                                        null);

                        conversationContext.setLastFilePath(
                                        null);

                } else {

                        conversationContext.setLastFileName(
                                        absolute.getFileName()
                                                        .toString());

                        conversationContext.setLastFilePath(
                                        absolute.toString());
                }
        }

        // ============================================================
        // DELETE
        // ============================================================

        private String executeDeletePlan(
                        BuddyActionPlan plan,
                        String originalInput) {

                String target = safe(plan.getTarget());

                if (target.isBlank()) {
                        return "What would you like me to delete?";
                }

                String sourcePath = findContextObjectPath(
                                target);

                if (sourcePath == null) {

                        sourcePath = findInCommonLocations(
                                        target);
                }

                if (sourcePath == null
                                || !Files.exists(
                                                Path.of(sourcePath))) {

                        return "I could not find "
                                        + target
                                        + ".";
                }

                try {

                        Path path = Path.of(sourcePath);

                        deletePathRecursively(
                                        path);

                        String result = "Deleted "
                                        + target
                                        + ".";

                        activityLogger.log(
                                        originalInput,
                                        result);

                        conversationContext.setLastAction(
                                        "Deleted an item");

                        conversationContext.setLastObject(
                                        target);

                        conversationContext.setLastLocation(
                                        null);

                        conversationContext.setLastFileName(
                                        null);

                        conversationContext.setLastFilePath(
                                        null);

                        conversationContext.setLastFolderName(
                                        null);

                        return result;

                } catch (Exception e) {

                        e.printStackTrace();

                        return "I could not delete "
                                        + target
                                        + ". The item may be in use by another program.";
                }
        }

        private void deletePathRecursively(
                        Path path)
                        throws java.io.IOException {

                if (Files.isDirectory(path)) {

                        try (var stream = Files.walk(path)) {

                                List<Path> paths = stream
                                                .sorted(
                                                                Comparator.reverseOrder())
                                                .toList();

                                for (Path item : paths) {

                                        Files.deleteIfExists(
                                                        item);
                                }
                        }

                } else {

                        Files.deleteIfExists(
                                        path);
                }
        }

        // ============================================================
        // RESOLVE FILE REFERENCE
        // ============================================================

        private String resolveReferencedFile(
                        String target,
                        String location) {

                String contextPath = conversationContext.getLastFilePath();

                if (contextPath != null
                                && Files.isRegularFile(
                                                Path.of(contextPath))) {

                        return contextPath;
                }

                contextPath = findContextObjectPath(
                                target);

                if (contextPath != null
                                && Files.isRegularFile(
                                                Path.of(contextPath))) {

                        return contextPath;
                }

                String logicalPath = resolveFolderPath(
                                target,
                                location);

                if (logicalPath != null
                                && Files.isRegularFile(
                                                Path.of(logicalPath))) {

                        return logicalPath;
                }

                return findInCommonLocations(
                                target);
        }

        // ============================================================
        // RESOLVE FOLDER REFERENCE
        // ============================================================

        private String resolveReferencedFolder(
                        String target,
                        String location) {

                String contextPath = findContextObjectPath(
                                target);

                if (contextPath != null
                                && Files.isDirectory(
                                                Path.of(contextPath))) {

                        return contextPath;
                }

                String commonPath = resolveCommonLocation(
                                target);

                if (commonPath != null
                                && Files.isDirectory(
                                                Path.of(commonPath))) {

                        return commonPath;
                }

                String path = resolveFolderPath(
                                target,
                                location);

                if (path != null
                                && Files.isDirectory(
                                                Path.of(path))) {

                        return path;
                }

                return null;
        }

        // ============================================================
        // CONTEXT OBJECT PATH
        // ============================================================

        private String findContextObjectPath(
                        String target) {

                if (target == null
                                || target.isBlank()) {

                        return null;
                }

                String object = conversationContext.getLastObject();

                String location = conversationContext.getLastLocation();

                if (object == null
                                || location == null) {

                        return null;
                }

                if (object.equalsIgnoreCase(
                                target)) {

                        return location;
                }

                String normalizedTarget = target.toLowerCase();

                String normalizedObject = object.toLowerCase();

                if (normalizedTarget.contains("mp3")
                                && normalizedObject.contains("mp3")) {

                        return location;
                }

                if (normalizedTarget.contains("mp4")
                                && normalizedObject.contains("mp4")) {

                        return location;
                }

                if ((normalizedTarget.contains("it")
                                || normalizedTarget.contains("file"))
                                && !location.isBlank()) {

                        return location;
                }

                if (normalizedTarget.contains("photo")
                                && (normalizedObject.endsWith(".jpg")
                                                || normalizedObject.endsWith(".jpeg")
                                                || normalizedObject.endsWith(".png")
                                                || normalizedObject.endsWith(".webp"))) {

                        return location;
                }

                if (normalizedTarget.contains("image")
                                && (normalizedObject.endsWith(".jpg")
                                                || normalizedObject.endsWith(".jpeg")
                                                || normalizedObject.endsWith(".png")
                                                || normalizedObject.endsWith(".webp"))) {

                        return location;
                }

                return null;
        }

        // ============================================================
        // SEARCH COMMON LOCATIONS
        // ============================================================

        private String findInCommonLocations(
                        String target) {

                List<String> roots = new ArrayList<>();

                try {

                        roots.add(
                                        fileManager.getDesktopPath());

                } catch (Exception ignored) {
                }

                String home = System.getProperty(
                                "user.home");

                roots.add(
                                Path.of(
                                                home,
                                                "Downloads")
                                                .toString());

                roots.add(
                                Path.of(
                                                home,
                                                "Documents")
                                                .toString());

                roots.add(
                                Path.of(
                                                home,
                                                "Pictures")
                                                .toString());

                roots.add(
                                Path.of(
                                                home,
                                                "Music")
                                                .toString());

                roots.add(
                                Path.of(
                                                home,
                                                "Videos")
                                                .toString());

                for (String root : roots) {

                        if (root == null
                                        || root.isBlank()) {

                                continue;
                        }

                        try {

                                Path rootPath = Path.of(root);

                                if (!Files.exists(
                                                rootPath)) {

                                        continue;
                                }

                                try (var stream = Files.walk(
                                                rootPath,
                                                3)) {

                                        Path match = stream
                                                        .filter(
                                                                        Files::exists)
                                                        .filter(
                                                                        p -> p.getFileName() != null
                                                                                        && p.getFileName()
                                                                                                        .toString()
                                                                                                        .equalsIgnoreCase(
                                                                                                                        target))
                                                        .findFirst()
                                                        .orElse(null);

                                        if (match != null) {

                                                return match
                                                                .toAbsolutePath()
                                                                .normalize()
                                                                .toString();
                                        }
                                }

                        } catch (Exception ignored) {
                        }
                }

                return null;
        }

        // ============================================================
        // CREATION PARENT
        // ============================================================

        private String resolveCreationParent(
                        String location) {

                if (location != null
                                && !location.isBlank()
                                && !location.equalsIgnoreCase(
                                                "none")) {

                        String explicit = resolveLocationPath(
                                        location);

                        if (explicit != null
                                        && Files.isDirectory(
                                                        Path.of(explicit))) {

                                return explicit;
                        }
                }

                String contextLocation = conversationContext.getLastLocation();

                if (contextLocation != null
                                && !contextLocation.isBlank()
                                && Files.isDirectory(
                                                Path.of(contextLocation))) {

                        return contextLocation;
                }

                return null;
        }

        // ============================================================
        // CREATED LOCATION
        // ============================================================

        private String determineCreatedLocation(
                        String target,
                        String parentPath) {

                try {

                        if (parentPath != null
                                        && !parentPath.isBlank()) {

                                return Path.of(
                                                parentPath,
                                                target)
                                                .toAbsolutePath()
                                                .normalize()
                                                .toString();
                        }

                        return Path.of(
                                        System.getProperty(
                                                        "user.home"),
                                        target)
                                        .toAbsolutePath()
                                        .normalize()
                                        .toString();

                } catch (Exception e) {

                        return null;
                }
        }

        // ============================================================
        // FOLDER PATH
        // ============================================================

        private String resolveFolderPath(
                        String target,
                        String location) {

                if (target == null
                                || target.isBlank()) {

                        return null;
                }

                if (location == null
                                || location.isBlank()
                                || location.equalsIgnoreCase("none")) {

                        return null;
                }

                String base = resolveLocationPath(
                                location);

                if (base == null) {
                        return null;
                }

                return Path.of(
                                base,
                                target)
                                .toAbsolutePath()
                                .normalize()
                                .toString();
        }

        // ============================================================
        // LOCATION PATH
        // ============================================================

        private String resolveLocationPath(
                        String location) {

                if (location == null
                                || location.isBlank()
                                || location.equalsIgnoreCase("none")) {

                        return null;
                }

                switch (location.toLowerCase()) {

                        case "desktop":
                                return fileManager
                                                .getDesktopPath();

                        case "downloads":
                        case "download":
                                return Path.of(
                                                System.getProperty(
                                                                "user.home"),
                                                "Downloads")
                                                .toAbsolutePath()
                                                .normalize()
                                                .toString();

                        case "documents":
                        case "document":
                                return Path.of(
                                                System.getProperty(
                                                                "user.home"),
                                                "Documents")
                                                .toAbsolutePath()
                                                .normalize()
                                                .toString();

                        case "pictures":
                        case "picture":
                                return Path.of(
                                                System.getProperty(
                                                                "user.home"),
                                                "Pictures")
                                                .toAbsolutePath()
                                                .normalize()
                                                .toString();

                        case "music":
                                return Path.of(
                                                System.getProperty(
                                                                "user.home"),
                                                "Music")
                                                .toAbsolutePath()
                                                .normalize()
                                                .toString();

                        case "videos":
                        case "video":
                                return Path.of(
                                                System.getProperty(
                                                                "user.home"),
                                                "Videos")
                                                .toAbsolutePath()
                                                .normalize()
                                                .toString();

                        default:
                                return null;
                }
        }

        // ============================================================
        // COMMON LOCATION
        // ============================================================

        private String resolveCommonLocation(
                        String target) {

                if (target == null
                                || target.isBlank()) {

                        return null;
                }

                switch (target.toLowerCase()) {

                        case "desktop":
                                return fileManager
                                                .getDesktopPath();

                        case "downloads":
                        case "download":
                                return resolveLocationPath(
                                                "downloads");

                        case "documents":
                        case "document":
                                return resolveLocationPath(
                                                "documents");

                        case "pictures":
                        case "picture":
                                return resolveLocationPath(
                                                "pictures");

                        case "music":
                                return resolveLocationPath(
                                                "music");

                        case "videos":
                        case "video":
                                return resolveLocationPath(
                                                "videos");

                        default:
                                return null;
                }
        }

        // ============================================================
        // SAFE STRINGckage buddy.commands;

        // ============================================================

        private String safe(
                        String value) {

                return value == null
                                ? ""
                                : value.trim();
        }

        // ============================================================
        // SAFE LOWERCASE
        // ============================================================

        private String safeLower(
                        String value) {

                return safe(value)
                                .toLowerCase();
        }
}
