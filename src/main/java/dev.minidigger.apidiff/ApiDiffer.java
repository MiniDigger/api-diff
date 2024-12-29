package dev.minidigger.apidiff;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import javax.lang.model.element.ElementKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ApiDiffer {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public final Map<String, ApiExport> exports = new LinkedHashMap<>();
    public final Map<String, ApiDiff> diffs = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public ApiExport load(String version) {
        return exports.computeIfAbsent(version, v -> {
            try {
                List<Map<String, Object>> input = gson.fromJson(Files.readString(Path.of("output/raw/paper-api-" + version + ".json")), ArrayList.class);
                ApiExport export = new ApiExport(version, new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
                parse(input, export, null);
                return export;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void diff(String versionA, String versionB, Path output) throws Exception {
        // read the two api exports
        ApiExport a = load(versionA);
        ApiExport b = load(versionB);

        List<Package> packagesAdded = new ArrayList<>();
        List<Package> packagesRemoved = new ArrayList<>();
        List<Package> packagesChanged = new ArrayList<>();

        List<Class> classesAdded = new ArrayList<>();
        List<Class> classesRemoved = new ArrayList<>();
        List<Class> classesChanged = new ArrayList<>();

        List<Member> membersAdded = new ArrayList<>();
        List<Member> membersRemoved = new ArrayList<>();
        List<Member> membersChanged = new ArrayList<>();

        // compare packages
        for (Map.Entry<String, Package> entry : b.packages.entrySet()) {
            Package aPackage = a.packages.get(entry.getKey());
            if (aPackage == null) {
                packagesAdded.add(entry.getValue());
            } else {
                if (!entry.getValue().classes().equals(aPackage.classes())) {
                    packagesChanged.add(entry.getValue());
                }
            }
        }
        for (Map.Entry<String, Package> entry : a.packages.entrySet()) {
            if (!b.packages.containsKey(entry.getKey())) {
                packagesRemoved.add(entry.getValue());
            }
        }

        // compare classes
        for (Map.Entry<String, Class> entry : b.classes.entrySet()) {
            Class aClass = a.classes.get(entry.getKey());
            if (aClass == null) {
                classesAdded.add(entry.getValue());
            } else {
                if (!entry.getValue().members().equals(aClass.members())) {
                    classesChanged.add(entry.getValue());
                }
            }
        }
        for (Map.Entry<String, Class> entry : a.classes.entrySet()) {
            if (!b.classes.containsKey(entry.getKey())) {
                classesRemoved.add(entry.getValue());
            }
        }

        // compare members
        for (Map.Entry<String, Member> entry : b.members.entrySet()) {
            Member aMember = a.members.get(entry.getKey());
            if (aMember == null) {
                membersAdded.add(entry.getValue());
            } else {
                if (!entry.getValue().equals(aMember)) {
                    membersChanged.add(entry.getValue());
                }
            }
        }
        for (Map.Entry<String, Member> entry : a.members.entrySet()) {
            if (!b.members.containsKey(entry.getKey())) {
                membersRemoved.add(entry.getValue());
            }
        }

        ApiDiff diff = new ApiDiff(
                versionA,
                versionB,
                packagesAdded,
                packagesRemoved,
                packagesChanged,
                classesAdded,
                classesRemoved,
                classesChanged,
                membersAdded.stream().collect(Collectors.groupingBy((m) -> m.parent().name())),
                membersRemoved.stream().collect(Collectors.groupingBy((m) -> m.parent().name())),
                membersChanged.stream().collect(Collectors.groupingBy((m) -> m.parent().name()))
        );
        diffs.put(versionA + "-" + versionB, diff);

        // poor mans type adapter
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("versionA", versionA);
        result.put("versionB", versionB);
        result.put("packagesAdded", packagesAdded.stream().map(Package::name).toList());
        result.put("packagesRemoved", packagesRemoved.stream().map(Package::name).toList());
        result.put("packagesChanged", packagesChanged.stream().map(Package::name).toList());
        result.put("classesAdded", classesAdded.stream().map(Class::name).toList());
        result.put("classesRemoved", classesRemoved.stream().map(Class::name).toList());
        result.put("classesChanged", classesChanged.stream().map(Class::name).toList());
        result.put("membersAdded", diff.membersAdded.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().map(Member::name).toList())));
        result.put("membersRemoved", diff.membersRemoved.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().map(Member::name).toList())));
        result.put("membersChanged", diff.membersRemoved.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().map(Member::name).toList())));
        Files.writeString(output, gson.toJson(result));
    }

    public record ApiDiff(String versionA, String versionB,
                          List<Package> packagesAdded, List<Package> packagesRemoved, List<Package> packagesChanged,
                          List<Class> classesAdded, List<Class> classesRemoved, List<Class> classesChanged,
                          Map<String, List<Member>> membersAdded, Map<String, List<Member>> membersRemoved,
                          Map<String, List<Member>> membersChanged) {
    }

    @SuppressWarnings("unchecked")
    private void parse(List<Map<String, Object>> input, ApiExport export, Element parent) {
        if (input == null) {
            return;
        }

        for (Map<String, Object> element : input) {
            ElementKind kind = ElementKind.valueOf((String) element.get("kind"));
            switch (kind) {
                case PACKAGE -> {
                    Package p = new Package((String) element.get("name"), new ArrayList<>(), (String) element.get("apiStatus"), (String) element.get("link"));
                    export.packages.put(p.name(), p);
                    parse((List<Map<String, Object>>) element.get("children"), export, p);
                }
                case CLASS, ENUM, RECORD, INTERFACE, ANNOTATION_TYPE -> {
                    Class c = new Class((String) element.get("name"), new ArrayList<>(), new ArrayList<>(), (String) element.get("apiStatus"), (String) element.get("link"));
                    export.classes.put(c.name(), c);
                    parent.addChild(c);
                    parse((List<Map<String, Object>>) element.get("children"), export, c);
                }

                case TYPE_PARAMETER -> {
                    // ignore
                }

                case CONSTRUCTOR, METHOD, FIELD, ENUM_CONSTANT, RECORD_COMPONENT -> {
                    Member m = new Member((String) element.get("name"), (String) element.get("kind"), (List<String>) element.get("params"), (String) element.get("apiStatus"), (Class) parent, (String) element.get("link"));
                    export.members.put(m.name(), m);
                    parent.addChild(m);
                }

                default -> throw new IllegalArgumentException("Unknown element kind: " + kind);
            }
        }
    }

    public record ApiExport(String version, Map<String, Package> packages, Map<String, Class> classes,
                            Map<String, Member> members) {
    }

    interface Element {
        String name();

        String link();

        void addChild(Element child);
    }

    record Package(@Expose String name, @Expose List<Class> classes, @Expose String apiStatus,
                   @Expose String link) implements Element {
        @Override
        public void addChild(Element child) {
            classes.add((Class) child);
        }
    }

    record Class(@Expose String name, @Expose List<Member> members, @Expose List<Class> innerClasses,
                 @Expose String apiStatus, @Expose String link) implements Element {
        @Override
        public void addChild(Element child) {
            if (child instanceof Class c) {
                innerClasses.add(c);
            } else if (child instanceof Member m) {
                members.add(m);
            } else {
                throw new IllegalArgumentException("Unknown child type: " + child.getClass());
            }
        }
    }

    record Member(@Expose String name, @Expose String type, @Expose List<String> params, @Expose String apiStatus,
                  Class parent, @Expose String link) implements Element {

        @Override
        public void addChild(Element child) {
            throw new UnsupportedOperationException("Members can't have children");
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Member member = (Member) o;
            return Objects.equals(name, member.name) && Objects.equals(type, member.type) && Objects.equals(apiStatus, member.apiStatus) && Objects.equals(params, member.params);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, params, apiStatus);
        }
    }
}
