package com.mira.crates.util;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.function.ToDoubleFunction;

public final class WeightedPicker {
    private WeightedPicker() {
    }

    public static <T> Optional<T> pick(List<T> values, ToDoubleFunction<T> weight, RandomGenerator random) {
        double total = values.stream().mapToDouble(value -> Math.max(0.0D, weight.applyAsDouble(value))).sum();
        if (total <= 0.0D) return Optional.empty();

        double cursor = random.nextDouble(total);
        for (T value : values) {
            double current = Math.max(0.0D, weight.applyAsDouble(value));
            if (current <= 0.0D) continue;
            cursor -= current;
            if (cursor < 0.0D) return Optional.of(value);
        }
        return values.stream().filter(value -> weight.applyAsDouble(value) > 0.0D).reduce((a, b) -> b);
    }
}
