package dev.phlp.stud.evaluator.core.di;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Concurrent hash map backed {@link ServiceRegistry} implementation.
 */
public final class DefaultServiceRegistry implements ServiceRegistry {

    private final ConcurrentHashMap<Class<?>, Object> services = new ConcurrentHashMap<>();

    @Override
    public <T> void add(Class<T> type, T implementation) {
        services.put(type, implementation);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        return (T) services.get(type);
    }
}
