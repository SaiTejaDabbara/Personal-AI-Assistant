package buddy.resolver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

public class WindowsLocationResolver {

    public String getDesktopPath() {

        try {

            Process process =
                    new ProcessBuilder(
                            "reg",
                            "query",
                            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders",
                            "/v",
                            "Desktop"
                    )
                            .redirectErrorStream(true)
                            .start();


            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream()
                            )
                    );


            String line;

            while (
                    (line = reader.readLine()) != null
            ) {

                line =
                        line.trim();


                if (
                        line.toLowerCase()
                                .startsWith("desktop")
                ) {

                    String[] parts =
                            line.split(
                                    "\\s{2,}"
                            );


                    if (
                            parts.length >= 3
                    ) {

                        String desktopPath =
                                parts[parts.length - 1]
                                        .trim();


                        desktopPath =
                                expandEnvironmentVariables(
                                        desktopPath
                                );


                        return Path.of(
                                        desktopPath
                                )
                                .toAbsolutePath()
                                .normalize()
                                .toString();
                    }
                }
            }


            process.waitFor();

        } catch (
                Exception e
        ) {

            System.out.println(
                    "Could not detect Windows Desktop location."
            );
        }


        // Fallback location

        return Path.of(
                        System.getProperty(
                                "user.home"
                        ),
                        "Desktop"
                )
                .toAbsolutePath()
                .normalize()
                .toString();
    }


    private String expandEnvironmentVariables(
            String path
    ) {

        String result =
                path;


        int start;


        while (
                (start = result.indexOf('%')) != -1
        ) {

            int end =
                    result.indexOf(
                            '%',
                            start + 1
                    );


            if (
                    end == -1
            ) {

                break;
            }


            String variable =
                    result.substring(
                            start + 1,
                            end
                    );


            String value =
                    System.getenv(
                            variable
                    );


            if (
                    value == null
            ) {

                break;
            }


            result =
                    result.substring(
                            0,
                            start
                    )
                            + value
                            + result.substring(
                            end + 1
                    );
        }


        return result;
    }
}