package net.paulem.fjc.gui.browse;

import org.jetbrains.annotations.Nullable;

public record SearchQuery(String query, @Nullable String gameVersion, @Nullable String loader,
                          @Nullable String category, SortField sort, int offset, int limit) {
    public SearchQuery withOffset(int newOffset) {
        return new SearchQuery(query, gameVersion, loader, category, sort, newOffset, limit);
    }
}
