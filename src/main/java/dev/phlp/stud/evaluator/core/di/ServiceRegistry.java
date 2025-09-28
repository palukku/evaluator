package dev.phlp.stud.evaluator.core.di;

/**
 * Very small dependency container used by the JavaFX composition root to make
 * services accessible to controllers without tight coupling.
 */
public interface ServiceRegistry {

    /**
     * Registers the given implementation under the provided type token.
     *
     * @param type           the lookup key
     * @param implementation concrete instance to store
     * @param <T>            service type
     */
    <T> void add(Class<T> type, T implementation);

    /**
     * Fetches the implementation previously registered under the given type.
     *
     * @param type lookup key
     * @param <T>  service type
     * @return the stored implementation or {@code null} if none was registered
     */
    <T> T get(Class<T> type);
}
