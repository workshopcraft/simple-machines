package com.workshopcraft.simplemachines.compat.mc.item;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Stand-in for the 1.20.5+ lore component; Comps writes it to display.Lore. */
public record ItemLore(List<Component> lines) {
}
