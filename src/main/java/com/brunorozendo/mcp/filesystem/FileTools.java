package com.brunorozendo.mcp.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contains the business logic for all filesystem-related tools and resources.
 */
public class FileTools {

    private final PathValidator pathValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileTools(List<String> allowedDirs) {
        this.pathValidator = new PathValidator(allowedDirs);
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

    // --- NEW METHOD FOR HANDLING RESOURCES ---
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

            TextResourceContents resourceContents = new TextResourceContents(uri, mimeType, content);
            return new ReadResourceResult(List.of(resourceContents));
        } catch (Exception e) {
            // In a real server, you might want to return a proper MCP error response
            // For simplicity here, we re-throw, and the server will catch it.
            throw new RuntimeException("Failed to read resource: " + e.getMessage(), e);
        }
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
        return handleTool(exchange, args, (ex, a) -> {
            List<String> paths = (List<String>) a.get("paths");
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
            Files.move(validSource, validDest);
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
            List<String> excludePatterns = (List<String>) a.getOrDefault("excludePatterns", Collections.emptyList());

            Path startPath = pathValidator.validate(pathStr);

            List<PathMatcher> excludeMatchers = excludePatterns.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .collect(Collectors.toList());

            List<String> results = new ArrayList<>();
            Files.walkFileTree(startPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relativeDir = startPath.relativize(dir);
                    if (excludeMatchers.stream().anyMatch(m -> m.matches(relativeDir))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (dir.getFileName().toString().toLowerCase().contains(pattern.toLowerCase())) {
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
                    if (file.getFileName().toString().toLowerCase().contains(pattern.toLowerCase())) {
                        results.add(file.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            String output = results.isEmpty() ? "No matches found" : String.join("\n", results);
            return new CallToolResult(output, false);
        });
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
            String pathStr = (String) a.get("path");
            List<Map<String, String>> edits = (List<Map<String, String>>) a.get("edits");
            boolean dryRun = (Boolean) a.getOrDefault("dryRun", false);

            Path validPath = pathValidator.validate(pathStr);
            String originalContent = Files.readString(validPath);
            String modifiedContent = originalContent;

            for (Map<String, String> edit : edits) {
                String oldText = edit.get("oldText").replace("\r\n", "\n");
                String newText = edit.get("newText").replace("\r\n", "\n");
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

            if (!dryRun) {
                Files.writeString(validPath, modifiedContent);
            }

            return new CallToolResult(resultMessage, false);
        });
    }
}
