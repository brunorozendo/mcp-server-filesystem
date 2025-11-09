# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java-based MCP (Model Context Protocol) server that provides filesystem access via HTTP servlet. It's packaged as a WAR file and exposes 10 filesystem operation tools through the MCP protocol using HTTP servlet transport.

## Build and Development Commands

### Building
```bash
# Clean and build the project (creates WAR file)
./gradlew clean build

# Build without tests
./gradlew build -x test
```

The built artifact is located at: `build/libs/filesystem-mcp-server-1.0.0.war`

### Testing
```bash
# Run all tests (using Spock framework)
./gradlew test

# Generate coverage report (Jacoco)
./gradlew test jacocoTestReport
```

## Architecture

### Core Components

**FilesystemServer** (`src/main/java/com/brunorozendo/mcp/filesystem/FilesystemServer.java`)
- Servlet entry point (`@WebServlet` mapped to `/mcp`, `/mcp/`, `/mcp/*`)
- Initializes MCP server using `HttpServletStreamableServerTransportProvider` in `init()` method
- Registers 10 filesystem tools with their handlers
- Server capabilities: tools enabled, resources enabled with subscriptions
- Delegates all HTTP requests to `transportProvider.service(req, resp)`

**FileTools** (`src/main/java/com/brunorozendo/mcp/filesystem/FileTools.java`)
- Contains business logic for all filesystem operations
- Each tool returns `Mono<CallToolResult>` (reactive pattern using Project Reactor)
- Uses `handleTool()` helper for consistent error handling
- All operations use Java NIO `Path` and `Files` APIs
- **Critical**: Path validation has been removed - all commented-out `PathValidator` calls show this was intentional

**ToolSchemas** (`src/main/java/com/brunorozendo/mcp/filesystem/ToolSchemas.java`)
- Defines MCP tool schemas using `McpSchema.Tool` and `McpSchema.JsonSchema`
- Each tool has: name, display name, description, input schema
- 10 tools: read_file, read_multiple_files, write_file, edit_file, create_directory, list_directory, directory_tree, move_file, search_files, get_file_info

### Key Implementation Details

**HTTP Servlet Transport**: This server uses `HttpServletStreamableServerTransportProvider` instead of stdio transport. The MCP endpoint is `/mcp` and the servlet is configured with `asyncSupported = true`.

**Reactive Pattern**: All tool handlers return `Mono<CallToolResult>`. The MCP SDK uses Project Reactor for async/reactive processing.

**Error Handling**: The `handleTool()` wrapper in FileTools catches exceptions and converts them to `CallToolResult` with `isError=true`.

**Edit File Tool**: Uses java-diff-utils (`com.github.difflib`) to generate unified diffs. Supports `dryRun` parameter for previewing changes before applying.

**Search Files Tool**: Uses Java's `PathMatcher` with glob patterns and `SimpleFileVisitor` for recursive file system traversal.

## Important Constraints

**No Path Validation**: Path validation has been intentionally removed from this implementation. All `PathValidator` references in FileTools.java are commented out. This means filesystem operations have no directory restrictions. This is a critical security consideration.

**Java 25**: The project targets Java 25 with toolchain configuration. This is a very recent Java version - ensure your JDK matches.

**WAR Deployment**: This is packaged as a WAR file, requiring a servlet container (Tomcat, Jetty, etc.) to run.

## Dependencies

Key dependencies from build.gradle:
- MCP SDK: `io.modelcontextprotocol.sdk:mcp:0.15.0`
- Servlet API: `jakarta.servlet:jakarta.servlet-api:6.1.0` (compileOnly)
- Diff Utils: `io.github.java-diff-utils:java-diff-utils:4.12`
- Jackson: `com.fasterxml.jackson.core:jackson-databind:2.19.1`
- Logging: SLF4J with Logback
- Testing: Spock Framework 2.4-M6-groovy-4.0 with spock-reports

## Testing Approach

Tests are written using Spock Framework (Groovy-based BDD). Test configuration:
- Uses JUnit Platform
- Reports only FAILED, PASSED, SKIPPED events
- Coverage tracked with Jacoco (`jacocoTestReport` runs after tests)
