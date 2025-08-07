package com.brunorozendo.mcp.filesystem;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        
        // Initialize the resource manager
        PathValidator pathValidator = new PathValidator(allowedDirs);
        ResourceManager resourceManager = new ResourceManager(pathValidator);

        // Connect file tools with resource manager for file system event handling
        fileTools.setResourceChangeCallback(uri -> {
            // Extract path from URI
            String pathStr = uri.substring("file://".length());
            Path path = Paths.get(pathStr);
            
            // Determine the type of change and update resource manager
            if (Files.exists(path)) {
                if (Files.isRegularFile(path)) {
                    resourceManager.onFileCreated(path);
                } else {
                    resourceManager.onFileModified(path);
                }
            } else {
                resourceManager.onFileDeleted(path);
            }
        });

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

        // Create a tool to list resources as a workaround
        McpSchema.Tool listResourcesTool = new McpSchema.Tool(
            "list_resources",
            "List all available file resources that can be accessed via the resources API",
            new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null),
            null
        );

        // Build the synchronous MCP server
        McpServer.sync(transportProvider)
            .serverInfo("java-secure-filesystem-server", "0.7.2")
            // Announce that we support both tools AND resources.
            // The booleans indicate we support dynamic changes and subscriptions.
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(false)
                .resources(true, true) // listChanged support, subscription support
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
            // Add the list_resources tool
            .tool(listResourcesTool, (exchange, toolArgs) -> {
                // This is a workaround to expose resource listing via a tool
                List<McpSchema.Resource> resources = resourceManager.getAllResources();
                StringBuilder output = new StringBuilder();
                output.append("Available Resources (").append(resources.size()).append(" total):\n\n");
                
                for (McpSchema.Resource resource : resources) {
                    output.append("- URI: ").append(resource.uri()).append("\n");
                    output.append("  Name: ").append(resource.name()).append("\n");
                    output.append("  Type: ").append(resource.mimeType()).append("\n");
                    output.append("  Description: ").append(resource.description()).append("\n\n");
                }
                
                return new McpSchema.CallToolResult(output.toString(), false);
            })
            .build();

        logger.info("Server connected and running on stdio.");
        logger.info("Resources support: The server tracks {} resources", resourceManager.getResourceCount());
        logger.info("Note: Full resources/list endpoint support requires MCP SDK update.");
        logger.info("Use the 'list_resources' tool as a workaround to see available resources.");
    }
}
