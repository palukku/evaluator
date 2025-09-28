package dev.phlp.stud.evaluator.service.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class CommandRunner implements AutoCloseable {
    private final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "command-runner");
        thread.setDaemon(true);
        return thread;
    });

    public CommandExecution runCommands(List<String> commands, Path workingDirectory, CommandOutputListener listener) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(listener, "listener");

        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<Process> currentProcess = new AtomicReference<>();
        Callable<Void> job = () -> {
            for (String command : commands) {
                if (cancelled.get()) {
                    break;
                }
                listener.onCommandStarted(command);
                Process process = startProcess(command, workingDirectory);
                currentProcess.set(process);
                try {
                    StreamForwarder stdout = new StreamForwarder(process.getInputStream(), listener::onStdout);
                    StreamForwarder stderr = new StreamForwarder(process.getErrorStream(), listener::onStderr);
                    Future<?> stdoutFuture = executorService.submit(stdout);
                    Future<?> stderrFuture = executorService.submit(stderr);
                    int exitCode = process.waitFor();
                    stdoutFuture.get(2, TimeUnit.SECONDS);
                    stderrFuture.get(2, TimeUnit.SECONDS);
                    listener.onCommandFinished(command, exitCode);
                    if (exitCode != 0) {
                        break;
                    }
                } catch (Exception ex) {
                    listener.onFailure(command, ex);
                    break;
                } finally {
                    process.destroy();
                    currentProcess.set(null);
                }
            }
            listener.onAllCommandsFinished(cancelled.get());
            return null;
        };
        Future<Void> future = executorService.submit(job);
        return new CommandExecution(cancelled, currentProcess, future);
    }

    private Process startProcess(String command, Path workingDirectory) throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(workingDirectory.toFile());
        if (isWindows()) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("/bin/sh", "-c", command);
        }
        return builder.start();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }

    public interface CommandOutputListener {
        void onCommandStarted(String command);

        void onStdout(String line);

        void onStderr(String line);

        void onCommandFinished(String command, int exitCode);

        void onFailure(String command, Exception exception);

        void onAllCommandsFinished(boolean cancelled);
    }

    public static class CommandExecution {
        private final AtomicBoolean cancelled;
        private final AtomicReference<Process> currentProcess;
        private final Future<Void> future;

        private CommandExecution(AtomicBoolean cancelled, AtomicReference<Process> currentProcess, Future<Void> future) {
            this.cancelled = cancelled;
            this.currentProcess = currentProcess;
            this.future = future;
        }

        public void cancel() {
            cancelled.set(true);
            Process process = currentProcess.getAndSet(null);
            if (process != null) {
                process.destroy();
                try {
                    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            future.cancel(true);
        }
    }

    private record StreamForwarder(
            InputStream inputStream,
            Consumer<String> consumer) implements Runnable {

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    consumer.accept(line);
                }
            } catch (IOException ignored) {
                // reader closed when process ends
            }
        }
    }
}
