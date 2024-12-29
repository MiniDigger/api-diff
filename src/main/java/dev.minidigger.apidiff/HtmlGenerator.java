package dev.minidigger.apidiff;

import dev.minidigger.apidiff.ApiDiffer.ApiDiff;
import dev.minidigger.apidiff.ApiDiffer.Member;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.minidigger.apidiff.ApiDiffer.*;

public class HtmlGenerator {

    private final Path output = Path.of("output");

    private final ApiDiffer apiDiffer;
    private final String css = """
            <style>
            html {
                color-scheme: dark light;
            }
            [index] {
                list-style-type: none;
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(35ch, 1fr));
                padding: 0;
            }
            [nested] {
                list-style-type: none;
                padding-left: 1em;
            }
            [diff] {
                list-style-type: none;
                display: grid;
                grid-template-columns: repeat(auto-fit, 60ch);
                padding-left: 1em;
            }
            h2:has(+ ul[empty]) {
                display: none;
            }
            a, a:visited {
                color: unset;
                text-decoration: none;
            }
            a:hover {
                text-decoration: underline;
            }
            </style>
            """;

    public HtmlGenerator(ApiDiffer apiDiffer) {
        this.apiDiffer = apiDiffer;
    }

    public void generateIndex() throws Exception {
        try (Stream<Path> files = Files.list(output.resolve("raw"))) {
            String rawData = files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json"))
                    .map(Path::getFileName).map(Path::toString).sorted()
                    .map((s) -> "    <li><a href=\"raw/" + s + "\">" + s + "</a></li>")
                    .collect(Collectors.joining("\n", "  <ul index>\n", "\n  </ul>"));
            String diffs = apiDiffer.diffs.keySet().stream().sorted().map((s) -> "    <li><a href=\"diff-" + s + ".html\">" + s + "</a></li>")
                    .collect(Collectors.joining("\n", "  <ul index>\n", "\n  </ul>"));
            String index = """
                    <html lang="en">
                    <head>
                        <title>ApiDiff</title>
                        %s
                    </head>
                    <body>
                    <h1>ApiDiff</h1>
                    <h2>Diffs</h2>
                    %s
                    <h2>Raw data</h2>
                    %s
                    </body>
                    </html>
                    """.formatted(css, diffs, rawData);
            Files.writeString(output.resolve("index.html"), index);
        }
    }

    public void generateSince() {
        // TODO generate since
//        apiDiffer.diffs.fo
    }

    public void generateDiff(String versionA, String versionB) throws Exception {
        ApiDiff diff = apiDiffer.diffs.get(versionA + "-" + versionB);
        String html = """
                <html lang="en">
                <head>
                    <title>{versionA} {versionB} | ApiDiff</title>
                    {css}
                </head>
                <body>
                <a href='index.html'>Back</a>
                <h1>Diff between {versionA} and {versionB}</h1>
                <h2>Added packages</h2>
                {packagesAdded}
                <h2>Removed packages</h2>
                {packagesRemoved}
                <h2>Changed packages</h2>
                {packagesChanged}
                <h2>Added classes</h2>
                {classesAdded}
                <h2>Removed classes</h2>
                {classesRemoved}
                <h2>Changed classes</h2>
                {classesChanged}
                <h2>Added members</h2>
                {membersAdded}
                <h2>Removed members</h2>
                {membersRemoved}
                <h2>Changed members</h2>
                {membersChanged}
                </body>
                </html>
                """
                .replace("{css}", css)
                .replace("{versionA}", versionA)
                .replace("{versionB}", versionB)
                .replace("{packagesAdded}", list(diff.packagesAdded()))
                .replace("{packagesRemoved}", list(diff.packagesRemoved()))
                .replace("{packagesChanged}", list(diff.packagesChanged()))
                .replace("{classesAdded}", list(diff.classesAdded()))
                .replace("{classesRemoved}", list(diff.classesRemoved()))
                .replace("{classesChanged}", list(diff.classesChanged()))
                .replace("{membersAdded}", group(diff.membersAdded(), versionB))
                .replace("{membersRemoved}", group(diff.membersRemoved(), versionA))
                .replace("{membersChanged}", group(diff.membersChanged(), versionB));

        Files.writeString(output.resolve("diff-" + versionA + "-" + versionB + ".html"), html);
    }

    private String group(Map<String, List<Member>> input, String version) {
        return input.keySet().stream().sorted()
                .map((c) -> {
                    String link = apiDiffer.load(version).classes().get(c).link();
                    return "<li>\n<h3><a href=\"" + link + "\">" + htmlEscape(c) + "</a></h3>\n" + list(input.get(c)) + "\n</li>\n";
                })
                .collect(Collectors.joining("\n", input.isEmpty() ? "<ul empty>" : "<ul nested>\n", "\n</ul>"));
    }

    private String list(List<? extends Element> list) {
        return list.stream().sorted(Comparator.comparing(Element::name))
                .map((s) -> "  <li><a href=\"" + s.link() + "\">" + htmlEscape(s.name()) + "</a></li>")
                .collect(Collectors.joining("\n", list.isEmpty() ? "<ul empty>" : "<ul diff>\n", "\n</ul>"));
    }

    private String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
