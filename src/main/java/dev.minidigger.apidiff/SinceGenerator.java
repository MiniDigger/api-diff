package dev.minidigger.apidiff;

import com.google.common.collect.*;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SinceGenerator {

    private final List<String> versions;
    private final ApiDiffer apiDiffer;

    public SinceGenerator(List<String> versions, ApiDiffer apiDiffer) {
        this.versions = versions;
        this.apiDiffer = apiDiffer;
    }

    public SinceReport generate() throws Exception {
        Map<String, String> packages = new LinkedHashMap<>();
        Map<String, String> classes = new LinkedHashMap<>();
        Table<String, String, String> members = TreeBasedTable.create();

        ApiDiffer.ApiExport lastExport = apiDiffer.load(versions.getLast());
        for (ApiDiffer.Package aPackage : lastExport.packages().values()) {
            packages.put(aPackage.name(), packetSince(aPackage));
            for (ApiDiffer.Class aClass : aPackage.classes()) {
                classes.put(aClass.name(), classSince(aClass));
                for (ApiDiffer.Member member : aClass.members()) {
                    members.put(aClass.name(), member.name(), memberSince(member));
                }
                for (ApiDiffer.Class innerClass : aClass.innerClasses()) {
                    classes.put(innerClass.name(), classSince(innerClass));
                    for (ApiDiffer.Member member : innerClass.members()) {
                        members.put(innerClass.name(), member.name(), memberSince(member));
                    }
                }
            }
        }

        SinceReport report = new SinceReport(packages, classes, members);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(report);
        Files.writeString(Path.of("output/raw/since.json"), json);
        return report;
    }

    private String packetSince(ApiDiffer.Package aPackage) {
        for (String version : versions) {
            ApiDiffer.ApiExport export = apiDiffer.load(version);
            if (export.packages().containsKey(aPackage.name())) {
                if (version.equals(versions.getFirst())) {
                    return "basically forever";
                }
                return version;
            }
        }
        return "forever";
    }

    private String classSince(ApiDiffer.Class aClass) {
        for (String version : versions) {
            ApiDiffer.ApiExport export = apiDiffer.load(version);
            if (export.classes().containsKey(aClass.name())) {
                if (version.equals(versions.getFirst())) {
                    return "basically forever";
                }
                return version;
            }
        }
        return "forever";
    }

    private String memberSince(ApiDiffer.Member member) {
        for (String version : versions) {
            ApiDiffer.ApiExport export = apiDiffer.load(version);
            if (export.members().containsKey(member.name())) {
                if (version.equals(versions.getFirst())) {
                    return "basically forever";
                }
                return version;
            }
        }
        return "forever";
    }

    public record SinceReport(Map<String, String> packages, Map<String, String> classes,
                              Table<String, String, String> members) {
    }
}
