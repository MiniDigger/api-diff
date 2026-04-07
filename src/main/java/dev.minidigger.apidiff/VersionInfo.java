package dev.minidigger.apidiff;

public record VersionInfo(String name, int build, String channel) {
    private String channelSuffix() {
        return switch(channel) {
            case "ALPHA" -> "-alpha";
            case "BETA" -> "-beta";
            default -> "";
        };
    }

    public String metadataUrl(String family) {
        var parts = family.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        if (major >= 26) {
            throw new RuntimeException("Versions >=26.1 do not have maven metadata");
        }
        String group = "io/papermc";
        if (minor < 17) {
            group = "com/destroystokyo";
        }

        return "https://repo.papermc.io/repository/maven-public/" + group + "/paper/paper-api/" + name.replace(".0", ".") + "-R0.1-SNAPSHOT/maven-metadata.xml";
    }

    public String sourcesUrl(String family, String snapshotVersion) {
        var parts = family.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        String group = "io/papermc";
        if (major < 26 && minor < 17) {
            group = "com/destroystokyo";
        }

        String fullName = name.replace(".0", ".");
        String sourcesName;
        if (major < 26) {
            fullName += "-R0.1-SNAPSHOT";
            sourcesName = snapshotVersion;
        } else {
            fullName += ".build." + build + channelSuffix();
            sourcesName = fullName;
        }

        return "https://repo.papermc.io/repository/maven-public/" + group + "/paper/paper-api/" + fullName + "/paper-api-" + sourcesName + "-sources.jar";
    }
}
