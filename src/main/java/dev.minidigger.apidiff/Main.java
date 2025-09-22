package dev.minidigger.apidiff;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static jdk.javadoc.internal.tool.Main.execute;

public class Main {
    public static void main(String[] args) throws Exception {
        List<String> versions = List.of(
                "1.09.4",
                "1.10.2",
                "1.11", "1.11.1", "1.11.2",
                "1.12", "1.12.1", "1.12.2",
                "1.13", "1.13.1", "1.13.2",
                "1.14", "1.14.1", "1.14.2", "1.14.3", "1.14.4",
                "1.15", "1.15.2",
                "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5",
                "1.17", "1.17.1",
                "1.18", "1.18.1", "1.18.2",
                "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4",
                "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
                "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9-pre2"
        );

        Main main = new Main();
        ApiDiffer apiDiffer = new ApiDiffer();
        SinceGenerator sinceGenerator = new SinceGenerator(versions, apiDiffer);
        HtmlGenerator htmlGenerator = new HtmlGenerator(apiDiffer);

        // download all sources jars
        for (String version : versions) {
            main.fetchSourcesJar(version);
        }

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

    public void fetchSourcesJar(String version) throws Exception {
        // TODO add hash check to prevent redownloading
        System.out.println("Fetching sources for " + version);
        int minor = Integer.parseInt(version.split("\\.")[1]);
        String group = "io/papermc";
        if (minor < 17) {
            group = "com/destroystokyo";
        }
        String metadataUrl = "https://repo.papermc.io/repository/maven-public/" + group + "/paper/paper-api/" + version.replace(".0", ".") + "-R0.1-SNAPSHOT/maven-metadata.xml";
        String snapshotVersion = getLatestSnapshotVersion(metadataUrl);
        String sourcesUrl = "https://repo.papermc.io/repository/maven-public/" + group + "/paper/paper-api/" + version.replace(".0", ".") + "-R0.1-SNAPSHOT/paper-api-" + snapshotVersion + "-sources.jar";
        downloadAndExtractSourcesToDisk(sourcesUrl, Path.of("sources/paper-api-" + version));
    }

    public String getLatestSnapshotVersion(String metadataUrl) throws Exception {
        URL url = new URI(metadataUrl).toURL();
        try (InputStream inputStream = url.openStream()) {
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
                        if ("jar".equals(extension)) {
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
