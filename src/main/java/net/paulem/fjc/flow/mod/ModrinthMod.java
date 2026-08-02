package net.paulem.fjc.flow.mod;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModrinthMod other)) return false;
        return projectReference.equals(other.projectReference)
                && versionNumber.equals(other.versionNumber)
                && versionId.equals(other.versionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectReference, versionNumber, versionId);
    }
}
