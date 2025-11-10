package com.brunorozendo.mcp.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;

import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FilesystemServer {

    private static final Logger logger = LoggerFactory.getLogger(FilesystemServer.class);

    static void main() {
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(new JacksonMcpJsonMapper(new ObjectMapper()));
        Transport.getMcp(transportProvider).build();

    }



}
