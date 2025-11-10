# Java MCP Filesystem Server

A Model Context Protocol (MCP) server implementation in Java that provides filesystem access to AI assistants. This multi-module project offers three different transport mechanisms (stdio, HTTP, SSE) all sharing common business logic for file operations.

## Features

### Multiple Transport Options
- **stdio**: Standalone application for command-line integration (Claude Desktop, etc.)
- **HTTP**: Servlet-based implementation for HTTP communication
- **SSE**: Server-Sent Events servlet for real-time streaming

### Comprehensive File Operations
The server exposes 10 MCP tools for filesystem manipulation:

- **`read_file`**: Read the complete contents of a single file
- **`read_multiple_files`**: Efficiently read multiple files in one operation
- **`write_file`**: Create new files or overwrite existing ones
- **`edit_file`**: Make line-based edits with diff preview support
- **`create_directory`**: Create single or nested directory structures
- **`list_directory`**: List contents of a directory with type indicators
- **`directory_tree`**: Get a recursive JSON tree view of directories
- **`move_file`**: Move or rename files and directories
- **`search_files`**: Recursively search for files matching glob patterns
- **`get_file_info`**: Retrieve detailed file metadata (size, timestamps, permissions)


## Requirements

- Java 25
- Gradle 8.x (for building from source)
- Servlet container (Tomcat, Jetty, etc.) for HTTP/SSE modules

## Building from Source

1. Clone the repository:
```bash
git clone <repository-url>
cd mcp-server-filesystem
```

2. Build all modules:
```bash
./gradlew clean build
```

This creates the following artifacts:
- **stdio**: `stdio/build/libs/stdio-1.0.0.jar` - Standalone application JAR
- **http**: `http/build/libs/http-1.0.0.war` - HTTP servlet WAR file
- **sse**: `sse/build/libs/sse-1.0.0.war` - SSE servlet WAR file
- **tools**: `tools/build/libs/tools-1.0.0.jar` - Shared library JAR

3. Build individual modules:
```bash
./gradlew :stdio:build
./gradlew :http:build
./gradlew :sse:build
```

## Usage

### Option 1: stdio Transport (Standalone)

The stdio transport uses stdin/stdout for communication. Note: The current implementation has `static void main()` which needs to be changed to `public static void main(String[] args)` to be executable.

Run the standalone application:

```bash
java -jar stdio/build/libs/stdio-1.0.0.jar
```

#### Configuring with Claude Desktop

Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/stdio-1.0.0.jar"
      ]
    }
  }
}
```

### Option 2: HTTP Transport (Servlet)

1. Deploy the WAR file to your servlet container:
```bash
cp http/build/libs/http-1.0.0.war $TOMCAT_HOME/webapps/
```

2. The HTTP endpoint will be available at:
```
http://localhost:8080/http-1.0.0/mcp
```

### Option 3: SSE Transport (Servlet)

1. Deploy the WAR file to your servlet container:
```bash
cp sse/build/libs/sse-1.0.0.war $TOMCAT_HOME/webapps/
```

2. The SSE endpoints will be available at:
```
SSE endpoint: http://localhost:8080/sse-1.0.0/sse
Messages endpoint: http://localhost:8080/sse-1.0.0/messages
```

The servlet is mapped to `/sse`, `/messages`, `/sse/*`, and `/messages/*`.

## Architecture

### Project Structure

```
mcp-server-filesystem/
├── tools/                       # Shared library module
│   └── src/main/java/com/brunorozendo/mcp/filesystem/
│       ├── Transport.java       # Central MCP server configuration
│       ├── FileTools.java       # Business logic for all file operations
│       └── ToolSchemas.java     # MCP tool schema definitions
├── stdio/                       # Standalone stdio transport
│   └── src/main/java/com/brunorozendo/mcp/filesystem/
│       └── FilesystemServer.java
├── http/                        # HTTP servlet transport
│   └── src/main/java/com/brunorozendo/mcp/filesystem/
│       └── FilesystemServer.java
├── sse/                         # SSE servlet transport
│   └── src/main/java/com/brunorozendo/mcp/filesystem/
│       └── FilesystemServer.java
├── settings.gradle              # Multi-module configuration
└── README.md
```

### Module Dependencies

- **tools** - Core library with all business logic
- **stdio, http, sse** - Thin transport adapters that depend on tools

### Development Commands

```bash
# Run all tests
./gradlew test

# Run tests for tools module
./gradlew :tools:test

# Build without tests
./gradlew build -x test

# Generate test coverage report
./gradlew :tools:test jacocoTestReport
```

### Key Dependencies

- `io.modelcontextprotocol.sdk:mcp:0.15.0` - MCP SDK for Java
- `jakarta.servlet:jakarta.servlet-api:6.1.0` - Servlet API
- `io.github.java-diff-utils:java-diff-utils:4.12` - Diff generation for edit operations
- `com.fasterxml.jackson.core:jackson-databind:2.19.1` - JSON processing
- Spock Framework 2.4-M6-groovy-4.0 - Testing

## Security Considerations

**IMPORTANT**: This implementation has **no path validation** or directory restrictions. All filesystem operations are unrestricted and limited only by the permissions of the user running the server.

1. **No Path Restrictions**: File operations can access any path the user has permissions for
2. **User Permissions**: The server runs with the same permissions as the user who starts it
3. **Production Use**: Consider implementing path validation before deploying in production environments

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
    "pattern": "*.java",
    "excludePatterns": ["**/build/**", "**/target/**"]
  }
}
```

## Troubleshooting

### Common Issues

1. **Server won't start**: Verify Java 25 is installed and in your PATH
2. **WAR deployment fails**: Ensure your servlet container supports Jakarta Servlet API 6.1
3. **Permission errors**: The server can only access files the running user has permissions for

### Debugging

Check application logs (stdout/stderr for stdio, container logs for HTTP/SSE) for error messages.

## Version History

- **1.0.0** - Multi-module architecture with stdio, HTTP, and SSE transports
- **0.7.2** - Previous version with single module implementation

## Contributing

Contributions are welcome! Please ensure:
1. Code follows Java naming conventions
2. New tools are added to the shared `tools` module
3. Tool schemas are properly defined in `ToolSchemas.java`
4. Changes are tested with an MCP client
5. Tests are written using Spock Framework in the `tools` module

## License

[Specify your license here]

## Author

Bruno Rozendo

## Acknowledgments

- Built with the [Model Context Protocol SDK](https://github.com/modelcontextprotocol/mcp) for Java
- Inspired by the TypeScript reference implementation
