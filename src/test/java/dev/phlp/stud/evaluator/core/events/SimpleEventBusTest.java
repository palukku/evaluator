package dev.phlp.stud.evaluator.core.events;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleEventBusTest {

    @Test
    void publishNotifiesSubscribers() {
        var bus = new SimpleEventBus();
        var payload = new AtomicReference<String>();

        bus.subscribe(TestEvent.class, event -> payload.set(event.value()));

        bus.publish(new TestEvent("hello"));

        assertEquals("hello", payload.get());
    }

    @Test
    void unsubscribeStopsNotifications() throws Exception {
        var bus = new SimpleEventBus();
        var counter = new AtomicInteger();

        var subscription = bus.subscribe(TestEvent.class, event -> counter.incrementAndGet());
        bus.publish(new TestEvent("first"));
        assertEquals(1, counter.get());

        subscription.close();
        bus.publish(new TestEvent("second"));

        assertEquals(1, counter.get());
    }

    @Test
    void multipleSubscribersReceiveEvents() {
        var bus = new SimpleEventBus();
        var first = new AtomicReference<String>();
        var second = new AtomicReference<String>();

        bus.subscribe(TestEvent.class, event -> first.set(event.value()));
        bus.subscribe(TestEvent.class, event -> second.set(event.value()));

        bus.publish(new TestEvent("payload"));

        assertEquals("payload", first.get());
        assertEquals("payload", second.get());
    }

    private record TestEvent(
            String value) implements AppEvent {
    }
}
