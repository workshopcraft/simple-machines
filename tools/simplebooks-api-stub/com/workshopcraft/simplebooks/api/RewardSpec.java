package com.workshopcraft.simplebooks.api;

import com.google.gson.JsonObject;

/** Compile-only stub of the Simple Books 1.3.0 API. The real classes are used at runtime. */
public class RewardSpec {
    public RewardSpec(String type, JsonObject json) {}

    public static RewardSpec of(JsonObject json) {
        return new RewardSpec("", json);
    }

    public String type() {
        return "";
    }

    public JsonObject json() {
        return new JsonObject();
    }

    public String string(String key, String def) {
        return def;
    }

    public long number(String key, long def) {
        return def;
    }
}
