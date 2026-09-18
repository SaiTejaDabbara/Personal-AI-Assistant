package buddy.resolver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class WindowsAppResolver {

    private final Map<String, String> installedApps = new HashMap<>();

    public WindowsAppResolver() {
        loadInstalledApps();
    }

    // ========================================
    // LOAD WINDOWS APPS ONCE
    // ========================================
    private void loadInstalledApps() {

        try {

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "Get-StartApps | ForEach-Object { $_.Name + '|' + $_.AppID }"
            );

            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(process.getInputStream())
                    );

            String line;

            while ((line = reader.readLine()) != null) {

                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                int separator = line.indexOf('|');

                if (separator == -1) {
                    continue;
                }

                String name =
                        line.substring(0, separator)
                                .trim()
                                .toLowerCase();

                String appId =
                        line.substring(separator + 1)
                                .trim();

                if (!name.isEmpty() && !appId.isEmpty()) {
                    installedApps.putIfAbsent(name, appId);
                }
            }

            process.waitFor();

            System.out.println(
                    "Loaded " + installedApps.size() + " Windows applications."
            );

        } catch (Exception e) {

            System.out.println(
                    "Could not load Windows applications."
            );
        }
    }

    // ========================================
    // FIND INSTALLED APPLICATION
    // ========================================
    public String findInstalledApp(String appName) {

        if (appName == null || appName.trim().isEmpty()) {
            return null;
        }

        String cleanName =
                appName.toLowerCase().trim();

        // Exact match first
        String exact =
                installedApps.get(cleanName);

        if (exact != null) {
            return exact;
        }

        // Partial match
        for (Map.Entry<String, String> entry :
                installedApps.entrySet()) {

            if (entry.getKey().contains(cleanName)) {
                return entry.getValue();
            }
        }

        return null;
    }

    // ========================================
    // REFRESH APPLICATION CACHE
    // ========================================
    public void refresh() {
        installedApps.clear();
        loadInstalledApps();
    }
}