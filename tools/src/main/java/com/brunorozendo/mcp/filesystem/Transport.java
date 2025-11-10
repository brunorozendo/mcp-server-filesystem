package com.brunorozendo.mcp.filesystem;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;

public class Transport {


    public static McpServer.AsyncSpecification<?> getMcp(McpServerTransportProvider transportProvider){
        return buildMcpServer(McpServer.async(transportProvider));
    }

    public static McpServer.AsyncSpecification<?> getMcp(HttpServletStreamableServerTransportProvider transportProvider) {
        return buildMcpServer(McpServer.async(transportProvider));
    }

    public static McpServer.AsyncSpecification<?> getMcp(HttpServletSseServerTransportProvider transportProvider) {
        return buildMcpServer(McpServer.async(transportProvider));
    }

    private static McpServer.AsyncSpecification<?> buildMcpServer(McpServer.AsyncSpecification<?> server) {
        return server
                .serverInfo("file-reader-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, true)
                        .build())
                .toolCall(ToolSchemas.READ_FILE, FileTools::readFile)
                .toolCall(ToolSchemas.READ_MULTIPLE_FILES, FileTools::readMultipleFiles)
                .toolCall(ToolSchemas.WRITE_FILE, FileTools::writeFile)
                .toolCall(ToolSchemas.EDIT_FILE, FileTools::editFile)
                .toolCall(ToolSchemas.CREATE_DIRECTORY, FileTools::createDirectory)
                .toolCall(ToolSchemas.LIST_DIRECTORY, FileTools::listDirectory)
                .toolCall(ToolSchemas.DIRECTORY_TREE, FileTools::directoryTree)
                .toolCall(ToolSchemas.MOVE_FILE, FileTools::moveFile)
                .toolCall(ToolSchemas.SEARCH_FILES, FileTools::searchFiles)
                .toolCall(ToolSchemas.GET_FILE_INFO, FileTools::getFileInfo);
    }
}
