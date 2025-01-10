package ovh.paulem.modrinthapi.types.project;

public enum ProjectSide {
    REQUIRED("required"),
    OPTIONAL("optional"),
    UNSUPPORTED("unsupported");

    private final String text;

    ProjectSide(final String text) {
        this.text = text;
    }

    public static ProjectSide getEnum(String text) {
        return ProjectSide.valueOf(ProjectSide.class, text.toUpperCase());
    }

    @Override
    public String toString() {
        return text;
    }
}
