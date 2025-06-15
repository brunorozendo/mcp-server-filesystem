package com.brunorozendo.mcp.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contains the business logic for all filesystem-related tools and resources.
 */
public class FileTools {

    private final PathValidator pathValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Map to track resources by URI
    private final Map<String, Path> resourceMap = new ConcurrentHashMap<>();

    // WatchService for file system events
    private WatchService watchService;

    // Map to track watch keys and their directories
    private final Map<WatchKey, Path> watchKeyMap = new ConcurrentHashMap<>();

    // Executor for background file watching
    private final ExecutorService watchExecutor = Executors.newSingleThreadExecutor();

    // Resource change callback
    private Consumer<String> resourceChangeCallback;

    public FileTools(List<String> allowedDirs) {
        this.pathValidator = new PathValidator(allowedDirs);

        try {
            // Initialize the WatchService
            this.watchService = FileSystems.getDefault().newWatchService();

            // Start the file watching thread
            startFileWatcher();

            // Register all allowed directories for watching
            for (String dir : allowedDirs) {
                Path path = Paths.get(dir);
                registerDirectoryForWatching(path);
            }
        } catch (IOException e) {
            System.err.println("Failed to initialize file watching: " + e.getMessage());
        }
    }

    /**
     * Sets the callback to be invoked when a resource changes.
     * 
     * @param callback The callback function that accepts a resource URI
     */
    public void setResourceChangeCallback(Consumer<String> callback) {
        this.resourceChangeCallback = callback;
    }

    /**
     * Registers a directory and all its subdirectories for file watching.
     * 
     * @param directory The directory to watch
     * @throws IOException If an I/O error occurs
     */
    private void registerDirectoryForWatching(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }

        // Register the directory itself
        WatchKey key = directory.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        watchKeyMap.put(key, directory);

