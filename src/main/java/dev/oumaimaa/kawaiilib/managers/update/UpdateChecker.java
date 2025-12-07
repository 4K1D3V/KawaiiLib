package dev.oumaimaa.kawaiilib.managers.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oumaimaa.kawaiilib.Bootstrap;
import dev.oumaimaa.kawaiilib.annotations.AutoUpdate;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

public final class UpdateChecker {

    private final Bootstrap plugin;
    private final AutoUpdate config;
    private final HttpClient httpClient;
    private String latestVersion;
    private String downloadUrl;

    public UpdateChecker(Bootstrap plugin, AutoUpdate config) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public CompletableFuture<Boolean> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String currentVersion = plugin.getDescription().getVersion();
                fetchLatestVersion();

                if (latestVersion != null && !latestVersion.equals(currentVersion)) {
                    plugin.getLogger().info("New version available: " + latestVersion +
                            " (Current: " + currentVersion + ")");

                    if (config.autoDownload()) {
                        downloadUpdate().join();
                    }

                    return true;
                }

                plugin.getLogger().info("You are running the latest version!");
                return false;

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
                return false;
            }
        });
    }

    private void fetchLatestVersion() throws Exception {
        switch (config.platform().toUpperCase()) {
            case "SPIGOT" -> fetchSpigotVersion();
            case "HANGAR" -> fetchHangarVersion();
            case "MODRINTH" -> fetchModrinthVersion();
            default -> throw new IllegalArgumentException("Unknown platform: " + config.platform());
        }
    }

    private void fetchSpigotVersion() throws Exception {
        String url = "https://api.spiget.org/v2/resources/" + config.resourceId() + "/versions/latest";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "KawaiiLib-UpdateChecker")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            latestVersion = json.get("name").getAsString();
            downloadUrl = "https://api.spiget.org/v2/resources/" + config.resourceId() + "/download";
        }
    }

    private void fetchHangarVersion() throws Exception {
        String url = "https://hangar.papermc.io/api/v1/projects/" + config.resourceId() + "/versions";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "KawaiiLib-UpdateChecker")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray versions = json.getAsJsonArray("result");

            if (!versions.isEmpty()) {
                JsonObject latest = versions.get(0).getAsJsonObject();
                latestVersion = latest.get("name").getAsString();

                JsonObject downloads = latest.getAsJsonObject("downloads");
                if (downloads.has("PAPER")) {
                    JsonObject paperDownload = downloads.getAsJsonObject("PAPER");
                    downloadUrl = paperDownload.get("downloadUrl").getAsString();
                }
            }
        }
    }

    private void fetchModrinthVersion() throws Exception {
        String url = "https://api.modrinth.com/v2/project/" + config.resourceId() + "/version";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "KawaiiLib-UpdateChecker")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();

            if (!versions.isEmpty()) {
                JsonObject latest = versions.get(0).getAsJsonObject();
                latestVersion = latest.get("version_number").getAsString();

                JsonArray files = latest.getAsJsonArray("files");
                if (!files.isEmpty()) {
                    JsonObject file = files.get(0).getAsJsonObject();
                    downloadUrl = file.get("url").getAsString();
                }
            }
        }
    }

    private @NotNull CompletableFuture<Void> downloadUpdate() {
        return CompletableFuture.runAsync(() -> {
            if (downloadUrl == null) {
                plugin.getLogger().warning("No download URL available");
                return;
            }

            try {
                plugin.getLogger().info("Downloading update from: " + downloadUrl);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .header("User-Agent", "KawaiiLib-UpdateChecker")
                        .GET()
                        .build();

                HttpResponse<InputStream> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream()
                );

                if (response.statusCode() == 200) {
                    Path updateFolder = plugin.getDataFolder().toPath().getParent().resolve("update");
                    Files.createDirectories(updateFolder);

                    Path updateFile = updateFolder.resolve(plugin.getName() + ".jar");
                    Files.copy(response.body(), updateFile, StandardCopyOption.REPLACE_EXISTING);

                    plugin.getLogger().info("Update downloaded successfully!");
                    plugin.getLogger().info("Restart the server to apply the update.");
                } else {
                    plugin.getLogger().warning("Failed to download update: HTTP " + response.statusCode());
                }

            } catch (Exception e) {
                plugin.getLogger().severe("Error downloading update: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}