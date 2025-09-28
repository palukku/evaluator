package dev.phlp.stud.evaluator.service.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phlp.stud.evaluator.model.state.EvaluationSaveData;
import dev.phlp.stud.evaluator.util.JsonMapperFactory;

public class AutoSaveService implements AutoCloseable {
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "autosave-writer");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<ScheduledFuture<?>> pendingSave = new AtomicReference<>();

    public AutoSaveService() {
        this(JsonMapperFactory.createDefaultMapper());
    }

    public AutoSaveService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<EvaluationSaveData> load(Path evaluationFile) {
        if (evaluationFile == null || !Files.exists(evaluationFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(evaluationFile.toFile(), EvaluationSaveData.class));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public void scheduleSave(Path evaluationFile, EvaluationSaveData data) {
        if (evaluationFile == null) {
            return;
        }
        data.setSavedAt(Instant.now());
        Runnable writer = () -> writeNow(evaluationFile, data);
        ScheduledFuture<?> future = scheduler.schedule(writer, 750, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> previous = pendingSave.getAndSet(future);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    public void writeImmediately(Path evaluationFile, EvaluationSaveData data) throws IOException {
        if (evaluationFile == null) {
            return;
        }
        data.setSavedAt(Instant.now());
        writeNow(evaluationFile, data);
    }

    private void writeNow(Path evaluationFile, EvaluationSaveData data) {
        try {
            Path parent = evaluationFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(evaluationFile.toFile(), data);
        } catch (IOException ex) {
            System.err.println("Autosave failed: " + ex.getMessage());
        }
    }

    @Override
    public void close() {
        ScheduledFuture<?> future = pendingSave.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
        scheduler.shutdownNow();
    }
}
