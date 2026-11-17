package com.workshopcraft.simplemachines.compat.mc.item;

import java.util.List;
import java.util.stream.Stream;

/** Stand-in for the 1.20.5+ writable book component, read from the book's pages tag. */
public record WritableBookContent(List<String> pages) {
    public Stream<String> getPages(boolean filtered) {
        return pages.stream();
    }
}
