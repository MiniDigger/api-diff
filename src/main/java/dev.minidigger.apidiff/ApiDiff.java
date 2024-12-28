package dev.minidigger.apidiff;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.lang.model.element.ElementKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ApiDiff {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ApiDiff(Path aPath, Path bPath, Path output) throws Exception {
        // read the two api exports
        List<Map<String, Object>> a = gson.fromJson(Files.readString(aPath), ArrayList.class);
        List<Map<String, Object>> b = gson.fromJson(Files.readString(bPath), ArrayList.class);

        Map<String, Package> aPackages = new LinkedHashMap<>();
        Map<String, Package> bPackages = new LinkedHashMap<>();

        Map<String, Class> aClasses = new LinkedHashMap<>();
        Map<String, Class> bClasses = new LinkedHashMap<>();

        Map<String, Member> aMembers = new LinkedHashMap<>();
        Map<String, Member> bMembers = new LinkedHashMap<>();

        List<Package> packagesAdded = new ArrayList<>();
        List<Package> packagesRemoved = new ArrayList<>();
        List<Package> packagesChanged = new ArrayList<>();

        List<Class> classesAdded = new ArrayList<>();
        List<Class> classesRemoved = new ArrayList<>();
        List<Class> classesChanged = new ArrayList<>();

        List<Member> membersAdded = new ArrayList<>();
        List<Member> membersRemoved = new ArrayList<>();
        List<Member> membersChanged = new ArrayList<>();

        parse(a, aPackages, aClasses, aMembers, null);
        parse(b, bPackages, bClasses, bMembers, null);

        // construct a diff between the two api exports
        // compare packages
        for (Map.Entry<String, Package> entry : aPackages.entrySet()) {
            if (!bPackages.containsKey(entry.getKey())) {
                packagesRemoved.add(entry.getValue());
            } else {
                Package bPackage = bPackages.get(entry.getKey());
                if (!entry.getValue().classes().equals(bPackage.classes())) {
                    packagesChanged.add(entry.getValue());
                }
            }
        }
        for (Map.Entry<String, Package> entry : bPackages.entrySet()) {
            if (!aPackages.containsKey(entry.getKey())) {
                packagesAdded.add(entry.getValue());
            }
        }

        // compare classes
        for (Map.Entry<String, Class> entry : aClasses.entrySet()) {
            if (!bClasses.containsKey(entry.getKey())) {
                classesRemoved.add(entry.getValue());
            } else {
                Class bClass = bClasses.get(entry.getKey());
                if (!entry.getValue().members().equals(bClass.members())) {
                    classesChanged.add(entry.getValue());
                }
            }
        }
        for (Map.Entry<String, Class> entry : bClasses.entrySet()) {
            if (!aClasses.containsKey(entry.getKey())) {
                classesAdded.add(entry.getValue());
            }
        }

        // compare members
        for (Map.Entry<String, Member> entry : aMembers.entrySet()) {
            if (!bMembers.containsKey(entry.getKey())) {
                membersRemoved.add(entry.getValue());
            } else {
                Member bMember = bMembers.get(entry.getKey());
                if (!entry.getValue().type().equals(bMember.type())) {
                    membersChanged.add(entry.getValue());
                }
            }
        }
        for (Map.Entry<String, Member> entry : bMembers.entrySet()) {
            if (!aMembers.containsKey(entry.getKey())) {
                membersAdded.add(entry.getValue());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("packagesAdded", packagesAdded.stream().map(Package::name).toList());
        result.put("packagesRemoved", packagesRemoved.stream().map(Package::name).toList());
        result.put("packagesChanged", packagesChanged.stream().map(Package::name).toList());

        result.put("classesAdded", classesAdded.stream().map(Class::name).toList());
        result.put("classesRemoved", classesRemoved.stream().map(Class::name).toList());
        result.put("classesChanged", classesChanged.stream().map(Class::name).toList());

        result.put("membersAdded", membersAdded.stream().map(Member::name).toList());
        result.put("membersRemoved", membersRemoved.stream().map(Member::name).toList());
        result.put("membersChanged", membersChanged.stream().map(Member::name).toList());

        Files.writeString(output, gson.toJson(result));
    }

    @SuppressWarnings("unchecked")
    private void parse(List<Map<String, Object>> input, Map<String, Package> packages, Map<String, Class> classes, Map<String, Member> members, Element parent) {
        if (input == null) {
            return;
        }

        for (Map<String, Object> element : input) {
            ElementKind kind = ElementKind.valueOf((String) element.get("kind"));
            switch (kind) {
                case PACKAGE -> {
                    Package p = new Package((String) element.get("name"), new ArrayList<>(), (String) element.get("apiStatus"));
                    packages.put(p.name(), p);
                    parse((List<Map<String, Object>>) element.get("children"), packages, classes, members, p);
                }
                case CLASS, ENUM, RECORD, INTERFACE, ANNOTATION_TYPE -> {
                    Class c = new Class((String) element.get("name"), new ArrayList<>(), new ArrayList<>(), (String) element.get("apiStatus"));
                    classes.put(c.name(), c);
                    parent.addChild(c);
                    parse((List<Map<String, Object>>) element.get("children"), packages, classes, members, c);
                }

                case TYPE_PARAMETER -> {
                    // ignore
                }

                case CONSTRUCTOR, METHOD, FIELD, ENUM_CONSTANT, RECORD_COMPONENT -> {
                    Member m = new Member((String) element.get("name"), (String) element.get("kind"), (List<String>) element.get("params"), (String) element.get("apiStatus"));
                    members.put(m.name(), m);
                    parent.addChild(m);
                }

                default -> throw new IllegalArgumentException("Unknown element kind: " + kind);
            }
        }
    }

    interface Element {
        String name();

        void addChild(Element child);
    }

    record Package(String name, List<Class> classes, String apiStatus) implements Element {
        @Override
        public void addChild(Element child) {
            classes.add((Class) child);
        }
    }

    record Class(String name, List<Member> members, List<Class> innerClasses, String apiStatus) implements Element {
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

    record Member(String name, String type, List<String> params, String apiStatus) implements Element {
        @Override
        public void addChild(Element child) {
            throw new UnsupportedOperationException("Members can't have children");
        }
    }
}
