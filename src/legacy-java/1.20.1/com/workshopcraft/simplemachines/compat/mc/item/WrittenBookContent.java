package com.workshopcraft.simplemachines.compat.mc.item;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Stand-in for the 1.20.5+ written book component, read from the book's title and pages tags. */
public record WrittenBookContent(Title title, List<Component> pages) {
    public record Title(String raw) {
    }

    public List<Component> getPages(boolean filtered) {
        return pages;
    }
}
