package com.brunorozendo.mcp.filesystem;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages resources and resource discovery for the MCP filesystem server.
 * This is a simplified version that works with the current MCP SDK limitations.
 */
public class ResourceManager {
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);
    
    private final PathValidator pathValidator;
    private final Map<String, Resource> resources = new ConcurrentHashMap<>();
    
    // Resource change listeners
    private final List<Runnable> listChangeListeners = new ArrayList<>();
    
    public ResourceManager(PathValidator pathValidator) {
        this.pathValidator = pathValidator;
        
        // Scan allowed directories for initial resources
        scanAllowedDirectories();
    }
    
    /**
     * Scan all allowed directories and register files as resources.
     */
    private void scanAllowedDirectories() {
        for (String dirPath : pathValidator.getAllowedDirectoriesAsString()) {
            try {
                Path dir = Paths.get(dirPath);
                if (Files.exists(dir) && Files.isDirectory(dir)) {
                    scanDirectory(dir);
                }
            } catch (Exception e) {
                logger.error("Error scanning directory {}: {}", dirPath, e.getMessage());
            }
        }
        logger.info("Scanned {} resources from allowed directories", resources.size());
    }
    
    /**
     * Recursively scan a directory and register all files as resources.
     */
    private void scanDirectory(Path directory) {
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isRegularFile()) {
                        registerFileResource(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    logger.warn("Failed to visit file {}: {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.error("Error walking directory tree {}: {}", directory, e.getMessage());
        }
    }
    
    /**
     * Register a file as a resource.
     */
    private void registerFileResource(Path file) {
        try {
            String uri = pathToUri(file);
            String name = file.getFileName().toString();
            String description = "File: " + file.toAbsolutePath().normalize();
            String mimeType = Files.probeContentType(file);
            
            if (mimeType == null) {
                // Guess MIME type based on extension
                mimeType = guessMimeType(name);
            }
            
            Resource resource = new Resource(
                uri,
                name,
                description,
                mimeType,
                null // No annotations for now
            );
            
            resources.put(uri, resource);
        } catch (Exception e) {
            logger.error("Error registering file resource {}: {}", file, e.getMessage());
        }
    }
    
    /**
     * Convert a Path to a file:// URI.
     */
    private String pathToUri(Path path) {
        return "file://" + path.toAbsolutePath().normalize().toString();
    }
    
    /**
     * Guess MIME type based on file extension.
     */
    private String guessMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".java")) return "text/x-java-source";
        if (lower.endsWith(".py")) return "text/x-python";
        if (lower.endsWith(".sh")) return "application/x-sh";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "application/x-yaml";
        if (lower.endsWith(".properties")) return "text/x-java-properties";
        if (lower.endsWith(".gradle")) return "text/x-gradle";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }
    
    /**
     * Get all resources as a list.
     * This is a simplified version since the SDK doesn't support proper resources/list yet.
     */
    public List<Resource> getAllResources() {
        return new ArrayList<>(resources.values());
    }
    
    /**
     * Update resources when a file is created or modified.
     */
    public void onFileCreated(Path file) {
        if (Files.isRegularFile(file)) {
            registerFileResource(file);
            notifyListChange();
        }
    }
    
    /**
     * Update resources when a file is deleted.
     */
    public void onFileDeleted(Path file) {
        String uri = pathToUri(file);
        if (resources.remove(uri) != null) {
            notifyListChange();
        }
    }
    
    /**
     * Update resources when a file is modified.
     */
    public void onFileModified(Path file) {
        if (Files.isRegularFile(file)) {
            // Re-register to update any metadata
            registerFileResource(file);
            // Note: This doesn't trigger a list change notification
            // since the list itself hasn't changed, only the content
        }
    }
    
    /**
     * Add a listener for resource list changes.
     */
    public void addListChangeListener(Runnable listener) {
        listChangeListeners.add(listener);
    }
    
    /**
     * Notify listeners that the resource list has changed.
     */
    private void notifyListChange() {
        for (Runnable listener : listChangeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                logger.error("Error notifying list change listener: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Get a specific resource by URI.
     */
    public Optional<Resource> getResource(String uri) {
        return Optional.ofNullable(resources.get(uri));
    }
    
    /**
     * Get total number of resources.
     */
    public int getResourceCount() {
        return resources.size();
    }
}
