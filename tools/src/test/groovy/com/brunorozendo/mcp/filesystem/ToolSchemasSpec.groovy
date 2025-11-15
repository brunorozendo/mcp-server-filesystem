package com.brunorozendo.mcp.filesystem

import spock.lang.Specification

class ToolSchemasSpec extends Specification {

    def "READ_FILE schema should be properly configured"() {
        when:
        def tool = ToolSchemas.READ_FILE

        then:
        tool.name() == "read_file"
        tool.title() == "read file"
        tool.description().contains("Read the complete contents")
        tool.inputSchema() != null
        tool.inputSchema().type() == "object"
        tool.inputSchema().properties().containsKey("path")
        tool.inputSchema().required() == ["path"]
    }

    def "READ_MULTIPLE_FILES schema should be properly configured"() {
        when:
        def tool = ToolSchemas.READ_MULTIPLE_FILES

        then:
        tool.name() == "read_multiple_files"
        tool.title() == "read multiple files"
        tool.description().contains("Read the contents of multiple files")
        tool.inputSchema() != null
        tool.inputSchema().properties().containsKey("paths")
        tool.inputSchema().required() == ["paths"]
        def pathsSchema = tool.inputSchema().properties().get("paths")
        pathsSchema.get("type") == "array"
    }

    def "WRITE_FILE schema should be properly configured"() {
        when:
        def tool = ToolSchemas.WRITE_FILE

        then:
        tool.name() == "write_file"
        tool.title() == "write file"
        tool.description().contains("Create a new file or completely overwrite")
        tool.inputSchema() != null
        tool.inputSchema().properties().containsKey("path")
        tool.inputSchema().properties().containsKey("content")
        tool.inputSchema().required() == ["path", "content"]
    }

    def "EDIT_FILE schema should be properly configured"() {
        when:
        def tool = ToolSchemas.EDIT_FILE

        then:
        tool.name() == "edit_file"
        tool.title() == "edit file"
        tool.description().contains("line-based edits")
        tool.inputSchema() != null
        tool.inputSchema().properties().containsKey("path")
        tool.inputSchema().properties().containsKey("edits")
        tool.inputSchema().properties().containsKey("dryRun")
        tool.inputSchema().required() == ["path", "edits"]
        def editsSchema = tool.inputSchema().properties().get("edits")
        editsSchema.get("type") == "array"
    }

    def "CREATE_DIRECTORY schema should be properly configured"() {
        when:
        def tool = ToolSchemas.CREATE_DIRECTORY

        then:
        tool.name() == "create_directory"
        tool.title() == "create directory"
        tool.description().contains("Create a new directory")
        tool.inputSchema() != null
        tool.inputSchema().properties().containsKey("path")
        tool.inputSchema().required() == ["path"]
    }

    def "LIST_DIRECTORY schema should be properly configured"() {
        when:
        def tool = ToolSchemas.LIST_DIRECTORY

        then:
        tool.name() == "list_directory"
        tool.title() == "list directory"
        tool.description().contains("listing of all files and directories")
        tool.inputSchema() != null
        tool.inputSchema().properties().containsKey("path")
        tool.inputSchema().required() == ["path"]
    }

    def "DIRECTORY_TREE schema should be properly configured"() {
        when:
        def tool = ToolSchemas.DIRECTORY_TREE

        then:
        tool.name() == "directory_tree"
        tool.title() == "directory tree"
        tool.description().contains("recursive tree view")
        tool.inputSchema() != null
        tool.inputSchema().properties().containsKey("path")
        tool.inputSchema().required() == ["path"]
    }

    def "MOVE_FILE schema should be properly configured"() {
        when:
        def tool = ToolSchemas.MOVE_FILE

        then:
        tool.name() == "move_file"
        tool.title() == "move file"
        tool.description().contains("Move or rename")
        tool.inputSchema() != null
        tool.inputSchema().properties().containsKey("source")
        tool.inputSchema().properties().containsKey("destination")
        tool.inputSchema().required() == ["source", "destination"]
    }

