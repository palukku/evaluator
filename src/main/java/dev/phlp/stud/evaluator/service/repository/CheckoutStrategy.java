package dev.phlp.stud.evaluator.service.repository;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class CheckoutStrategy {
    private final CheckoutMode mode;
    private final String detail;

    private CheckoutStrategy(CheckoutMode mode, String detail) {
        this.mode = mode;
        this.detail = Optional.ofNullable(detail)
                              .map(String::trim)
                              .filter(value -> !value.isEmpty())
                              .orElse(null);
    }

    public static CheckoutStrategy none() {
        return new CheckoutStrategy(null, null);
    }

    public static CheckoutStrategy of(CheckoutMode mode, String detail) {
        if (mode == null) {
            return none();
        }
        return new CheckoutStrategy(mode, detail);
    }

    public static Optional<CheckoutStrategy> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        String trimmed = encoded.trim();
        String[] parts = trimmed.split(":", 2);
        try {
            CheckoutMode parsedMode = CheckoutMode.valueOf(parts[0].toUpperCase(Locale.ROOT));
            String parsedDetail =
                    parts.length > 1 ?
                    parts[1] :
                    null;
            return Optional.of(new CheckoutStrategy(parsedMode, parsedDetail));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public Optional<CheckoutMode> mode() {
        return Optional.ofNullable(mode);
    }

    public Optional<String> detail() {
        return Optional.ofNullable(detail);
    }

    public Optional<String> encode() {
        return mode().map(activeMode -> detail()
                .map(value -> activeMode.name() + ':' + value)
                .orElse(activeMode.name()));
    }

    public CheckoutStrategy updateDetail(String newDetail) {
        return new CheckoutStrategy(mode, newDetail);
    }

    public CheckoutStrategy updateMode(CheckoutMode newMode) {
        return new CheckoutStrategy(newMode, detail);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CheckoutStrategy other)) {
            return false;
        }
        return mode == other.mode && Objects.equals(detail, other.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, detail);
    }
}
