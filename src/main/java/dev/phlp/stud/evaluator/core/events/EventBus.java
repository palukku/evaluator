package dev.phlp.stud.evaluator.core.events;

import java.util.function.Consumer;

/**
 * Lightweight publish/subscribe contract that allows controllers and services
 * to communicate without direct coupling.
 */
public interface EventBus {

    /**
     * Registers a handler for events of the given type.
     *
     * @param type    event class to subscribe to
     * @param handler consumer that receives the event instances
     * @param <T>     concrete event type
     * @return an {@link AutoCloseable} handle that removes the subscription when closed
     */
    <T extends AppEvent> AutoCloseable subscribe(Class<T> type, Consumer<T> handler);

    /**
     * Publishes the given event to all registered handlers for its class.
     *
     * @param event event to distribute
     */
    void publish(AppEvent event);
}
