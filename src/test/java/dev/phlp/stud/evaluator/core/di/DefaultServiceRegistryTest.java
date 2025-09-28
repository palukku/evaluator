package dev.phlp.stud.evaluator.core.di;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultServiceRegistryTest {

    @Test
    void returnsNullWhenServiceIsMissing() {
        var registry = new DefaultServiceRegistry();

        assertNull(registry.get(SampleService.class));
    }

    @Test
    void storesAndReturnsServices() {
        var registry = new DefaultServiceRegistry();
        var sample = new SampleServiceImpl();
        var another = new AnotherServiceImpl();

        registry.add(SampleService.class, sample);
        registry.add(AnotherService.class, another);

        assertSame(sample, registry.get(SampleService.class));
        assertSame(another, registry.get(AnotherService.class));
    }

    private interface SampleService {
    }

    private interface AnotherService {
    }

    private static final class SampleServiceImpl implements SampleService {
    }

    private static final class AnotherServiceImpl implements AnotherService {
    }
}
