package com.mira.crates.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WeightedPickerTest {
    @Test
    void ignoresZeroWeightEntries() {
        record Entry(String id, double weight) {}
        List<Entry> values = List.of(new Entry("never", 0), new Entry("always", 1));
        for (int i = 0; i < 50; i++) {
            assertEquals("always", WeightedPicker.pick(values, Entry::weight, new Random(i)).orElseThrow().id());
        }
    }

    @Test
    void emptyWhenNoPositiveWeights() {
        assertTrue(WeightedPicker.pick(List.of(0, 0), Integer::doubleValue, new Random(1)).isEmpty());
    }
}
