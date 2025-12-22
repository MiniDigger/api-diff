package dev.minidigger.apidiff;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        Map<String, List<String>> versionsMap;
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
            if (json.has("errors"))
                throw new RuntimeException("Couldn't fetch versions from " + API_URL + ", reason: " + json.get("errors"));
            versionsMap =
                    json.getAsJsonObject("data")
                            .getAsJsonObject("project")
                            .getAsJsonObject("versions")
                            .getAsJsonArray("nodes")
                            .asList()
                            .stream()
                            .skip(2) // skip 1.7.10 and 1.8.8
                            .map(JsonElement::getAsJsonObject)
                            // this version doesn't have a sources jar
                            .filter(v -> !v.get("key").getAsString().equals("1.13-pre7"))
                            .collect(Collectors.groupingBy(
                                    v -> v.getAsJsonObject("family").get("key").getAsString(),
                                    LinkedHashMap::new,
                                    Collectors.mapping(
                                            v -> v.get("key").getAsString(),
                                            Collectors.toCollection(ArrayList::new)
                                    )
                            ));

        }

        Main main = new Main();
        ApiDiffer apiDiffer = new ApiDiffer();

        for (var it = versionsMap.entrySet().iterator(); it.hasNext(); ) {
            var versionFamily = it.next();
            var family = versionFamily.getKey();

            versionFamily.getValue().removeIf(version ->
                    {
                        try {
                            return !main.fetchSourcesJar(family, version);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
            );

            if (versionFamily.getValue().isEmpty()) {
                it.remove();
            }
        }

        var versions = versionsMap.values().stream()
                .flatMap(Collection::stream)
                .toList();

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

    public void generateApiExport(String version) {
        // TODO add hash check to prevent rerunning
        String packages = "com.destroystokyo.paper:org.bukkit:org.spigotmc";
        if (Files.isDirectory(Path.of("sources/paper-api-" + version + "/io"))) {
            packages += ":io.papermc.paper";
        }
        execute("--ignore-source-errors", "-public", "-quiet", "-doclet", "dev.minidigger.apidiff.ApiExportDoclet", "--output-file", "output/raw/paper-api-" + version + ".json", "--mc-version", version, "-sourcepath", "sources/paper-api-" + version, "-subpackages", packages);
    }

    public boolean fetchSourcesJar(String family, String version) throws Exception {
        // TODO add hash check to prevent redownloading
        System.out.println("Fetching sources for " + version);
        var parts = family.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        String group = "io/papermc";
        // hopefully this doesn't break with new versioning system
        if (major < 26 && minor < 17)
            group = "com/destroystokyo";

        String metadataUrl = "https://repo.papermc.io/repository/maven-public/" + group + "/paper/paper-api/" + version.replace(".0", ".") + "-R0.1-SNAPSHOT/maven-metadata.xml";
        String snapshotVersion = getLatestSnapshotVersion(metadataUrl);
        if (snapshotVersion == null) {
            System.err.println("Could not find snapshot version for " + version);
            return false;
        }
        String sourcesUrl = "https://repo.papermc.io/repository/maven-public/" + group + "/paper/paper-api/" + version.replace(".0", ".") + "-R0.1-SNAPSHOT/paper-api-" + snapshotVersion + "-sources.jar";
        downloadAndExtractSourcesToDisk(sourcesUrl, Path.of("sources/paper-api-" + version));
        return true;
    }

    public String getLatestSnapshotVersion(String metadataUrl) throws Exception {
        URL url = new URI(metadataUrl).toURL();
        URLConnection urlConnection = url.openConnection();
        if (urlConnection instanceof HttpsURLConnection httpsURLConnection) {
            httpsURLConnection.setRequestProperty("User-Agent", "api-diff/1.0 <https://github.com/MiniDigger/api-diff/>");
        }
        try (InputStream inputStream = urlConnection.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            Element root = document.getDocumentElement();
            NodeList versioning = root.getElementsByTagName("versioning");
            if (versioning.getLength() > 0) {
                Element versioningElement = (Element) versioning.item(0);
                NodeList snapshotVersions = versioningElement.getElementsByTagName("snapshotVersions");
                if (snapshotVersions.getLength() > 0) {
                    Element snapshotVersionsElement = (Element) snapshotVersions.item(0);
                    NodeList snapshotVersionList = snapshotVersionsElement.getElementsByTagName("snapshotVersion");
                    for (int i = 0; i < snapshotVersionList.getLength(); i++) {
                        Element snapshotVersion = (Element) snapshotVersionList.item(i);
                        String extension = snapshotVersion.getElementsByTagName("extension").item(0).getTextContent();
                        NodeList classifierNodes = snapshotVersion.getElementsByTagName("classifier");
                        String classifier = classifierNodes.getLength() > 0 ? classifierNodes.item(0).getTextContent() : "";

                        if ("jar".equals(extension) && "sources".equals(classifier)) {
                            return snapshotVersion.getElementsByTagName("value").item(0).getTextContent();
                        }
                    }
                }
            }
        }
        return null;
    }

    public void downloadAndExtractSourcesToDisk(String sourcesUrl, Path outputDir) throws Exception {
        URL url = new URI(sourcesUrl).toURL();
        try (ZipInputStream zipInputStream = new ZipInputStream(url.openStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path filePath = outputDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    try (FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, len);
                        }
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }
}
