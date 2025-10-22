package com.brunorozendo.mcp.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {"/mcp", "/mcp/", "/mcp/*"}, asyncSupported = true)
public class FilesystemServer extends HttpServlet {

    private  HttpServletStreamableServerTransportProvider transportProvider;

    @Override
    public void init() throws ServletException {
        try {

            FileTools fileTools = new FileTools();

            transportProvider = HttpServletStreamableServerTransportProvider.builder()
                    .mcpEndpoint("/mcp")
                    .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                    .build();

            McpServer.async(transportProvider)
                .serverInfo("file-reader-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, true)  // Enable resource support with subscriptions
                        .build())
                .toolCall(ToolSchemas.READ_FILE, fileTools::readFile)
                .toolCall(ToolSchemas.READ_MULTIPLE_FILES, fileTools::readMultipleFiles)
                .toolCall(ToolSchemas.WRITE_FILE, fileTools::writeFile)
                .toolCall(ToolSchemas.EDIT_FILE, fileTools::editFile)
                .toolCall(ToolSchemas.CREATE_DIRECTORY, fileTools::createDirectory)
                .toolCall(ToolSchemas.LIST_DIRECTORY, fileTools::listDirectory)
                .toolCall(ToolSchemas.DIRECTORY_TREE, fileTools::directoryTree)
                .toolCall(ToolSchemas.MOVE_FILE, fileTools::moveFile)
                .toolCall(ToolSchemas.SEARCH_FILES, fileTools::searchFiles)
                .toolCall(ToolSchemas.GET_FILE_INFO, fileTools::getFileInfo)
                .build();


        } catch (Exception e) {
            throw new ServletException("Failed to initialize MCP server", e);
        }
    }


    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.transportProvider.service(req, resp);
    }
}
