package net.paulem.fjc.flow.mod;

public class ModrinthMod implements Mod
{
    private String projectReference = "";
    private String versionNumber = "";
    private String versionId = "";

    /**
     * Construct a new ModrinthVersionInfo object.
     * @param projectReference the project reference can be slug or id.
     * @param versionNumber the version number (and NOT the version name unless they are the same).
     */
    public ModrinthMod(String projectReference, String versionNumber, String versionId)
    {
        this.projectReference = projectReference.trim();
        this.versionNumber = versionNumber.trim();
        this.versionId = versionId.trim();
    }

    public String getProjectReference()
    {
        return this.projectReference;
    }

    public String getVersionNumber()
    {
        return this.versionNumber;
    }

    public String getVersionId() {
        return versionId;
    }
}
