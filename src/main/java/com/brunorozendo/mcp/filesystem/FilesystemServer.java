package com.brunorozendo.mcp.filesystem;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class FilesystemServer {

    private static final Logger logger = LoggerFactory.getLogger(FilesystemServer.class);

    public static void main(String[] args) {
        if (args.length == 0) {
            logger.info("Usage: java -jar <jar-file> <allowed-directory> [additional-directories...]");
            System.exit(1);
        }

        List<String> allowedDirs = Arrays.asList(args);
        logger.info("Secure MCP Filesystem Server starting...");
        logger.info("Allowed directories: {}", allowedDirs);

        // The business logic for all tools and resources
        FileTools fileTools = new FileTools(allowedDirs);

        // The transport provider for stdio
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider();

        // Define the resource specification using a template URI.
        // This allows the server to handle requests for any file.
        McpSchema.Resource fileResourceTemplate = new McpSchema.Resource(
            "file://{path}", // The URI template
            "File System Resource",
            "Provides access to the content of any file within the allowed directories.",
            null, // MimeType is determined dynamically in the handler
            null  // No special annotations
        );

        McpServerFeatures.SyncResourceSpecification resourceSpec = new McpServerFeatures.SyncResourceSpecification(
            fileResourceTemplate,
            fileTools::readResource // Link to the handler method
        );

        // Build the synchronous MCP server
        McpServer.sync(transportProvider)
            .serverInfo("java-secure-filesystem-server", "0.7.1")
            // Announce that we support both tools AND resources.
            // The booleans indicate we support dynamic changes and subscriptions.
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(false)
                .resources(true, true)
                .build())
            // Register the resource handler
            .resources(resourceSpec)
            // Register all the tool handlers
            .tool(ToolSchemas.READ_FILE, fileTools::readFile)
            .tool(ToolSchemas.READ_MULTIPLE_FILES, fileTools::readMultipleFiles)
            .tool(ToolSchemas.WRITE_FILE, fileTools::writeFile)
            .tool(ToolSchemas.EDIT_FILE, fileTools::editFile)
            .tool(ToolSchemas.CREATE_DIRECTORY, fileTools::createDirectory)
            .tool(ToolSchemas.LIST_DIRECTORY, fileTools::listDirectory)
            .tool(ToolSchemas.DIRECTORY_TREE, fileTools::directoryTree)
            .tool(ToolSchemas.MOVE_FILE, fileTools::moveFile)
            .tool(ToolSchemas.SEARCH_FILES, fileTools::searchFiles)
            .tool(ToolSchemas.GET_FILE_INFO, fileTools::getFileInfo)
            .tool(ToolSchemas.LIST_ALLOWED_DIRECTORIES, fileTools::listAllowedDirectories)
            .build();

        logger.info("Server connected and running on stdio.");
    }
}
