package buddy.resolver;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class WindowsProcessResolver {

    public String findProcess(String appName) {

        try {

            appName = appName
                    .toLowerCase()
                    .trim()
                    .replace(".exe", "");

            String safeAppName =
                    appName.replace("'", "''");


            // ========================================
            // 1. SEARCH BY PROCESS NAME
            // ========================================

            String processName =
                    runPowerShell(
                            "Get-Process | "
                                    + "Where-Object { "
                                    + "$_.ProcessName -like '*"
                                    + safeAppName
                                    + "*' "
                                    + "} | "
                                    + "Select-Object -First 1 "
                                    + "-ExpandProperty ProcessName"
                    );


            if (processName != null) {

                System.out.println(
                        "Found by process name: "
                                + processName
                );

                return processName;
            }


            // ========================================
            // 2. SEARCH BY WINDOW TITLE
            // ========================================

            String command =
                    "Get-Process | "
                            + "Where-Object { "
                            + "$_.MainWindowTitle -ne '' "
                            + "-and "
                            + "$_.MainWindowTitle -like '*"
                            + safeAppName
                            + "*' "
                            + "} | "
                            + "Select-Object -First 1 "
                            + "-ExpandProperty ProcessName";


            processName =
                    runPowerShell(command);


            if (processName != null) {

                System.out.println(
                        "Found by window title: "
                                + processName
                );

                return processName;
            }


            System.out.println(
                    "No matching process found."
            );


            return null;

        } catch (Exception e) {

            System.out.println(
                    "Resolver error: "
                            + e.getMessage()
            );

            return null;
        }
    }


    // ========================================
    // RUN POWERSHELL
    // ========================================

    private String runPowerShell(
            String command
    ) {

        try {

            ProcessBuilder processBuilder =
                    new ProcessBuilder(
                            "powershell",
                            "-NoProfile",
                            "-Command",
                            command
                    );


            processBuilder.redirectErrorStream(
                    true
            );


            Process process =
                    processBuilder.start();


            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream()
                            )
                    );


            String line;


            while ((line = reader.readLine()) != null) {

                line =
                        line.trim();


                if (!line.isEmpty()) {

                    return line;
                }
            }


            process.waitFor();


            return null;

        } catch (Exception e) {

            return null;
        }
    }
}