        // Register all subdirectories recursively
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    registerDirectoryForWatching(path);
                }
            }
        }
    }

    /**
     * Starts a background thread to watch for file system events.
     */
    private void startFileWatcher() {
        watchExecutor.submit(() -> {
            try {
                while (true) {
                    WatchKey key;
                    try {
                        // Wait for a key to be available
                        key = watchService.take();
                    } catch (InterruptedException e) {
                        return;
                    }

                    Path dir = watchKeyMap.get(key);
                    if (dir == null) {
                        continue;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        // Skip OVERFLOW events
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }

                        // Get the filename from the event
                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                        Path filename = pathEvent.context();
                        Path fullPath = dir.resolve(filename);

                        // Handle the event based on its kind
                        handleFileEvent(kind, fullPath);

                        // If a new directory is created, register it for watching
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
                            registerDirectoryForWatching(fullPath);
                        }
                    }

                    // Reset the key to receive further events
                    boolean valid = key.reset();
                    if (!valid) {
                        watchKeyMap.remove(key);
                        if (watchKeyMap.isEmpty()) {
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error in file watcher: " + e.getMessage());
            }
        });
    }

    /**
     * Handles a file system event by updating the resource map and notifying subscribers.
     * 
     * @param kind The kind of event
     * @param path The path that was affected
     */
    private void handleFileEvent(WatchEvent.Kind<?> kind, Path path) {
        // Convert the path to a URI
        String uri = "file://" + path.toAbsolutePath().normalize().toString();

        // Update the resource map based on the event kind
        if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            resourceMap.put(uri, path);
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            resourceMap.remove(uri);
        }

        // Notify subscribers if a callback is registered
        if (resourceChangeCallback != null) {
            resourceChangeCallback.accept(uri);
        }
    }

    private CallToolResult handleTool(McpSyncServerExchange exchange, Map<String, Object> args, ToolLogic logic) {
        try {
            return logic.execute(exchange, args);
        } catch (Exception e) {
            return new CallToolResult("Error: " + e.getMessage(), true);
        }
    }

    @FunctionalInterface
    private interface ToolLogic {
        CallToolResult execute(McpSyncServerExchange exchange, Map<String, Object> args) throws Exception;
    }

    // --- METHOD FOR HANDLING RESOURCES ---
    public ReadResourceResult readResource(McpSyncServerExchange exchange, ReadResourceRequest request) {
        try {
            String uri = request.uri();
            if (!uri.startsWith("file://")) {
                throw new IllegalArgumentException("Only 'file://' URIs are supported.");
            }
            // Extract path from URI, ensuring to handle potential Windows drive letters correctly
            String pathStr = uri.substring("file://".length());
            if (System.getProperty("os.name").toLowerCase().contains("win") && pathStr.startsWith("/")) {
                pathStr = pathStr.substring(1);
            }

            Path validPath = pathValidator.validate(pathStr);
            String content = Files.readString(validPath);
            String mimeType = Files.probeContentType(validPath);
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }

            // Add the resource to our tracking map
            resourceMap.put(uri, validPath);

            TextResourceContents resourceContents = new TextResourceContents(uri, mimeType, content);
            return new ReadResourceResult(List.of(resourceContents));
        } catch (Exception e) {
            // In a real server, you might want to return a proper MCP error response
            // For simplicity here, we re-throw, and the server will catch it.
            throw new RuntimeException("Failed to read resource: " + e.getMessage(), e);
        }
    }

    /**
     * Gets a list of all currently tracked resource URIs.
     * 
     * @return A list of resource URIs
     */
    public List<String> getTrackedResources() {
        return new ArrayList<>(resourceMap.keySet());
    }

    // --- EXISTING TOOL IMPLEMENTATIONS ---

    public CallToolResult readFile(McpSyncServerExchange exchange, Map<String, Object> args) {
        return handleTool(exchange, args, (ex, a) -> {
            String pathStr = (String) a.get("path");
            Path validPath = pathValidator.validate(pathStr);
            String content = Files.readString(validPath);
            return new CallToolResult(content, false);
        });
    }

    public CallToolResult readMultipleFiles(McpSyncServerExchange exchange, Map<String, Object> args) {
        return handleTool(exchange, args, (ex, toolArgs) -> {
            Object pathsObject = toolArgs.get("paths");
            List<String> paths = List.of();

            if (pathsObject instanceof List<?> pathList) {
                paths = pathList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
            }

            StringBuilder results = new StringBuilder();
            for (String pathStr : paths) {
                try {
                    Path validPath = pathValidator.validate(pathStr);
                    String content = Files.readString(validPath);
                    results.append(pathStr).append(":\n").append(content).append("\n");
                } catch (Exception e) {
                    results.append(pathStr).append(": Error - ").append(e.getMessage()).append("\n");
                }
                results.append("\n---\n");
            }
            return new CallToolResult(results.toString(), false);
        });
    }

    public CallToolResult writeFile(McpSyncServerExchange exchange, Map<String, Object> args) {
        return handleTool(exchange, args, (ex, a) -> {
            String pathStr = (String) a.get("path");
            String content = (String) a.get("content");
            Path validPath = pathValidator.validate(pathStr);
            Files.writeString(validPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Add to resource map and notify subscribers
            String uri = "file://" + validPath.toAbsolutePath().normalize().toString();
            resourceMap.put(uri, validPath);
            if (resourceChangeCallback != null) {
                resourceChangeCallback.accept(uri);
            }

            return new CallToolResult("Successfully wrote to " + pathStr, false);
        });
    }

    public CallToolResult createDirectory(McpSyncServerExchange exchange, Map<String, Object> args) {
        return handleTool(exchange, args, (ex, a) -> {
            String pathStr = (String) a.get("path");
            Path validPath = pathValidator.validate(pathStr);
            Files.createDirectories(validPath);
            return new CallToolResult("Successfully created directory " + pathStr, false);
        });
    }

    public CallToolResult listDirectory(McpSyncServerExchange exchange, Map<String, Object> args) {
        return handleTool(exchange, args, (ex, a) -> {
            String pathStr = (String) a.get("path");
            Path validPath = pathValidator.validate(pathStr);
            try (Stream<Path> stream = Files.list(validPath)) {
                String formatted = stream
                    .map(p -> (Files.isDirectory(p) ? "[DIR] " : "[FILE] ") + p.getFileName().toString())
                    .collect(Collectors.joining("\n"));
                return new CallToolResult(formatted, false);
            }
        });
    }

    public CallToolResult moveFile(McpSyncServerExchange exchange, Map<String, Object> args) {
        return handleTool(exchange, args, (ex, a) -> {
            String sourceStr = (String) a.get("source");
            String destStr = (String) a.get("destination");
            Path validSource = pathValidator.validate(sourceStr);
            Path validDest = pathValidator.validate(destStr);

            // Create URIs for source and destination
            String sourceUri = "file://" + validSource.toAbsolutePath().normalize().toString();
            String destUri = "file://" + validDest.toAbsolutePath().normalize().toString();

            // Move the file
            Files.move(validSource, validDest);

            // Update resource map and notify subscribers
            resourceMap.remove(sourceUri);
            resourceMap.put(destUri, validDest);

            if (resourceChangeCallback != null) {
                resourceChangeCallback.accept(sourceUri); // Notify about the source being removed
                resourceChangeCallback.accept(destUri);   // Notify about the destination being added
            }

            return new CallToolResult("Successfully moved " + sourceStr + " to " + destStr, false);
        });
    }

    public CallToolResult getFileInfo(McpSyncServerExchange exchange, Map<String, Object> args) {
        return handleTool(exchange, args, (ex, a) -> {
            String pathStr = (String) a.get("path");
            Path validPath = pathValidator.validate(pathStr);
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
            return new CallToolResult(info, false);
        });
    }

    public CallToolResult listAllowedDirectories(McpSyncServerExchange exchange, Map<String, Object> args) {
        return handleTool(exchange, args, (ex, a) -> {
            String dirs = "Allowed directories:\n" + String.join("\n", pathValidator.getAllowedDirectoriesAsString());
            return new CallToolResult(dirs, false);
        });
    }

    public CallToolResult searchFiles(McpSyncServerExchange exchange, Map<String, Object> args) {
        return handleTool(exchange, args, (ex, a) -> {
            String pathStr = (String) a.get("path");
            String pattern = (String) a.get("pattern");
            List<String> excludePatterns = getOptionalStringList(a);

            Path startPath = pathValidator.validate(pathStr);

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
            return new CallToolResult(output, false);
        });
    }

    private List<String> getOptionalStringList(Map<String, Object> args) {
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

    public CallToolResult directoryTree(McpSyncServerExchange exchange, Map<String, Object> args) {
        return handleTool(exchange, args, (ex, a) -> {
            String pathStr = (String) a.get("path");
            Path startPath = pathValidator.validate(pathStr);
            Map<String, Object> tree = buildTree(startPath);
            String jsonTree = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
            return new CallToolResult(jsonTree, false);
        });
    }

    private Map<String, Object> buildTree(Path currentPath) throws IOException {
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
                        System.err.println("Could not process path " + child + ": " + e.getMessage());
                    }
                });
            }
            entry.put("children", children);
        }
        return entry;
    }

    public CallToolResult editFile(McpSyncServerExchange exchange, Map<String, Object> args) {
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

            Path validPath = pathValidator.validate(editArgs.path());
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

                // Update resource map and notify subscribers
                String uri = "file://" + validPath.toAbsolutePath().normalize().toString();
                resourceMap.put(uri, validPath);

                if (resourceChangeCallback != null) {
                    resourceChangeCallback.accept(uri);
                }
            }

            return new CallToolResult(resultMessage, false);
        });
    }
}
