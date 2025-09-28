package dev.phlp.stud.evaluator.service.repository;

import java.util.List;
import java.util.stream.IntStream;

public record PlaceholderRange(
        int start,
        int end) {
    public PlaceholderRange {
        if (start <= 0) {
            throw new IllegalArgumentException("start must be positive");
        }
        if (end < start) {
            throw new IllegalArgumentException("end must be greater or equal to start");
        }
    }

    public List<Integer> values() {
        return IntStream.rangeClosed(start, end).boxed().toList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlaceholderRange(
                int start1,
                int end1
        ))) {
            return false;
        }
        return start == start1 && end == end1;
    }
}
