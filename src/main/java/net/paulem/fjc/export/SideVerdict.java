package net.paulem.fjc.export;

/**
 * Whether a mod is needed on a dedicated server, and how we found out.
 *
 * @param method    human-readable origin of the verdict ("Modrinth", "fabric.mod.json"...)
 * @param heuristic true when the verdict is a best guess (bytecode analysis) rather than something the mod or its
 *                  platform declares, so the UI can flag it for a second look
 */
public record SideVerdict(Side side, String method, boolean heuristic) {
    public enum Side {
        /** Runs on the client only: left out of a server export. */
        CLIENT_ONLY,
        /** Works on a dedicated server (server-only, or both sides). */
        SERVER_CAPABLE
    }

    public static SideVerdict clientOnly(String method) {
        return new SideVerdict(Side.CLIENT_ONLY, method, false);
    }

    public static SideVerdict serverCapable(String method) {
        return new SideVerdict(Side.SERVER_CAPABLE, method, false);
    }

    public SideVerdict asHeuristic() {
        return new SideVerdict(side, method, true);
    }

    public boolean isClientOnly() {
        return side == Side.CLIENT_ONLY;
    }
}
