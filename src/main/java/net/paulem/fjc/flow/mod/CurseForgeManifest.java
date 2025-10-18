package net.paulem.fjc.flow.mod;

import java.util.List;

public record CurseForgeManifest(String author, List<CurseForgeMod> files) {
}
