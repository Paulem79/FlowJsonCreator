package ovh.paulem.modrinthapi.types.version;

public enum VersionType {
    RELEASE("release"),
    BETA("beta"),
    ALPHA("alpha");

    private final String text;

    VersionType(final String text) {
        this.text = text;
    }

    public static VersionType getEnum(String text) {
        return VersionType.valueOf(VersionType.class, text.toUpperCase());
    }

    @Override
    public String toString() {
        return text;
    }
}
