package dev.minidigger.apidiff;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SourceFetcher {

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
