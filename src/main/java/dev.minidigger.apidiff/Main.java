package dev.minidigger.apidiff;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static jdk.javadoc.internal.tool.Main.execute;

public class Main {
    private static final URI API_URL = URI.create("https://fill.papermc.io/graphql");
    // language=graphql
    private static final String VERSION_REQUEST = """
             {
                 project(key: "paper") {
                     versions(first: 100, orderBy: { direction: ASC }) {
                         nodes {
                            family {
                                key
                            }
                            key
                         }
                     }
                 }
             }
            """.stripIndent()
            .replace("\n", "\\n")
            .replace("\"", "\\\"");


    public static void main(String[] args) throws Exception {
        Main main = new Main();
        ApiDiffer apiDiffer = new ApiDiffer();
        SourceFetcher sourceFetcher = new SourceFetcher();

        List<String> versions = getVersions(main, sourceFetcher);

        SinceGenerator sinceGenerator = new SinceGenerator(versions, apiDiffer);
        HtmlGenerator htmlGenerator = new HtmlGenerator(apiDiffer);
        // generate api-export json
        for (String version : versions) {
            main.generateApiExport(version);
        }

        for (int i = 0; i < versions.size() - 1; i++) {
            apiDiffer.diff(versions.get(i), versions.get(i + 1), Path.of("output/raw/paper-api-diff-" + versions.get(i) + "-" + versions.get(i + 1) + ".json"));
            htmlGenerator.generateDiff(versions.get(i), versions.get(i + 1));
        }

//        main.generateApiExport("1.21.3");
//        main.generateApiExport("1.21.4");
//        apiDiffer.diff("1.21.3", "1.21.4", Path.of("output/raw/paper-api-diff-1.21.3-1.21.4.json"));
//        htmlGenerator.generateDiff("1.21.3", "1.21.4");

        htmlGenerator.generateSince(versions, sinceGenerator.generate());
        htmlGenerator.generateIndex();
    }

    private static @NonNull List<String> getVersions(Main main, SourceFetcher sourceFetcher) throws IOException, InterruptedException {
        List<String> versions;

        boolean automatic = true;
        if (automatic) {
            var versionsMap = main.fetchVersions();
            for (var it = versionsMap.entrySet().iterator(); it.hasNext(); ) {
                var versionFamily = it.next();
                var family = versionFamily.getKey();

                versionFamily.getValue().removeIf(version -> {
                    try {
                        return version.contains("pre") || version.contains("rc") || !sourceFetcher.fetchSourcesJar(family, version);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                if (versionFamily.getValue().isEmpty()) {
                    it.remove();
                }
            }

            versions = versionsMap.values().stream()
                    .flatMap(Collection::stream)
                    .toList();
        } else {
            versions = List.of("1.21.3", "1.21.4");
            versions.forEach(version -> {
                try {
                    sourceFetcher.fetchSourcesJar(version, version);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return versions;
    }

    public @NonNull Map<String, List<String>> fetchVersions() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(API_URL)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"" + VERSION_REQUEST + "\"}"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var code = response.statusCode();

            if (code > 299 || code < 200) {
                throw new RuntimeException("Could not connect to " + API_URL + " due to: " + response.body());
            }

            var json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (json.has("errors")) {
                throw new RuntimeException("Couldn't fetch versions from " + API_URL + ", reason: " + json.get("errors"));
            }
            return json.getAsJsonObject("data")
                    .getAsJsonObject("project")
                    .getAsJsonObject("versions")
                    .getAsJsonArray("nodes")
                    .asList()
                    .stream()
                    .skip(2) // skip 1.7.10 and 1.8.8
                    .map(JsonElement::getAsJsonObject)
                    .collect(Collectors.groupingBy(
                            v -> v.getAsJsonObject("family").get("key").getAsString(),
                            LinkedHashMap::new,
                            Collectors.mapping(
                                    v -> v.get("key").getAsString(),
                                    Collectors.toCollection(ArrayList::new)
                            )
                    ));
        }
    }

    public void generateApiExport(String version) {
        // TODO add hash check to prevent rerunning
        String packages = "com.destroystokyo.paper:org.bukkit:org.spigotmc";
        if (Files.isDirectory(Path.of("sources/paper-api-" + version + "/io"))) {
            packages += ":io.papermc.paper";
        }
        execute("--ignore-source-errors", "-public", "-quiet", "-doclet", "dev.minidigger.apidiff.ApiExportDoclet", "--output-file", "output/raw/paper-api-" + version + ".json", "--mc-version", version, "-sourcepath", "sources/paper-api-" + version, "-subpackages", packages);
    }
}
