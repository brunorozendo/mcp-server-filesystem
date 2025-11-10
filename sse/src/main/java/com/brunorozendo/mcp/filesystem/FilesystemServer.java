package com.brunorozendo.mcp.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {"/sse", "/messages", "/sse/*", "/messages/*",}, asyncSupported = true)
public class FilesystemServer extends HttpServlet {

    private HttpServletSseServerTransportProvider transportProvider;

    @Override
    public void init() throws ServletException {
        try {
            transportProvider = HttpServletSseServerTransportProvider.builder()
                    .sseEndpoint("/sse")
                    .messageEndpoint("/v2/messages")
                    .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
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

}
