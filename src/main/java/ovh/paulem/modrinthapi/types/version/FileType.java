package ovh.paulem.modrinthapi.types.version;

import org.jetbrains.annotations.Nullable;

public enum FileType {
    REQUIRED_RESOURCE_PACK("required-resource-pack"),
    OPTIONAL_RESOURCE_PACK("optional-resource-pack");

    private final String text;

    FileType(final String text) {
        this.text = text;
    }

    @Nullable
    public static FileType getEnum(@Nullable String text) {
        if(text == null) return null;
        return FileType.valueOf(FileType.class, text.toUpperCase().replace("-", "_"));
    }

    @Override
    public String toString() {
        return text;
    }
}