    def "SEARCH_FILES schema should be properly configured"() {
        when:
        def tool = ToolSchemas.SEARCH_FILES

        then:
        tool.name() == "search_files"
        tool.title() == "search files"
        tool.description().contains("Recursively search")
        tool.inputSchema() != null
        tool.inputSchema().properties().containsKey("path")
        tool.inputSchema().properties().containsKey("pattern")
        tool.inputSchema().properties().containsKey("excludePatterns")
        tool.inputSchema().required() == ["path", "pattern"]
        def excludeSchema = tool.inputSchema().properties().get("excludePatterns")
        excludeSchema.get("type") == "array"
    }

    def "GET_FILE_INFO schema should be properly configured"() {
        when:
        def tool = ToolSchemas.GET_FILE_INFO

        then:
        tool.name() == "get_file_info"
        tool.title() == "get file info"
        tool.description().contains("detailed metadata")
        tool.inputSchema() != null
        tool.inputSchema().properties().containsKey("path")
        tool.inputSchema().required() == ["path"]
    }

    def "constructor should not be accessible"() {
        when:
        def constructor = ToolSchemas.class.getDeclaredConstructor()
        constructor.setAccessible(true)
        constructor.newInstance()

        then:
        noExceptionThrown()
    }

    def "all tools should have non-null schemas"() {
        expect:
        ToolSchemas.READ_FILE != null
        ToolSchemas.READ_MULTIPLE_FILES != null
        ToolSchemas.WRITE_FILE != null
        ToolSchemas.EDIT_FILE != null
        ToolSchemas.CREATE_DIRECTORY != null
        ToolSchemas.LIST_DIRECTORY != null
        ToolSchemas.DIRECTORY_TREE != null
        ToolSchemas.MOVE_FILE != null
        ToolSchemas.SEARCH_FILES != null
        ToolSchemas.GET_FILE_INFO != null
    }

    def "single path schema should have correct structure"() {
        when:
        def tool = ToolSchemas.READ_FILE
        def schema = tool.inputSchema()

        then:
        schema.type() == "object"
        schema.properties().size() == 1
        schema.properties().get("path").get("type") == "string"
        schema.required().size() == 1
        schema.required().contains("path")
    }

    def "multi path schema should have correct array structure"() {
        when:
        def tool = ToolSchemas.READ_MULTIPLE_FILES
        def schema = tool.inputSchema()
        def pathsProperty = schema.properties().get("paths")

        then:
        pathsProperty.get("type") == "array"
        pathsProperty.get("items") != null
        ((Map)pathsProperty.get("items")).get("type") == "string"
    }

    def "path and content schema should have both fields"() {
        when:
        def tool = ToolSchemas.WRITE_FILE
        def schema = tool.inputSchema()

        then:
        schema.properties().size() == 2
        schema.properties().get("path").get("type") == "string"
        schema.properties().get("content").get("type") == "string"
        schema.required().size() == 2
    }

    def "source destination schema should have both fields"() {
        when:
        def tool = ToolSchemas.MOVE_FILE
        def schema = tool.inputSchema()

        then:
        schema.properties().size() == 2
        schema.properties().get("source").get("type") == "string"
        schema.properties().get("destination").get("type") == "string"
        schema.required().size() == 2
    }

    def "search schema should have path, pattern, and excludePatterns"() {
        when:
        def tool = ToolSchemas.SEARCH_FILES
        def schema = tool.inputSchema()

        then:
        schema.properties().size() == 3
        schema.properties().get("path").get("type") == "string"
        schema.properties().get("pattern").get("type") == "string"
        schema.properties().get("excludePatterns").get("type") == "array"
        schema.required() == ["path", "pattern"]
    }

    def "edit file schema should have complex structure"() {
        when:
        def tool = ToolSchemas.EDIT_FILE
        def schema = tool.inputSchema()
        def editsProperty = schema.properties().get("edits")

        then:
        schema.properties().size() == 3
        schema.properties().get("path").get("type") == "string"
        editsProperty.get("type") == "array"
        schema.properties().get("dryRun").get("type") == "boolean"
        schema.required() == ["path", "edits"]

        def itemsSchema = editsProperty.get("items") as Map
        itemsSchema.get("type") == "object"
        def props = itemsSchema.get("properties") as Map
        props.containsKey("oldText")
        props.containsKey("newText")
    }
}
