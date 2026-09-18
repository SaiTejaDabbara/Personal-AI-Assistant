package buddy.resolver;

import buddy.apps.AppManager;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TargetResolver {

    private final AppManager appManager;
    private final WindowsAppResolver windowsAppResolver;

    public TargetResolver(AppManager appManager) {
        this.appManager = appManager;
        this.windowsAppResolver = new WindowsAppResolver();
    }

    public ResolvedTarget resolve(String target) {

        if (target == null || target.trim().isEmpty()) {
            return new ResolvedTarget("unknown", "", "");
        }

        String cleanTarget = target.toLowerCase().trim();

        // 1. Known application
        if (appManager.isApplication(cleanTarget)) {
            return new ResolvedTarget(
                    "application",
                    cleanTarget,
                    appManager.getApplicationPath(cleanTarget)
            );
        }

        // 2. Saved / known website
        if (appManager.isWebsite(cleanTarget)) {
            return new ResolvedTarget(
                    "website",
                    cleanTarget,
                    appManager.getWebsite(cleanTarget)
            );
        }

        // 3. Dynamic Windows Application Search
        String windowsAppId = windowsAppResolver.findInstalledApp(cleanTarget);
        if (windowsAppId != null) {
            return new ResolvedTarget(
                    "windows_app",
                    cleanTarget,
                    windowsAppId
            );
        }

        // 4. Unknown target: Search for official website
        String query = cleanTarget + " official website";
        String searchUrl = "https://www.google.com/search?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);

        return new ResolvedTarget("search", cleanTarget, searchUrl);
    }
}