package dev.phlp.stud.evaluator.service.repository;

import java.util.Objects;
import java.util.Optional;

public final class CheckoutInfo {
    private final String reference;
    private final CheckoutStrategy strategy;

    public CheckoutInfo(String reference, CheckoutStrategy strategy) {
        this.reference = Optional.ofNullable(reference)
                                 .map(String::trim)
                                 .filter(value -> !value.isEmpty())
                                 .orElse(null);
        this.strategy =
                strategy != null ?
                strategy :
                CheckoutStrategy.none();
    }

    public Optional<String> reference() {
        return Optional.ofNullable(reference);
    }

    public CheckoutStrategy strategy() {
        return strategy;
    }

    public CheckoutInfo withReference(String newReference) {
        return new CheckoutInfo(newReference, strategy);
    }

    public CheckoutInfo withStrategy(CheckoutStrategy newStrategy) {
        return new CheckoutInfo(reference, newStrategy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CheckoutInfo other)) {
            return false;
        }
        return Objects.equals(reference, other.reference) && Objects.equals(strategy, other.strategy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference, strategy);
    }
}
