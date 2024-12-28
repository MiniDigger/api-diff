package dev.minidigger.apidiff;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.util.DocTrees;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.util.ElementScanner14;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ApiExportDoclet implements Doclet {
    private static final Comparator<Map<String, Object>> comparator = Comparator.comparing(m -> (String) m.get("name"));

    private Locale locale;
    private Reporter reporter;
    private Path outputFile;

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.locale = locale;
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return Set.of(
                new Option() {
                    @Override
                    public int getArgumentCount() {
                        return 1;
                    }

                    @Override
                    public String getDescription() {
                        return "name of the output file";
                    }

                    @Override
                    public Kind getKind() {
                        return Kind.STANDARD;
                    }

                    @Override
                    public List<String> getNames() {
                        return List.of("--output-file");
                    }

                    @Override
                    public String getParameters() {
                        return "";
                    }

                    @Override
                    public boolean process(String option,
                                           List<String> arguments) {
                        outputFile = Path.of(arguments.getFirst());
                        return true;
                    }
                });
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_21;
    }

    @Override
    public boolean run(DocletEnvironment environment) {
        HtmlDocletWriter htmlDocletWriter = bullshit(environment);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try (var out = new PrintWriter(Files.newBufferedWriter(outputFile))) {
            ShowElements se = new ShowElements(environment.getDocTrees(), htmlDocletWriter);
            Set<Map<String, Object>> result = new TreeSet<>(comparator);
            se.scan(environment.getSpecifiedElements(), result);
            out.println(gson.toJson(result));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    static class ShowElements extends ElementScanner14<Void, Set<Map<String, Object>>> {
        private final DocTrees docTrees;
        private final HtmlDocletWriter htmlDocletWriter;

        ShowElements(DocTrees docTrees, HtmlDocletWriter htmlDocletWriter) {
            this.docTrees = docTrees;
            this.htmlDocletWriter = htmlDocletWriter;
        }

        @Override
        public Void scan(Element e, Set<Map<String, Object>> result) {
            Map<String, Object> element = new LinkedHashMap<>();
            Set<Map<String, Object>> children = new TreeSet<>(comparator);
            element.put("kind", e.getKind());
            element.put("name", e.toString());
            element.put("children", children);

            if (e instanceof Symbol s && s.getMetadata() != null) {
                for (Attribute.Compound attribute : s.getMetadata().getDeclarationAttributes()) {
                    String annotation = attribute.getAnnotationType().toString();
                    if (annotation.startsWith("org.jetbrains.annotations.ApiStatus.")) {
                        element.put("apiStatus", annotation.replace("org.jetbrains.annotations.ApiStatus.", ""));
                    }
                    if (annotation.startsWith("java.lang.Deprecated")) {
                        Map<String, String> deprecated = new LinkedHashMap<>();
                        attribute.getElementValues().forEach((k, v) -> {
                            if (k.getSimpleName().contentEquals("forRemoval")) {
                                deprecated.put("forRemoval", v.toString());
                            } else if (k.getSimpleName().contentEquals("since")) {
                                deprecated.put("since", v.toString().replace("\"", ""));
                            }
                        });
                        deprecated.put("deprecated", "true");
                        element.put("deprecated", deprecated);
                    }
                }
            }

            if ("Internal".equals(element.get("apiStatus"))) {
                return null;
            }

            if (e.toString().contains("NamespacedTag(java.lang.@org.jetbrains.annotations.NotNull String,java.lang.@org.jetbrains.annotations.NotNull String)")) {
                System.out.println("woo");
            }

            DocCommentTree dcTree = docTrees.getDocCommentTree(e);
            if (dcTree != null) {
                Map<String, String> comment = new LinkedHashMap<>();
                comment.put("preamble", htmlDocletWriter.commentTagsToContent(e, dcTree.getPreamble(), false).toString());
                comment.put("body", htmlDocletWriter.commentTagsToContent(e, dcTree.getFullBody(), false).toString());
                comment.put("postamble", htmlDocletWriter.commentTagsToContent(e, dcTree.getPostamble(), false).toString());
                // TODO this doesnt work for some reason
                comment.put("tags", htmlDocletWriter.commentTagsToContent(e, dcTree.getBlockTags(), false).toString());
                comment.put("plain", dcTree.toString());
                element.put("comment", comment);

                Set.of("preamble", "body", "postamble", "tags").forEach(k -> {
                    if (comment.get(k).isBlank()) {
                        comment.remove(k);
                    }
                });
            }

            result.add(element);
            try {
                super.scan(e, children);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (children.isEmpty()) {
                element.remove("children");
            }

            return null;
        }
    }

    private HtmlDocletWriter bullshit(DocletEnvironment environment) {
        HtmlConfiguration htmlConfiguration = new HtmlConfiguration(this, locale, reporter) {
            {
                initConfiguration(environment, (s) -> s);
            }

            @Override
            public void initDocLint(List<String> opts, Set<String> customTagNames) {
                // no-op
            }
        };
        try {
            htmlConfiguration.setOptions();
        } catch (DocletException e) {
            throw new RuntimeException(e);
        }
        return new HtmlDocletWriter(htmlConfiguration, DocPath.create(outputFile.getFileName().toString()));
    }
}