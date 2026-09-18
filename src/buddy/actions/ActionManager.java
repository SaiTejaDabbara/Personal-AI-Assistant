package buddy.actions;

import java.awt.Desktop;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import buddy.resolver.WindowsProcessResolver;

public class ActionManager {

    // ========================================
    // OPEN WEBSITE
    // ========================================
    public String openWebsite(String website) {
        try {
            Desktop.getDesktop().browse(new URI(website));
            return "Opening website...";
        } catch (Exception e) {
            return "Sorry, I could not open the website.";
        }
    }

    // ========================================
    // CLOSE WINDOWS APP
    // ========================================
    public String closeWindowsApp(String appName) {
        try {
            WindowsProcessResolver resolver = new WindowsProcessResolver();
            String processName = resolver.findProcess(appName);

            if (processName == null) {
                return "I could not find a running application named " + appName + ".";
            }

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "taskkill", "/IM", processName + ".exe", "/F"
            );

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return "Closing " + appName + "...";
            }

            return "I found " + appName + " but could not close it.";

        } catch (Exception e) {
            return "Sorry, I could not close " + appName + ".";
        }
    }

    // ========================================
    // OPEN SAVED APPLICATION
    // ========================================
    public String openApplication(String applicationPath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(applicationPath);
            processBuilder.start();
            return "Opening application...";
        } catch (Exception e) {
            return "Sorry, I could not open the application.";
        }
    }

    // ========================================
    // OPEN WINDOWS / MICROSOFT STORE APPLICATION
    // ========================================
    public String openWindowsApp(String appId) {
        try {
            new ProcessBuilder(
                    "cmd", "/c", "start", "", "shell:AppsFolder\\" + appId
            ).start();
            return "Opening application...";
        } catch (Exception e) {
            return "Sorry, I could not open the Windows application.";
        }
    }

    // ========================================
    // CLOSE APPLICATIONS
    // ========================================
    public String closeApplication(String application) {
        try {
            ProcessBuilder processBuilder;

            switch (application.toLowerCase()) {
                case "notepad":
                    processBuilder = new ProcessBuilder("taskkill", "/IM", "notepad.exe", "/F");
                    break;

                case "chrome":
                    processBuilder = new ProcessBuilder("taskkill", "/IM", "chrome.exe", "/F");
                    break;

                default:
                    return "I don't know how to close " + application + " yet.";
            }

            processBuilder.start();
            return "Closing " + application + "...";

        } catch (Exception e) {
            return "Sorry, I could not close " + application + ".";
        }
    }

    // ========================================
    // CURRENT TIME
    // ========================================
    public String getCurrentTime() {
        LocalTime time = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");
        return "The current time is " + time.format(formatter);
    }

    // ========================================
    // GOOGLE SEARCH
    // ========================================
    public String searchGoogle(String query) {
        try {
            String searchUrl = "https://www.google.com/search?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);

            Desktop.getDesktop().browse(new URI(searchUrl));
            return "Searching Google for " + query;

        } catch (Exception e) {
            return "Sorry, I could not search Google.";
        }
    }

    // ========================================
    // YOUTUBE SEARCH
    // ========================================
    public String searchYouTube(String query) {
        try {
            String searchUrl = "https://www.youtube.com/results?search_query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);

            Desktop.getDesktop().browse(new URI(searchUrl));
            return "Searching YouTube for " + query;

        } catch (Exception e) {
            return "Sorry, I could not search YouTube.";
        }
    }

    // ========================================
    // LINKEDIN SEARCH
    // ========================================
    public String searchLinkedIn(String query) {
        try {
            String searchUrl = "https://www.linkedin.com/search/results/people/?keywords="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);

            Desktop.getDesktop().browse(new URI(searchUrl));
            return "Searching LinkedIn for " + query;

        } catch (Exception e) {
            return "Sorry, I could not search LinkedIn.";
        }
    }

    // ========================================
    // SEARCH ON PLATFORM
    // ========================================
    public String searchOnPlatform(String platform, String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl;

            switch (platform.toLowerCase()) {
                case "google":
                    searchUrl = "https://www.google.com/search?q=" + encodedQuery;
                    break;

                case "youtube":
                    searchUrl = "https://www.youtube.com/results?search_query=" + encodedQuery;
                    break;

                case "linkedin":
                    searchUrl = "https://www.linkedin.com/search/results/people/?keywords=" + encodedQuery;
                    break;

                default:
                    return "I can currently search on Google, YouTube, and LinkedIn. "
                            + "I don't know how to search on " + platform + " yet.";
            }

            Desktop.getDesktop().browse(new URI(searchUrl));
            return "Searching " + platform + " for " + query;

        } catch (Exception e) {
            return "Sorry, I could not search " + platform + ".";
        }
    }
}