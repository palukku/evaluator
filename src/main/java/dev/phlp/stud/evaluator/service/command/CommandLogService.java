package dev.phlp.stud.evaluator.service.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CommandLogService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public Path writeLog(Path evaluationDirectory, String nodeName, Iterable<String> commands, CharSequence output) throws IOException {
        Path logDirectory = evaluationDirectory.resolve("logs");
        Files.createDirectories(logDirectory);
        String safeNode = nodeName.replaceAll("[^a-zA-Z0-9-_]", "_");
        String filename = FORMATTER.format(LocalDateTime.now()) + "_" + safeNode + ".log";
        Path logFile = logDirectory.resolve(filename);
        String lineSeparator = System.lineSeparator();
        StringBuilder builder = new StringBuilder();
        builder.append("# Commands").append(lineSeparator);
        for (String command : commands) {
            builder.append("$ ").append(command).append(lineSeparator);
        }
        builder.append(lineSeparator).append("# Output").append(lineSeparator);
        builder.append(output);
        Files.writeString(logFile, builder.toString(), StandardCharsets.UTF_8);
        return evaluationDirectory.relativize(logFile);
    }
}
