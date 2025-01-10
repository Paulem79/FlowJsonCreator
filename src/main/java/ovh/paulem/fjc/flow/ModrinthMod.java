package ovh.paulem.fjc.flow;

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
    public ModrinthMod(String projectReference, String versionNumber)
    {
        this.projectReference = projectReference.trim();
        this.versionNumber = versionNumber.trim();
    }

    /**
     * Construct a new ModrinthVersionInfo object.
     * This constructor doesn't need a project reference because
     * we can access the version without any project information.
     * @param versionId the version id.
     */
    public ModrinthMod(String versionId)
    {
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

    public String getVersionId()
    {
        return this.versionId;
    }
}
