package com.brunorozendo.mcp.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contains the business logic for all filesystem-related tools and resources.
 */
public class FileTools {

    private static final Logger logger = LoggerFactory.getLogger(FileTools.class);

    private final static ObjectMapper objectMapper = new ObjectMapper();


    private static Mono<CallToolResult> handleTool(McpAsyncServerExchange exchange, Map<String, Object> args, ToolLogic logic) {
        try {
            return logic.execute(exchange, args);
        } catch (Exception e) {
            return Mono.just(CallToolResult.builder().textContent(List.of("Error: " + e.getMessage())).isError(true).build());
        }
    }



    @FunctionalInterface
    private interface ToolLogic {
        Mono<CallToolResult> execute(McpAsyncServerExchange exchange, Map<String, Object> args) throws Exception;
    }


    public static Mono<CallToolResult> readFile(McpAsyncServerExchange exchange, McpSchema.CallToolRequest req) {
        Map<String, Object> args = req.arguments();
        try {
            String pathStr = (String) args.get("path");
            Path validPath = Path.of(pathStr);
            String content = Files.readString(validPath);
            return Mono.just(CallToolResult.builder().textContent(List.of(content)).build());
        } catch (IOException e) {
            return Mono.just(CallToolResult.builder().textContent(List.of(e.getMessage())).isError(true).build());
        }


    }

    public static Mono<CallToolResult> readMultipleFiles(McpAsyncServerExchange exchange, McpSchema.CallToolRequest req) {
        Map<String, Object> args = req.arguments();
        return handleTool(exchange, args, (ex, toolArgs) -> {
            List<String> paths = List.of();

            // Check if paths are provided in the args
            Object pathsObject = toolArgs.get("paths");
            if (pathsObject instanceof List<?> pathList) {
                paths = pathList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
            }



            StringBuilder results = new StringBuilder();
            for (String pathStr : paths) {
                try {
                    Path validPath = Path.of(pathStr);
                    if (Files.isDirectory(validPath)) {
                        // If path is a directory, read all files in it
                        try (Stream<Path> stream = Files.walk(validPath)) {
                            List<Path> files = stream
                                .filter(Files::isRegularFile)
                                .toList();

                            for (Path file : files) {
                                try {
                                    String content = Files.readString(file);
                                    results.append(file.toString()).append(":\n").append(content).append("\n");
                                    results.append("\n---\n");
                                } catch (Exception e) {
                                    results.append(file.toString()).append(": Error - ").append(e.getMessage()).append("\n");
                                    results.append("\n---\n");
                                }
                            }
                        }
                    } else {
                        // If path is a file, read it directly
                        String content = Files.readString(validPath);
                        results.append(pathStr).append(":\n").append(content).append("\n");
                        results.append("\n---\n");
                    }
                } catch (Exception e) {
                    results.append(pathStr).append(": Error - ").append(e.getMessage()).append("\n");
                    results.append("\n---\n");
                }
            }
            return Mono.just(CallToolResult.builder().textContent(List.of(results.toString())).build());
        });
    }

    public static Mono<CallToolResult> writeFile(McpAsyncServerExchange exchange, McpSchema.CallToolRequest req) {
        Map<String, Object> args = req.arguments();
        return handleTool(exchange, args, (ex, a) -> {
            String pathStr = (String) a.get("path");
            String content = (String) a.get("content");
            Path validPath = Path.of(pathStr);
            Files.writeString(validPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return Mono.just(CallToolResult.builder().textContent(List.of("Successfully wrote to " + pathStr)).build());
        });
    }

    public static Mono<CallToolResult> createDirectory(McpAsyncServerExchange exchange, McpSchema.CallToolRequest req) {
        Map<String, Object> args = req.arguments();
        return handleTool(exchange, args, (ex, a) -> {
            String pathStr = (String) a.get("path");
            Path validPath = Path.of(pathStr);
            Files.createDirectories(validPath);
            return Mono.just(CallToolResult.builder().textContent(List.of("Successfully created directory " + pathStr)).build());
        });
    }

    public static Mono<CallToolResult> listDirectory(McpAsyncServerExchange exchange, McpSchema.CallToolRequest req) {
        Map<String, Object> args = req.arguments();
        return handleTool(exchange, args, (ex, a) -> {
            String pathStr = (String) a.get("path");

            Path validPath = Path.of(pathStr);
            try (Stream<Path> stream = Files.list(validPath)) {
                String formatted = stream
                    .map(p -> (Files.isDirectory(p) ? "[DIR] " : "[FILE] ") + p.getFileName().toString())
                    .collect(Collectors.joining("\n"));
                return Mono.just(CallToolResult.builder().textContent(List.of(formatted)).build());
            }
        });
    }

    public static Mono<CallToolResult> moveFile(McpAsyncServerExchange exchange, McpSchema.CallToolRequest req) {
        Map<String, Object> args = req.arguments();
        return handleTool(exchange, args, (ex, a) -> {
            String sourceStr = (String) a.get("source");
            String destStr = (String) a.get("destination");
            Path validSource = Path.of(sourceStr);
            Path validDest = Path.of(destStr);

            // Move the file
            Files.move(validSource, validDest);

            return Mono.just(CallToolResult.builder().textContent(List.of("Successfully moved " + sourceStr + " to " + destStr)).build());
        });
    }

    public static Mono<CallToolResult> getFileInfo(McpAsyncServerExchange exchange, McpSchema.CallToolRequest req) {
        Map<String, Object> args = req.arguments();
        return handleTool(exchange, args, (ex, a) -> {
            String pathStr = (String) a.get("path");
            Path validPath = Path.of(pathStr);
            BasicFileAttributes stats = Files.readAttributes(validPath, BasicFileAttributes.class);
            DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault());

            String permissions;
            try {
                permissions = PosixFilePermissions.toString(Files.getPosixFilePermissions(validPath));
            } catch (UnsupportedOperationException | IOException e) {
                permissions = "N/A";
            }

            String info = String.format(
                "size: %d\ncreated: %s\nmodified: %s\naccessed: %s\nisDirectory: %b\nisFile: %b\npermissions: %s",
                stats.size(),
                formatter.format(stats.creationTime().toInstant()),
                formatter.format(stats.lastModifiedTime().toInstant()),
                formatter.format(stats.lastAccessTime().toInstant()),
                stats.isDirectory(),
                stats.isRegularFile(),
                permissions
            );
            return Mono.just(CallToolResult.builder().textContent(List.of(info)).build());
        });
    }


