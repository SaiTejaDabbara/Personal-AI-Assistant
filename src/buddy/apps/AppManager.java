package buddy.apps;

import java.util.HashMap;
import java.util.Map;
import buddy.memory.MemoryManager;

public class AppManager {

    private Map<String, String> websites;
    private Map<String, String> applications;
    private MemoryManager memoryManager;


    public AppManager() {

        websites = new HashMap<>();
        applications = new HashMap<>();
        memoryManager = new MemoryManager();

        // ========================================
        // DEFAULT WEBSITES
        // ========================================

        websites.put(
                "chatgpt",
                "https://chatgpt.com"
        );

        websites.put(
                "google",
                "https://www.google.com"
        );

        websites.put(
                "youtube",
                "https://www.youtube.com"
        );

        websites.put(
                "linkedin",
                "https://www.linkedin.com"
        );


        // ========================================
        // DEFAULT APPLICATIONS
        // ========================================

        applications.put(
                "notepad",
                "notepad.exe"
        );

        applications.put(
                "chrome",
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"
        );
        // ========================================
// LOAD SAVED MEMORY
// ========================================

        websites.putAll(
                memoryManager.loadWebsites()
        );

        applications.putAll(
                memoryManager.loadApplications()
        );
    }


    // ========================================
    // WEBSITE METHODS
    // ========================================

    public boolean isWebsite(String name) {

        return websites.containsKey(
                name.toLowerCase()
        );
    }


    public String getWebsite(String name) {

        return websites.get(
                name.toLowerCase()
        );
    }


    // ========================================
    // APPLICATION METHODS
    // ========================================

    public boolean isApplication(String name) {

        return applications.containsKey(
                name.toLowerCase()
        );
    }


    public String getApplicationPath(String name) {

        return applications.get(
                name.toLowerCase()
        );
    }


    // ========================================
    // ADD NEW WEBSITE
    // ========================================

    public void addWebsite(
            String name,
            String url
    ) {

        name = name.toLowerCase();

        websites.put(
                name,
                url
        );

        memoryManager.saveWebsite(
                name,
                url
        );
    }


    // ========================================
    // ADD NEW APPLICATION
    // ========================================

    public void addApplication(
            String name,
            String path
    ) {

        name = name.toLowerCase();

        applications.put(
                name,
                path
        );

        memoryManager.saveApplication(
                name,
                path
        );
    }


    // ========================================
    // GET ALL WEBSITE NAMES
    // ========================================

    public Iterable<String> getWebsiteNames() {

        return websites.keySet();
    }


    // ========================================
    // GET ALL APPLICATION NAMES
    // ========================================

    public Iterable<String> getApplicationNames() {

        return applications.keySet();
    }
}