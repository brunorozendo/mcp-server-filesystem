# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-module Java MCP (Model Context Protocol) server providing filesystem access through three different transport mechanisms: stdio, HTTP servlet, and SSE (Server-Sent Events). All implementations share common business logic in the `tools` module and expose 10 filesystem operation tools.

## Project Structure

This is a **multi-module Gradle project** with four modules:

- **`tools/`** - Shared business logic and tool definitions (library)
- **`stdio/`** - Standalone application using stdio transport (JAR)
- **`http/`** - HTTP servlet implementation (WAR)
- **`sse/`** - Server-Sent Events servlet implementation (WAR)

## Build and Development Commands

### Building All Modules
```bash
# Clean and build all modules
./gradlew clean build

# Build without tests
./gradlew build -x test
```

### Building Individual Modules
```bash
# Build specific module
./gradlew :stdio:build
./gradlew :http:build
./gradlew :sse:build
./gradlew :tools:build
```

### Testing
```bash
# Run all tests (using Spock framework in tools module)
./gradlew test

# Run tests for specific module
./gradlew :tools:test

# Generate coverage report (Jacoco)
./gradlew :tools:test jacocoTestReport
```

### Build Artifacts
- stdio: `stdio/build/libs/stdio-1.0.0.jar` (application JAR)
- http: `http/build/libs/http-1.0.0.war` (WAR file)
- sse: `sse/build/libs/sse-1.0.0.war` (WAR file)
- tools: `tools/build/libs/tools-1.0.0.jar` (library JAR)

## Architecture

### Module Architecture

The project follows a **shared library pattern** where the `tools` module contains all business logic and the three transport modules (`stdio`, `http`, `sse`) are thin adapters that wire up the appropriate transport layer.

**Shared Components (tools module)**:

- **`Transport.java`** - Central MCP server configuration that all transport implementations use
  - Provides three static `getMcp()` methods for different transport providers:
    - `getMcp(McpServerTransportProvider)` - Generic provider
    - `getMcp(HttpServletStreamableServerTransportProvider)` - HTTP transport
    - `getMcp(HttpServletSseServerTransportProvider)` - SSE transport
  - Single `buildMcpServer()` method registers all 10 tools with their handlers
  - Server info: name="file-reader-server", version="1.0.0"
  - Server capabilities: `tools=true`, `resources(false, true)` (resources disabled, subscriptions enabled)
  - Located at: `tools/src/main/java/com/brunorozendo/mcp/filesystem/Transport.java`

- **`FileTools.java`** - All filesystem operation business logic
  - 10 tool handler methods: `readFile()`, `readMultipleFiles()`, `writeFile()`, `editFile()`, `createDirectory()`, `listDirectory()`, `directoryTree()`, `moveFile()`, `searchFiles()`, `getFileInfo()`
  - Each returns `Mono<CallToolResult>` (reactive pattern using Project Reactor)
  - Uses `handleTool()` helper for consistent error handling
  - All operations use Java NIO `Path` and `Files` APIs
  - **No path validation** - filesystem operations have no directory restrictions

- **`ToolSchemas.java`** - MCP tool schema definitions
  - Defines 11 tool schemas using `McpSchema.Tool` and `McpSchema.JsonSchema` (10 registered + 1 unregistered)
  - Each tool has: name, display name, description, input schema
  - Schema helper methods: `createSinglePathSchema()`, `createMultiPathSchema()`, `createEditFileSchema()`, etc.
  - Note: `LIST_ALLOWED_DIRECTORIES` tool schema is defined but NOT registered in Transport.buildMcpServer()

**Transport Implementations**:

1. **stdio module** (`stdio/src/main/java/.../FilesystemServer.java`)
   - Standalone Java application with `static void main()` method
   - **BUG**: Method signature is `static void main()` instead of `public static void main(String[] args)` - won't execute
   - Uses `StdioServerTransportProvider` with `JacksonMcpJsonMapper` for stdin/stdout communication
   - Calls `Transport.getMcp(transportProvider).build()` to initialize server
   - Build configuration creates fat JAR with manifest Main-Class attribute
   - Usage (after fixing main): `java -jar stdio-1.0.0.jar`

2. **http module** (`http/src/main/java/.../FilesystemServer.java`)
   - Servlet extending `HttpServlet` with `@WebServlet` annotation
   - Mapped to `/mcp`, `/mcp/*` with `asyncSupported = true`
   - Uses `HttpServletStreamableServerTransportProvider` built with:
     - `.mcpEndpoint("/mcp")`
     - `.jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))`
   - Initializes in `init()` method, delegates to `transportProvider.service(req, resp)` in `service()`
   - Has unused `doGet()` override that calls `super.doGet()`
   - Requires servlet container (Tomcat, Jetty, etc.)

