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
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Contains the business logic for all filesystem-related tools and resources.
 */
public class FileTools {

    private static final Logger logger = LoggerFactory.getLogger(FileTools.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter FILE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault());


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
        } catch (Exception e) {
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
                            stream.filter(Files::isRegularFile).forEach(file -> {
                                try {
                                    String content = Files.readString(file);
                                    results.append(file).append(":\n").append(content).append("\n");
                                    results.append("\n---\n");
                                } catch (Exception e) {
                                    results.append(file).append(": Error - ").append(e.getMessage()).append("\n");
                                    results.append("\n---\n");
                                }
                            });
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
            // walkFileTree with maxDepth=1 routes all direct children (files AND dirs) through
            // visitFile because directories at the depth boundary are treated as leaf entries.
            // attrs.isDirectory() lets us distinguish them without an extra stat call.
            List<String> entries = new ArrayList<>();
            Files.walkFileTree(validPath, Set.of(), 1, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    entries.add((attrs.isDirectory() ? "[DIR] " : "[FILE] ") + file.getFileName());
                    return FileVisitResult.CONTINUE;
                }
            });
            return Mono.just(CallToolResult.builder().textContent(List.of(String.join("\n", entries))).build());
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

            BasicFileAttributes stats;
            String permissions;
            try {
                // PosixFileAttributes extends BasicFileAttributes — one syscall covers both.
                PosixFileAttributes posixStats = Files.readAttributes(validPath, PosixFileAttributes.class);
                stats = posixStats;
                permissions = PosixFilePermissions.toString(posixStats.permissions());
            } catch (UnsupportedOperationException e) {
                stats = Files.readAttributes(validPath, BasicFileAttributes.class);
                permissions = "N/A";
            }

            String info = String.format(
                "size: %d\ncreated: %s\nmodified: %s\naccessed: %s\nisDirectory: %b\nisFile: %b\npermissions: %s",
                stats.size(),
                FILE_TIMESTAMP_FORMATTER.format(stats.creationTime().toInstant()),
                FILE_TIMESTAMP_FORMATTER.format(stats.lastModifiedTime().toInstant()),
                FILE_TIMESTAMP_FORMATTER.format(stats.lastAccessTime().toInstant()),
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
                    for (PathMatcher m : excludeMatchers) {
                        if (m.matches(relativeDir)) return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (patternMatcher.matches(relativeDir) || patternMatcher.matches(dir.getFileName())) {
                        results.add(dir.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relativeFile = startPath.relativize(file);
                    for (PathMatcher m : excludeMatchers) {
                        if (m.matches(relativeFile)) return FileVisitResult.CONTINUE;
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

    private static Map<String, Object> buildTree(Path startPath) throws IOException {
        boolean rootIsDir = Files.isDirectory(startPath);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", startPath.getFileName().toString());
        root.put("type", rootIsDir ? "directory" : "file");
        if (!rootIsDir) {
            return root;
        }

        List<Map<String, Object>> rootChildren = new ArrayList<>();
        root.put("children", rootChildren);

        // Iterative DFS: each frame holds the directory to expand and its pre-created children list.
        // This avoids StackOverflowError on deeply nested trees.
        Deque<Map.Entry<Path, List<Map<String, Object>>>> stack = new ArrayDeque<>();
        stack.push(Map.entry(startPath, rootChildren));

        while (!stack.isEmpty()) {
            Map.Entry<Path, List<Map<String, Object>>> frame = stack.pop();
            Path dir = frame.getKey();
            List<Map<String, Object>> children = frame.getValue();

            List<Path> entries;
            try (Stream<Path> stream = Files.list(dir)) {
                entries = stream
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList();
            } catch (IOException e) {
                logger.error("Could not list directory {}: {}", dir, e.getMessage());
                continue;
            }

            for (Path child : entries) {
                boolean childIsDir = Files.isDirectory(child);
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("name", child.getFileName().toString());
                node.put("type", childIsDir ? "directory" : "file");
                children.add(node);
                if (childIsDir) {
                    List<Map<String, Object>> grandChildren = new ArrayList<>();
                    node.put("children", grandChildren);
                    stack.push(Map.entry(child, grandChildren));
                }
            }
        }
        return root;
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
            String originalContent = Files.readString(validPath).replace("\r\n", "\n");
            String modifiedContent = originalContent;

            for (Edit edit : editArgs.edits()) {
                String oldText = edit.oldText().replace("\r\n", "\n");
                String newText = edit.newText().replace("\r\n", "\n");
                int idx = modifiedContent.indexOf(oldText);
                if (idx == -1) {
                    throw new IOException("Could not find exact match for edit:\n" + oldText);
                }
                modifiedContent = modifiedContent.substring(0, idx) + newText + modifiedContent.substring(idx + oldText.length());
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
