package ovh.paulem.modrinthapi.types.enums;

public enum DependencyType {
    REQUIRED("required"),
    OPTIONAL("optional"),
    INCOMPATIBLE("incompatible"),
    EMBEDDED("embedded");

    private final String text;

    DependencyType(final String text) {
        this.text = text;
    }

    public static DependencyType getEnum(String text) {
        return DependencyType.valueOf(DependencyType.class, text.toUpperCase());
    }

    @Override
    public String toString() {
        return text;
    }
}