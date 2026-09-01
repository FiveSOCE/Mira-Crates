package com.mira.crates.api;

import java.util.List;

public record CrateSnapshot(String id, String displayName, List<String> acceptedKeys, int rewardCount) {
    public CrateSnapshot {
        acceptedKeys = List.copyOf(acceptedKeys);
    }
}