    public static Mono<CallToolResult> searchFiles(McpAsyncServerExchange exchange, McpSchema.CallToolRequest req) {
        Map<String, Object> args = req.arguments();
        return handleTool(exchange, args, (ex, a) -> {
            String pathStr = (String) a.get("path");
            String pattern = (String) a.get("pattern");
            List<String> excludePatterns = getOptionalStringList(req);

            Path startPath = Path.of(pathStr);

            // Create a PathMatcher for the search pattern
            PathMatcher patternMatcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

            List<PathMatcher> excludeMatchers = excludePatterns.stream()
                    .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                    .toList();

            List<String> results = new ArrayList<>();
            Files.walkFileTree(startPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relativeDir = startPath.relativize(dir);
                    if (excludeMatchers.stream().anyMatch(m -> m.matches(relativeDir))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (patternMatcher.matches(relativeDir) || patternMatcher.matches(dir.getFileName())) {
                        results.add(dir.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relativeFile = startPath.relativize(file);
                    if (excludeMatchers.stream().anyMatch(m -> m.matches(relativeFile))) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (patternMatcher.matches(relativeFile) || patternMatcher.matches(file.getFileName())) {
                        results.add(file.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            String output = results.isEmpty() ? "No matches found" : String.join("\n", results);
            return Mono.just(CallToolResult.builder().textContent(List.of(output)).build());
        });
    }

    private static List<String> getOptionalStringList(McpSchema.CallToolRequest req) {
        Map<String, Object> args = req.arguments();
        Object value = args.get("excludePatterns");
        if (value instanceof List<?> list) {
            // This is a common pattern for deserialized data where generic types are erased.
            // We are assuming the list contains strings as per the tool's contract.
            @SuppressWarnings("unchecked")
            List<String> stringList = (List<String>) list;
            return stringList;
        }
        return Collections.emptyList();
    }

    public static Mono<CallToolResult> directoryTree(McpAsyncServerExchange exchange, McpSchema.CallToolRequest req) {
        Map<String, Object> args = req.arguments();
        return handleTool(exchange, args, (ex, a) -> {
            String pathStr = (String) a.get("path");
            Path startPath = Path.of(pathStr);
            Map<String, Object> tree = buildTree(startPath);
            String jsonTree = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
            return Mono.just(CallToolResult.builder().textContent(List.of(jsonTree)).build());
        });
    }

    private static Map<String, Object> buildTree(Path currentPath) throws IOException {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", currentPath.getFileName().toString());
        boolean isDir = Files.isDirectory(currentPath);
        entry.put("type", isDir ? "directory" : "file");

        if (isDir) {
            List<Map<String, Object>> children = new ArrayList<>();
            try (Stream<Path> stream = Files.list(currentPath)) {
                stream.forEach(child -> {
                    try {
                        children.add(buildTree(child));
                    } catch (IOException e) {
                        // Skip files we can't read, but log it.
                        logger.error("Could not process path {}: {}", child, e.getMessage());
                    }
                });
            }
            entry.put("children", children);
        }
        return entry;
    }

    public static Mono<CallToolResult> editFile(McpAsyncServerExchange exchange, McpSchema.CallToolRequest req) {
        Map<String, Object> args = req.arguments();
        return handleTool(exchange, args, (ex, a) -> {
            // Define local records for structured, type-safe argument handling.
            record Edit(String oldText, String newText) {
            }
            record EditFileArgs(String path, List<Edit> edits, Boolean dryRun) {
                boolean isDryRun() {
                    // Replicates the behavior of Map.getOrDefault("dryRun", false) for a nullable Boolean.
                    return dryRun != null && dryRun;
                }
            }

            // Use ObjectMapper to convert the map to a strongly-typed object, avoiding unsafe casts.
            EditFileArgs editArgs = objectMapper.convertValue(a, EditFileArgs.class);

            Path validPath = Path.of(editArgs.path());
            String originalContent = Files.readString(validPath);
            String modifiedContent = originalContent;

            for (Edit edit : editArgs.edits()) {
                String oldText = edit.oldText().replace("\r\n", "\n");
                String newText = edit.newText().replace("\r\n", "\n");
                if (!modifiedContent.contains(oldText)) {
                    throw new IOException("Could not find exact match for edit:\n" + oldText);
                }
                modifiedContent = modifiedContent.replace(oldText, newText);
            }

            List<String> originalLines = Arrays.asList(originalContent.split("\n"));
            List<String> modifiedLines = Arrays.asList(modifiedContent.split("\n"));

            Patch<String> patch = DiffUtils.diff(originalLines, modifiedLines);
            List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
                    validPath.toString(),
                    validPath.toString(),
                    originalLines, patch, 3
            );

            String diffString = String.join("\n", unifiedDiff);
            String resultMessage = "```diff\n" + diffString + "\n```";

            if (!editArgs.isDryRun()) {
                Files.writeString(validPath, modifiedContent);
            }

            return Mono.just(CallToolResult.builder().textContent(List.of(resultMessage)).build());
        });
    }
}
