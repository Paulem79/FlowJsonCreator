package ovh.paulem.fjc.flow;

/**
 * This class represents a Mod object.
 */
public record UrlMod(String name, String downloadURL, String sha1, long size) implements Mod {
}
