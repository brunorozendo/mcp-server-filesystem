package com.brunorozendo.mcp.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Security component to ensure all file operations are constrained to allowed directories.
 * Prevents path traversal attacks by normalizing and checking real paths.
 */
public class PathValidator {

    private final List<Path> allowedDirs;

    public PathValidator(List<String> allowedDirPaths) {
        this.allowedDirs = allowedDirPaths.stream()
                .map(p -> Paths.get(p).toAbsolutePath().normalize())
                .collect(Collectors.toList());
    }

    /**
     * Validates if a given path is within the allowed directories.
     * Handles symbolic links by checking their real path.
     *
     * @param requestedPath The path to validate.
     * @return The validated, absolute, and normalized Path object.
     * @throws SecurityException if the path is outside the allowed directories.
     * @throws IOException if there's an issue resolving the path.
     */
    public Path validate(String requestedPath) throws SecurityException, IOException {
        if (requestedPath == null || requestedPath.isBlank()) {
            throw new SecurityException("Path cannot be null or empty.");
        }

        Path absolutePath = Paths.get(requestedPath).toAbsolutePath().normalize();

        // For existing files/dirs, check their real path to resolve symlinks
        if (Files.exists(absolutePath)) {
            Path realPath = absolutePath.toRealPath();
            if (isWithinAllowedDirs(realPath)) {
                return realPath;
            }
        } else {
            // For new files/dirs, check the real path of the parent directory
            Path parent = absolutePath.getParent();
            if (parent == null) {
                 throw new SecurityException("Cannot create a file in the root directory.");
            }
            if (Files.exists(parent)) {
                Path realParentPath = parent.toRealPath();
                if (isWithinAllowedDirs(realParentPath)) {
                    // Return the intended absolute path, not the parent
                    return absolutePath;
                }
            } else {
                 throw new SecurityException("Parent directory does not exist: " + parent);
            }
        }

        throw new SecurityException("Access denied. Path is outside of allowed directories: " + requestedPath);
    }

    private boolean isWithinAllowedDirs(Path path) {
        return allowedDirs.stream().anyMatch(path::startsWith);
    }

    public List<String> getAllowedDirectoriesAsString() {
        return allowedDirs.stream().map(Path::toString).collect(Collectors.toList());
    }
}
