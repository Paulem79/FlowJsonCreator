package net.paulem.fjc.flow.mod;

/**
 * This class represents a Mod object.
 */
public record UrlMod(String name, String downloadURL, String sha1, long size) implements Mod {
}
