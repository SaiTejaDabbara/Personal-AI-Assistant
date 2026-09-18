package buddy.web;

public class WebIntelligence {

    private final WebSearchManager webSearchManager;

    public WebIntelligence() {

        webSearchManager =
                new WebSearchManager();
    }


    // ============================================================
    // WEB INFORMATION
    // ============================================================

    public String getCurrentInformation(
            String userQuestion
    ) {

        if (
                userQuestion == null
                        || userQuestion.isBlank()
        ) {

            return "I need something to search for.";
        }


        long startTime =
                System.currentTimeMillis();


        System.out.println(
                "Searching the web..."
        );


        String searchResults =
                webSearchManager.searchNews(
                        userQuestion
                );


        long searchTime =
                System.currentTimeMillis()
                        - startTime;


        System.out.println(
                "Web search time: "
                        + searchTime
                        + " ms"
        );


        if (
                searchResults == null
                        || searchResults.isBlank()
        ) {

            return "I could not retrieve information from the web right now.";
        }


        if (
                searchResults.startsWith(
                        "I could not"
                )
        ) {

            return searchResults;
        }


        /*
         * WebSearchManager already provides the current
         * information from the web.
         *
         * Do NOT send the results through BuddyChat here.
         *
         * This avoids a second Ollama request and makes
         * current-information searches much faster.
         */

        return searchResults;
    }
}