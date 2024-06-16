package io.github.paulem.fjc.flow;

/**
 * This class represents a Mod object.
 */
public class UrlMod
{
    private final String name;
    private final String sha1;
    private final long size;
    private final String downloadURL;

    /**
     * Construct a new Mod object.
     * @param name Name of mod file.
     * @param downloadURL Mod download URL.
     * @param sha1 Sha1 of mod file.
     * @param size Size of mod file.
     */
    public UrlMod(String name, String downloadURL, String sha1, long size)
    {
        this.name = name;
        this.downloadURL = downloadURL;
        this.sha1 = sha1;
        this.size = size;
    }

    /**
     * Get the mod name.
     * @return the mod name.
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * Get the sha1 of the mod.
     * @return the sha1 of the mod.
     */
    public String getSha1()
    {
        return this.sha1;
    }

    /**
     * Get the mod size.
     * @return the mod size.
     */
    public long getSize()
    {
        return this.size;
    }

    /**
     * Get the mod url.
     * @return the mod url.
     */
    public String getDownloadURL()
    {
        return this.downloadURL;
    }
}
