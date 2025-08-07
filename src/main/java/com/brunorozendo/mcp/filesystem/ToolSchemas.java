package com.brunorozendo.mcp.filesystem;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;

/**
 * A utility class to define the schema for each tool exposed by the server.
 * This mirrors the Zod schemas from the TypeScript implementation.
 */
public final class ToolSchemas {

    private ToolSchemas() {}

    public static final McpSchema.Tool READ_FILE = new McpSchema.Tool(
        "read_file",
        "Read the complete contents of a file from the file system. " +
        "Handles various text encodings and provides detailed error messages " +
        "if the file cannot be read. Use this tool when you need to examine " +
        "the contents of a single file. Only works within allowed directories.",
        createSinglePathSchema(),
        null
    );

    public static final McpSchema.Tool READ_MULTIPLE_FILES = new McpSchema.Tool(
        "read_multiple_files",
        "Read the contents of multiple files simultaneously. This is more " +
        "efficient than reading files one by one when you need to analyze " +
        "or compare multiple files. Each file's content is returned with its " +
        "path as a reference. Failed reads for individual files won't stop " +
        "the entire operation. Only works within allowed directories.",
        createMultiPathSchema(),
        null
    );

    public static final McpSchema.Tool WRITE_FILE = new McpSchema.Tool(
        "write_file",
        "Create a new file or completely overwrite an existing file with new content. " +
        "Use with caution as it will overwrite existing files without warning. " +
        "Handles text content with proper encoding. Only works within allowed directories.",
        createPathAndContentSchema(),
        null
    );

    public static final McpSchema.Tool EDIT_FILE = new McpSchema.Tool(
        "edit_file",
        "Make line-based edits to a text file. Each edit replaces exact line sequences " +
        "with new content. Returns a git-style diff showing the changes made. " +
        "Only works within allowed directories.",
        createEditFileSchema(),
        null
    );

    public static final McpSchema.Tool CREATE_DIRECTORY = new McpSchema.Tool(
        "create_directory",
        "Create a new directory or ensure a directory exists. Can create multiple " +
        "nested directories in one operation. If the directory already exists, " +
        "this operation will succeed silently. Perfect for setting up directory " +
        "structures for projects or ensuring required paths exist. Only works within allowed directories.",
        createSinglePathSchema(),
        null
    );

    public static final McpSchema.Tool LIST_DIRECTORY = new McpSchema.Tool(
        "list_directory",
        "Get a detailed listing of all files and directories in a specified path. " +
        "Results clearly distinguish between files and directories with [FILE] and [DIR] " +
        "prefixes. This tool is essential for understanding directory structure and " +
        "finding specific files within a directory. Only works within allowed directories.",
        createSinglePathSchema(),
        null
    );

    public static final McpSchema.Tool DIRECTORY_TREE = new McpSchema.Tool(
        "directory_tree",
        "Get a recursive tree view of files and directories as a JSON structure. " +
        "Each entry includes 'name', 'type' (file/directory), and 'children' for directories. " +
        "Files have no children array, while directories always have a children array (which may be empty). " +
        "The output is formatted with 2-space indentation for readability. Only works within allowed directories.",
        createSinglePathSchema(),
        null
    );

    public static final McpSchema.Tool MOVE_FILE = new McpSchema.Tool(
        "move_file",
        "Move or rename files and directories. Can move files between directories " +
        "and rename them in a single operation. If the destination exists, the " +
        "operation will fail. Works across different directories and can be used " +
        "for simple renaming within the same directory. Both source and destination must be within allowed directories.",
        createSourceDestSchema(),
        null
    );

    public static final McpSchema.Tool SEARCH_FILES = new McpSchema.Tool(
        "search_files",
        "Recursively search for files and directories matching a glob pattern. " +
        "Searches through all subdirectories from the starting path. Supports glob patterns " +
        "like '*.java' or '*.{java,xml}'. Returns full paths to all " +
        "matching items. Great for finding files when you don't know their exact location. " +
        "Only searches within allowed directories.",
        createSearchSchema(),
        null
    );

    public static final McpSchema.Tool GET_FILE_INFO = new McpSchema.Tool(
        "get_file_info",
        "Retrieve detailed metadata about a file or directory. Returns comprehensive " +
        "information including size, creation time, last modified time, permissions, " +
        "and type. This tool is perfect for understanding file characteristics " +
        "without reading the actual content. Only works within allowed directories.",
        createSinglePathSchema(),
        null
    );

    public static final McpSchema.Tool LIST_ALLOWED_DIRECTORIES = new McpSchema.Tool(
        "list_allowed_directories",
        "Returns the list of directories that this server is allowed to access. " +
        "Use this to understand which directories are available before trying to access files.",
        createEmptySchema(),
        null
    );

    private static McpSchema.JsonSchema createSinglePathSchema() {
        return new McpSchema.JsonSchema("object", Map.of("path", Map.of("type", "string")), List.of("path"), false, null, null);
    }

    private static McpSchema.JsonSchema createMultiPathSchema() {
        return new McpSchema.JsonSchema("object", Map.of("paths", Map.of("type", "array", "items", Map.of("type", "string"))), List.of("paths"), false, null, null);
    }

    private static McpSchema.JsonSchema createPathAndContentSchema() {
        return new McpSchema.JsonSchema("object", Map.of(
                "path", Map.of("type", "string"),
                "content", Map.of("type", "string")
        ), List.of("path", "content"), false, null, null);
    }

    private static McpSchema.JsonSchema createSourceDestSchema() {
        return new McpSchema.JsonSchema("object", Map.of(
                "source", Map.of("type", "string"),
                "destination", Map.of("type", "string")
        ), List.of("source", "destination"), false, null, null);
    }

    private static McpSchema.JsonSchema createSearchSchema() {
        return new McpSchema.JsonSchema("object", Map.of(
                "path", Map.of("type", "string"),
                "pattern", Map.of("type", "string"),
                "excludePatterns", Map.of("type", "array", "items", Map.of("type", "string"))
        ), List.of("path", "pattern"), false, null, null);
    }

    private static McpSchema.JsonSchema createEditFileSchema() {
        Map<String, Object> editOperationSchema = Map.of(
            "type", "object",
            "properties", Map.of(
                "oldText", Map.of("type", "string", "description", "Text to search for - must match exactly"),
                "newText", Map.of("type", "string", "description", "Text to replace with")
            ),
            "required", List.of("oldText", "newText")
        );

        return new McpSchema.JsonSchema("object", Map.of(
                "path", Map.of("type", "string"),
                "edits", Map.of("type", "array", "items", editOperationSchema),
                "dryRun", Map.of("type", "boolean", "description", "Preview changes using git-style diff format")
        ), List.of("path", "edits"), false, null, null);
    }

    private static McpSchema.JsonSchema createEmptySchema() {
        return new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null);
    }
}