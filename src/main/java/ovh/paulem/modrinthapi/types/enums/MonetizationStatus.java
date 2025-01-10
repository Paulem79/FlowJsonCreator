package ovh.paulem.modrinthapi.types.enums;

import org.jetbrains.annotations.Nullable;

public enum MonetizationStatus {
    MONETIZED("monetized"),
    DEMONETIZED("demonetized"),
    FORCE_DEMONETIZED("force-demonetized");

    private final String text;

    MonetizationStatus(final String text) {
        this.text = text;
    }

    @Nullable
    public static MonetizationStatus getEnum(@Nullable String text) {
        if(text == null) return null;
        return MonetizationStatus.valueOf(MonetizationStatus.class, text.toUpperCase().replace("-", "_"));
    }

    @Override
    public String toString() {
        return text;
    }
}
