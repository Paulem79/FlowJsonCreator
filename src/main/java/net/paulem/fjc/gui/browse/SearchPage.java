package net.paulem.fjc.gui.browse;

import java.util.List;

public record SearchPage(List<SearchResult> results, boolean hasMore) {
}
