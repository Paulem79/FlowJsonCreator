package io.github.paulem.fjc.flow;

import java.util.Objects;

public class CurseFileInfo {
    private final int projectID;
    private final int fileID;

    /**
     * Construct a new CurseFileInfo object.
     *
     * @param projectID the ID of the project.
     * @param fileID    the ID of the file.
     */
    public CurseFileInfo(int projectID, int fileID) {
        this.projectID = projectID;
        this.fileID = fileID;
    }

    /**
     * Get the project ID.
     * @return the project ID.
     */
    public int getProjectID()
    {
        return this.projectID;
    }

    /**
     * Get the file ID.
     * @return the file ID.
     */
    public int getFileID()
    {
        return this.fileID;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        final CurseFileInfo that = (CurseFileInfo)o;
        return this.projectID == that.projectID && this.fileID == that.fileID;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.projectID, this.fileID);
    }
}