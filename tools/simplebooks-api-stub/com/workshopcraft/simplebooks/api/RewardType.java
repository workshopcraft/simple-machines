package com.workshopcraft.simplebooks.api;

import com.google.gson.JsonObject;

/** Compile-only stub of the Simple Books 1.3.0 API. The real classes are used at runtime. */
public interface RewardType {
    RewardSpec parse(JsonObject json, RewardProblems problems);

    default Display display(RewardSpec spec) {
        return new Display(spec.string("icon", ""), spec.string("label", spec.type()));
    }

    void grant(RewardContext ctx, RewardSpec spec);
}
