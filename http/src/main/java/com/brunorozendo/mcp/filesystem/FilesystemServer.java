package com.brunorozendo.mcp.filesystem;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {"/mcp", "/mcp/*" }, asyncSupported = true)
public class FilesystemServer extends HttpServlet {

    private  HttpServletStreamableServerTransportProvider transportProvider;

    @Override
    public void init() throws ServletException {
        try {
            transportProvider = HttpServletStreamableServerTransportProvider.builder()
                    .mcpEndpoint("/mcp")
                    .jsonMapper(McpJsonDefaults.getMapper())
                    .build();
            Transport.getMcp(transportProvider).build();
        } catch (Exception e) {
            throw new ServletException("Failed to initialize MCP server", e);
        }
    }


    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.transportProvider.service(req, resp);
    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doGet(req, resp);
    }
}
