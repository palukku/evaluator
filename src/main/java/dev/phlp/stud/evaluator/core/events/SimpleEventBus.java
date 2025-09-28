package dev.phlp.stud.evaluator.core.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Simple in-memory implementation of the {@link EventBus} using thread-safe
 * collections so that controllers can register and publish events from the FX
 * thread and background tasks alike.
 */
public final class SimpleEventBus implements EventBus {

    private final Map<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();

    @Override
    public <T extends AppEvent> AutoCloseable subscribe(Class<T> type, Consumer<T> handler) {
        handlers.computeIfAbsent(type, key -> Collections.synchronizedList(new ArrayList<>())).add(handler);
        return () -> handlers.getOrDefault(type, List.of()).remove(handler);
    }

    @Override
    public void publish(AppEvent event) {
        var subscribers = handlers.getOrDefault(event.getClass(), List.of());
        synchronized (subscribers) {
            for (var rawHandler : subscribers) {
                @SuppressWarnings("unchecked")
                var handler = (Consumer<AppEvent>) rawHandler;
                handler.accept(event);
            }
        }
    }
}
