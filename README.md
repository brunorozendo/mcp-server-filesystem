# Java MCP Filesystem Server

A secure Model Context Protocol (MCP) server implementation in Java that provides controlled filesystem access to AI assistants. This server enables AI models to safely read, write, and manipulate files within specified directories while preventing unauthorized access through path validation.

## Features

### Security-First Design
- **Path Validation**: All file operations are restricted to explicitly allowed directories
- **Path Traversal Protection**: Prevents `../` attacks through path normalization and validation
- **Symbolic Link Resolution**: Safely handles symbolic links by checking their real paths

### Comprehensive File Operations
The server exposes 11 MCP tools for filesystem manipulation:

- **`read_file`**: Read the complete contents of a single file
- **`read_multiple_files`**: Efficiently read multiple files in one operation
- **`write_file`**: Create new files or overwrite existing ones
- **`edit_file`**: Make line-based edits with diff preview support
- **`create_directory`**: Create single or nested directory structures
- **`list_directory`**: List contents of a directory with type indicators
- **`directory_tree`**: Get a recursive JSON tree view of directories
- **`move_file`**: Move or rename files and directories
- **`search_files`**: Recursively search for files matching patterns
- **`get_file_info`**: Retrieve detailed file metadata (size, timestamps, permissions)
- **`list_allowed_directories`**: Show which directories the server can access

### MCP Resources Support
In addition to tools, the server also supports MCP resources, allowing clients to access file contents through `file://` URIs.

## Requirements

- Java 21 or higher
- Gradle 8.x (for building from source)

## Installation

### Option 1: Download Pre-built JAR

Download the latest release JAR file from the releases page (if available).

### Option 2: Build from Source

1. Clone the repository:
```bash
git clone <repository-url>
cd java-mcp-filesystem-server
```

2. Build the project:
```bash
./gradlew clean build
```

This creates a fat JAR with all dependencies at:
```
build/libs/filesystem-mcp-server-all.jar
```

## Usage

### Running the Server

The server requires at least one allowed directory as a command-line argument:

```bash
java -jar build/libs/filesystem-mcp-server-all.jar /path/to/allowed/directory [additional directories...]
```

Example with multiple directories:
```bash
java -jar build/libs/filesystem-mcp-server-all.jar /Users/myuser/documents /Users/myuser/projects
```

### Configuring with Claude Desktop

To use this server with Claude Desktop, add it to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/filesystem-mcp-server-all.jar",
        "/Users/myuser/documents",
        "/Users/myuser/projects"
      ]
    }
  }
}
```

### Configuring with Other MCP Clients

The server communicates over stdio (standard input/output), making it compatible with any MCP client that supports stdio transport. Provide the command and arguments as shown above.

## Development

### Project Structure

```
java-mcp-filesystem-server/
├── src/main/java/com/brunorozendo/mcp/filesystem/
│   ├── FilesystemServer.java    # Main server entry point
│   ├── FileTools.java           # Business logic for all file operations
│   ├── PathValidator.java       # Security component for path validation
│   └── ToolSchemas.java         # MCP tool schema definitions
├── build.gradle                 # Gradle build configuration
└── README.md                    # This file
```

### Building for Development

```bash
# Run tests (if any)
./gradlew test

# Build without running tests
./gradlew build -x test

# Create distribution archives
./gradlew distZip distTar
```

### Key Dependencies

- `io.modelcontextprotocol.sdk:mcp:0.10.0` - MCP SDK for Java
- `io.github.java-diff-utils:java-diff-utils:4.12` - For generating diffs in edit operations
- `com.fasterxml.jackson.core:jackson-databind:2.15.2` - JSON processing

## Security Considerations

1. **Directory Access**: Only directories explicitly passed as arguments can be accessed
2. **Path Validation**: Every path is validated before any operation
3. **No Elevation**: The server runs with the same permissions as the user who starts it
4. **Symbolic Links**: Resolved to their real paths to prevent escaping allowed directories

## Tool Examples

### Reading a File
```json
{
  "tool": "read_file",
  "parameters": {
    "path": "/Users/myuser/documents/example.txt"
  }
}
```

### Editing a File with Preview
```json
{
  "tool": "edit_file",
  "parameters": {
    "path": "/Users/myuser/documents/example.txt",
    "edits": [
      {
        "oldText": "Hello World",
        "newText": "Hello MCP"
      }
    ],
    "dryRun": true
  }
}
```

### Searching for Files
```json
{
  "tool": "search_files",
  "parameters": {
    "path": "/Users/myuser/projects",
    "pattern": ".java",
    "excludePatterns": ["build", "target"]
  }
}
```

## Troubleshooting

### Common Issues

1. **"Access denied" errors**: Ensure the path is within an allowed directory
2. **"Path is outside of allowed directories"**: Check that you've included the parent directory in the server arguments
3. **Server won't start**: Verify Java 21+ is installed and in your PATH

### Debugging

Enable verbose logging by modifying the server to include debug output, or check stderr for error messages.

## Version History

- **0.7.2** - Current version with dependency fixes and full MCP tools support
- Previous versions available in git history

## Contributing

Contributions are welcome! Please ensure:
1. Code follows Java naming conventions
2. Security validations are maintained
3. New tools include proper schema definitions
4. Changes are tested with an MCP client

## License

[Specify your license here]

## Author

Bruno Rozendo

## Acknowledgments

- Built with the [Model Context Protocol SDK](https://github.com/modelcontextprotocol/mcp) for Java
- Inspired by the TypeScript reference implementation
