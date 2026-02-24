package com.brunorozendo.mcp.filesystem;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FilesystemServer {

    private static final Logger logger = LoggerFactory.getLogger(FilesystemServer.class);

    public static void main(String[] args) {
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        Transport.getMcp(transportProvider).build();
    }



}