3. **sse module** (`sse/src/main/java/.../FilesystemServer.java`)
   - Servlet extending `HttpServlet` with `@WebServlet` annotation
   - Mapped to `/sse`, `/messages`, `/sse/*`, `/messages/*` with `asyncSupported = true`
   - Uses `HttpServletSseServerTransportProvider` built with:
     - `.sseEndpoint("/sse")`
     - `.messageEndpoint("/v2/messages")`
     - `.jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))`
   - Initializes in `init()` method, delegates to `transportProvider.service(req, resp)` in `service()`
   - No unused method overrides
   - Requires servlet container (Tomcat, Jetty, etc.)

### Key Implementation Details

**Reactive Pattern**: All tool handlers return `Mono<CallToolResult>`. The MCP SDK uses Project Reactor for async/reactive processing.

**Error Handling**:
- The `handleTool()` wrapper in FileTools catches exceptions and converts them to `CallToolResult` with `isError=true`
- Note: `readFile()` handles its own exceptions directly without using `handleTool()`

**Edit File Tool**:
- Uses java-diff-utils (`com.github.difflib`) to generate unified diffs
- Supports `dryRun` parameter for previewing changes without writing
- Uses `ObjectMapper.convertValue()` to convert Map arguments to type-safe records
- Normalizes line endings to `\n` before matching
- Requires exact text match for each edit operation

**Search Files Tool**:
- Uses Java's `PathMatcher` with glob patterns and `SimpleFileVisitor` for recursive traversal
- Supports `excludePatterns` parameter (array of glob patterns)
- Matches both relative paths and filenames
- Skips entire directory subtrees when directory matches exclude pattern

**Read Multiple Files Tool**:
- Accepts array of paths
- If path is a directory, recursively reads all regular files in it
- If path is a file, reads it directly
- Continues processing even if individual files fail
- Separates file contents with `\n---\n` delimiter

**Transport Abstraction**: The `Transport` class provides overloaded `getMcp()` methods accepting different transport provider types, but all call the same `buildMcpServer()` to register tools, ensuring consistent behavior across all transports.

## Important Constraints

**No Path Validation**: Path validation has been intentionally removed from this implementation. Filesystem operations have no directory restrictions. This is a critical security consideration.

**Java 25**: All modules target Java 25 with toolchain configuration. Ensure your JDK matches this version.

**WAR Deployment**: The http and sse modules produce WAR files requiring a servlet container to run.

**stdio Main Method Bug**: The stdio module has `static void main()` instead of `public static void main(String[] args)`, preventing execution.

## Dependencies

Key dependencies (from tools/build.gradle):
- MCP SDK: `io.modelcontextprotocol.sdk:mcp:0.15.0`
- Servlet API: `jakarta.servlet:jakarta.servlet-api:6.1.0` (compileOnly)
- Diff Utils: `io.github.java-diff-utils:java-diff-utils:4.12`
- Jackson: `com.fasterxml.jackson.core:jackson-databind:2.19.1`
- Logging: SLF4J 2.0.17 with Logback 1.5.20
- Testing: Spock Framework 2.4-M6-groovy-4.0 with spock-reports 2.5.1

## Testing Approach

Tests are in the `tools` module using Spock Framework (Groovy-based BDD):
- Uses JUnit Platform
- Reports only FAILED, PASSED, SKIPPED events
- Coverage tracked with Jacoco (`jacocoTestReport` runs after tests)
- Currently no test files exist in the repository

## Available Tools

The server exposes **10 registered tools**. Note: An 11th tool (`list_allowed_directories`) is defined in ToolSchemas.java but NOT registered in Transport.java, so it's unavailable to clients.

### Registered Tools (Available to Clients)

1. **read_file** - Read complete contents of a single file
   - Parameters: `path` (string)

2. **read_multiple_files** - Read multiple files simultaneously (directories recursively read all files)
   - Parameters: `paths` (array of strings)

3. **write_file** - Create new file or overwrite existing
   - Parameters: `path` (string), `content` (string)

4. **edit_file** - Make line-based edits with diff preview
   - Parameters: `path` (string), `edits` (array of {oldText, newText}), `dryRun` (boolean, optional)

5. **create_directory** - Create directories (including nested)
   - Parameters: `path` (string)

6. **list_directory** - List directory contents with [FILE]/[DIR] prefixes
   - Parameters: `path` (string)

7. **directory_tree** - Recursive JSON tree view of directories
   - Parameters: `path` (string)

8. **move_file** - Move or rename files/directories
   - Parameters: `source` (string), `destination` (string)

9. **search_files** - Recursive search with glob patterns
   - Parameters: `path` (string), `pattern` (string), `excludePatterns` (array of strings, optional)

10. **get_file_info** - File metadata (size, timestamps, permissions)
    - Parameters: `path` (string)