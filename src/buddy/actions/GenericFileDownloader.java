package buddy.actions;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ThreadLocalRandom;

public class GenericFileDownloader {

    private final HttpClient httpClient;

    private String lastDownloadedPath;

    public GenericFileDownloader() {
        httpClient = HttpClient.newBuilder().build();
    }

    /**
     * Downloads a file into the requested destination folder.
     *
     * If the user asks for a random mp3/song/music file without a URL,
     * a legal public sample music file is selected from SoundHelix.
     */
    public String download(
            String source,
            String target,
            String destinationFolder) throws Exception {

        lastDownloadedPath = null;

        String requestedSource =
                source == null ? "" : source.trim();

        String requestedTarget =
                target == null ? "" : target.trim();

        String destination =
                destinationFolder == null
                        ? ""
                        : destinationFolder.trim();

        if (destination.isBlank()) {
            throw new IllegalArgumentException(
                    "Download destination is empty.");
        }

        Path destinationPath = Path.of(destination)
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(destinationPath);

        String effectiveSource = requestedSource;

        // ------------------------------------------------------------
        // Random music request
        // ------------------------------------------------------------

        if (effectiveSource.isBlank()
                && isRandomMusicRequest(requestedTarget)) {

            int number =
                    ThreadLocalRandom.current().nextInt(1, 17);

            effectiveSource =
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-"
                            + number
                            + ".mp3";
        }

        if (effectiveSource.isBlank()) {
            throw new IllegalArgumentException(
                    "I need a download source or URL for that file.");
        }

        URI uri;

        try {
            uri = URI.create(effectiveSource);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "The download source is not a valid URL.", e);
        }

        String scheme = uri.getScheme();

        // Never call equalsIgnoreCase() on a null scheme.
        if (!"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme)) {

            throw new IllegalArgumentException(
                    "The download source must be an HTTP or HTTPS URL.");
        }

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(uri)
                        .header("User-Agent", "Buddy/1.0")
                        .GET()
                        .build();

        HttpResponse<byte[]> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            throw new IOException(
                    "Download failed with HTTP status "
                            + response.statusCode()
                            + ".");
        }

        byte[] data = response.body();

        if (data == null || data.length == 0) {
            throw new IOException("The downloaded file is empty.");
        }

        String fileName = determineFileName(
                uri,
                response,
                requestedTarget);

        fileName = sanitizeFileName(fileName);

        Path finalPath = uniquePath(
                destinationPath,
                fileName);

        Files.write(
                finalPath,
                data,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);

        lastDownloadedPath = finalPath
                .toAbsolutePath()
                .normalize()
                .toString();

        return "Downloaded "
                + finalPath.getFileName()
                + " to "
                + finalPath.getParent()
                + ".";
    }

    public String getLastDownloadedPath() {
        return lastDownloadedPath;
    }

    private boolean isRandomMusicRequest(String target) {

        if (target == null || target.isBlank()) {
            return false;
        }

        String value = target.toLowerCase();

        boolean random = value.contains("random");

        boolean music =
                value.contains("mp3")
                        || value.contains("song")
                        || value.contains("music")
                        || value.contains("audio");

        return random && music;
    }

    private String determineFileName(
            URI uri,
            HttpResponse<byte[]> response,
            String target) {

        // ------------------------------------------------------------
        // Content-Disposition filename
        // ------------------------------------------------------------

        String contentDisposition =
                response.headers()
                        .firstValue("Content-Disposition")
                        .orElse("");

        String dispositionName =
                extractFileNameFromContentDisposition(
                        contentDisposition);

        if (!dispositionName.isBlank()) {
            return dispositionName;
        }

        // ------------------------------------------------------------
        // URL filename
        // ------------------------------------------------------------

        String path = uri.getPath();

        if (path != null && !path.isBlank()) {

            int slash = path.lastIndexOf('/');

            String name =
                    slash >= 0
                            ? path.substring(slash + 1)
                            : path;

            if (!name.isBlank()
                    && !name.equals("1200")) {

                return name;
            }
        }

        // ------------------------------------------------------------
        // Target fallback
        // ------------------------------------------------------------

        String cleanedTarget =
                sanitizeFileName(
                        target == null ? "download" : target);

        String lower =
                cleanedTarget.toLowerCase();

        if (lower.endsWith(".mp3")
                || lower.endsWith(".wav")
                || lower.endsWith(".mp4")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp")
                || lower.endsWith(".pdf")
                || lower.endsWith(".txt")
                || lower.endsWith(".zip")) {

            return cleanedTarget;
        }

        String contentType =
                response.headers()
                        .firstValue("Content-Type")
                        .orElse("")
                        .toLowerCase();

        if (contentType.contains("audio/mpeg")) {
            return "random-song.mp3";
        }

        if (contentType.contains("audio/wav")
                || contentType.contains("audio/x-wav")) {
            return "random-audio.wav";
        }

        if (contentType.contains("video/mp4")) {
            return "download.mp4";
        }

        if (contentType.contains("image/jpeg")) {
            return "download.jpg";
        }

        if (contentType.contains("image/png")) {
            return "download.png";
        }

        if (contentType.contains("image/webp")) {
            return "download.webp";
        }

        if (contentType.contains("application/pdf")) {
            return "download.pdf";
        }

        return "download.bin";
    }

    private String extractFileNameFromContentDisposition(
            String header) {

        if (header == null || header.isBlank()) {
            return "";
        }

        String lower = header.toLowerCase();
        int index = lower.indexOf("filename=");

        if (index < 0) {
            return "";
        }

        String value =
                header.substring(index + "filename=".length())
                        .trim();

        if (value.startsWith("\"")
                && value.endsWith("\"")
                && value.length() >= 2) {

            value = value.substring(1, value.length() - 1);
        }

        int semicolon = value.indexOf(';');

        if (semicolon >= 0) {
            value = value.substring(0, semicolon).trim();
        }

        return value;
    }

    private String sanitizeFileName(String name) {

        if (name == null || name.isBlank()) {
            return "download.bin";
        }

        String sanitized =
                name.replaceAll("[\\\\/:*?\"<>|]", "_")
                        .trim();

        sanitized =
                sanitized.replaceAll("\\s+", " ");

        if (sanitized.isBlank()
                || sanitized.equals(".")
                || sanitized.equals("..")) {

            return "download.bin";
        }

        return sanitized;
    }

    private Path uniquePath(
            Path folder,
            String fileName) {

        Path first = folder.resolve(fileName);

        if (!Files.exists(first)) {
            return first;
        }

        String base = fileName;
        String extension = "";

        int dot = fileName.lastIndexOf('.');

        if (dot > 0) {
            base = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }

        int counter = 2;

        while (true) {

            Path candidate = folder.resolve(
                    base + " (" + counter + ")" + extension);

            if (!Files.exists(candidate)) {
                return candidate;
            }

            counter++;
        }
    }
}
