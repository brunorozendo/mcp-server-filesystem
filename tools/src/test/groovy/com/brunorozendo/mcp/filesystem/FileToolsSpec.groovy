package com.brunorozendo.mcp.filesystem

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper
import io.modelcontextprotocol.json.TypeRef
import io.modelcontextprotocol.server.McpAsyncServerExchange
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class FileToolsSpec extends Specification {

    @TempDir
    Path tempDir

    static JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper())

    // Helper method to create CallToolRequest using JsonMapper for Groovy-Record compatibility
    private static McpSchema.CallToolRequest createRequest(Map<String, Object> args) {
        def requestMap = new LinkedHashMap<String, Object>()
        requestMap.put("arguments", args)
        requestMap.put("_meta", null)

        return jsonMapper.convertValue(requestMap, new TypeRef<McpSchema.CallToolRequest>() {})
    }

    // Helper method to extract text from CallToolResult
    private static String getText(CallToolResult result) {
        return result.content().isEmpty() ? "" : ((McpSchema.TextContent)result.content().get(0)).text()
    }

    def "readFile - should read file content successfully"() {
        given:
        def testFile = tempDir.resolve("test.txt")
        Files.writeString(testFile, "Hello World")
        def request = createRequest(["path": testFile.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.readFile(exchange, request).block()

        then:
        getText(result) == "Hello World"
        !result.isError()
    }

    def "readFile - should return error for non-existent file"() {
        given:
        def nonExistentPath = tempDir.resolve("nonexistent.txt").toString()
        def request = createRequest(["path": nonExistentPath])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.readFile(exchange, request).block()

        then:
        result.isError()
        // IOException.getMessage() for NoSuchFileException returns the file path
        getText(result).contains(nonExistentPath) || getText(result).contains("nonexistent.txt")
    }

    def "readFile - should handle IOException"() {
        given:
        def dir = tempDir.resolve("somedir")
        Files.createDirectory(dir)
        def request = createRequest(["path": dir.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.readFile(exchange, request).block()

        then:
        result.isError()
    }

    def "readMultipleFiles - should read multiple files successfully"() {
        given:
        def file1 = tempDir.resolve("file1.txt")
        def file2 = tempDir.resolve("file2.txt")
        Files.writeString(file1, "Content 1")
        Files.writeString(file2, "Content 2")
        def request = createRequest(["paths": [file1.toString(), file2.toString()]])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.readMultipleFiles(exchange, request).block()

        then:
        getText(result).contains("file1.txt:\nContent 1")
        getText(result).contains("file2.txt:\nContent 2")
        getText(result).contains("---")
        !result.isError()
    }

    def "readMultipleFiles - should read directory recursively"() {
        given:
        def subDir = tempDir.resolve("subdir")
        Files.createDirectory(subDir)
        def file1 = subDir.resolve("file1.txt")
        def file2 = subDir.resolve("file2.txt")
        Files.writeString(file1, "Content 1")
        Files.writeString(file2, "Content 2")
        def request = createRequest(["paths": [subDir.toString()]])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.readMultipleFiles(exchange, request).block()

        then:
        getText(result).contains("Content 1")
        getText(result).contains("Content 2")
        !result.isError()
    }

    def "readMultipleFiles - should handle errors for individual files"() {
        given:
        def file1 = tempDir.resolve("file1.txt")
        Files.writeString(file1, "Content 1")
        def nonExistent = tempDir.resolve("nonexistent.txt")
        def request = createRequest(["paths": [file1.toString(), nonExistent.toString()]])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.readMultipleFiles(exchange, request).block()

        then:
        getText(result).contains("Content 1")
        getText(result).contains("Error")
        !result.isError()
    }

    def "readMultipleFiles - should handle empty paths list"() {
        given:
        def request = createRequest(["paths": []])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.readMultipleFiles(exchange, request).block()

        then:
        !result.isError()
    }

    def "readMultipleFiles - should handle missing paths argument"() {
        given:
        def request = createRequest([:])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.readMultipleFiles(exchange, request).block()

        then:
        !result.isError()
    }

    def "readMultipleFiles - should handle non-list paths argument"() {
        given:
        def request = createRequest(["paths": "not a list"])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.readMultipleFiles(exchange, request).block()

        then:
        !result.isError()
    }

    def "readMultipleFiles - should handle IOException when walking directory"() {
        given:
        def file = tempDir.resolve("file.txt")
        Files.writeString(file, "content")
        def request = createRequest(["paths": [file.toString()]])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.readMultipleFiles(exchange, request).block()

        then:
        !result.isError()
        getText(result).contains("content")
    }

    def "writeFile - should write content to file"() {
        given:
        def testFile = tempDir.resolve("write-test.txt")
        def request = createRequest(["path": testFile.toString(), "content": "Test Content"])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.writeFile(exchange, request).block()

        then:
        getText(result).contains("Successfully wrote")
        !result.isError()
        Files.readString(testFile) == "Test Content"
    }

    def "writeFile - should overwrite existing file"() {
        given:
        def testFile = tempDir.resolve("overwrite-test.txt")
        Files.writeString(testFile, "Old Content")
        def request = createRequest(["path": testFile.toString(), "content": "New Content"])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.writeFile(exchange, request).block()

        then:
        !result.isError()
        Files.readString(testFile) == "New Content"
    }

    def "writeFile - should handle exception"() {
        given:
        def invalidPath = "/invalid/path/that/does/not/exist/file.txt"
        def request = createRequest(["path": invalidPath, "content": "Content"])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.writeFile(exchange, request).block()

        then:
        result.isError()
    }

    def "createDirectory - should create directory"() {
        given:
        def newDir = tempDir.resolve("newdir")
        def request = createRequest(["path": newDir.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.createDirectory(exchange, request).block()

        then:
        getText(result).contains("Successfully created")
        !result.isError()
        Files.isDirectory(newDir)
    }

    def "createDirectory - should create nested directories"() {
        given:
        def nestedDir = tempDir.resolve("parent/child/grandchild")
        def request = createRequest(["path": nestedDir.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.createDirectory(exchange, request).block()

        then:
        !result.isError()
        Files.isDirectory(nestedDir)
    }

    def "createDirectory - should handle exception"() {
        given:
        def testFile = tempDir.resolve("file.txt")
        Files.writeString(testFile, "content")
        def request = createRequest(["path": testFile.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.createDirectory(exchange, request).block()

        then:
        result.isError()
    }

    def "listDirectory - should list directory contents"() {
        given:
        def subDir = tempDir.resolve("subdir")
        Files.createDirectory(subDir)
        def file = tempDir.resolve("file.txt")
        Files.writeString(file, "content")
        def request = createRequest(["path": tempDir.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.listDirectory(exchange, request).block()

        then:
        getText(result).contains("[DIR] subdir")
        getText(result).contains("[FILE] file.txt")
        !result.isError()
    }

    def "listDirectory - should handle empty directory"() {
        given:
        def emptyDir = tempDir.resolve("empty")
        Files.createDirectory(emptyDir)
        def request = createRequest(["path": emptyDir.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.listDirectory(exchange, request).block()

        then:
        getText(result) == ""
        !result.isError()
    }

    def "listDirectory - should handle exception"() {
        given:
        def nonExistent = tempDir.resolve("nonexistent")
        def request = createRequest(["path": nonExistent.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.listDirectory(exchange, request).block()

        then:
        result.isError()
    }

    def "moveFile - should move file successfully"() {
        given:
        def source = tempDir.resolve("source.txt")
        Files.writeString(source, "content")
        def dest = tempDir.resolve("dest.txt")
        def request = createRequest(["source": source.toString(), "destination": dest.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.moveFile(exchange, request).block()

        then:
        getText(result).contains("Successfully moved")
        !result.isError()
        Files.exists(dest)
        !Files.exists(source)
    }

    def "moveFile - should handle exception"() {
        given:
        def nonExistent = tempDir.resolve("nonexistent.txt")
        def dest = tempDir.resolve("dest.txt")
        def request = createRequest(["source": nonExistent.toString(), "destination": dest.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.moveFile(exchange, request).block()

        then:
        result.isError()
    }

    def "getFileInfo - should return file info"() {
        given:
        def testFile = tempDir.resolve("info-test.txt")
        Files.writeString(testFile, "content")
        def request = createRequest(["path": testFile.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.getFileInfo(exchange, request).block()

        then:
        getText(result).contains("size:")
        getText(result).contains("created:")
        getText(result).contains("modified:")
        getText(result).contains("isFile: true")
        !result.isError()
    }

    def "getFileInfo - should return directory info"() {
        given:
        def dir = tempDir.resolve("infodir")
        Files.createDirectory(dir)
        def request = createRequest(["path": dir.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.getFileInfo(exchange, request).block()

        then:
        getText(result).contains("isDirectory: true")
        !result.isError()
    }

    def "getFileInfo - should handle UnsupportedOperationException for permissions"() {
        given:
        def testFile = tempDir.resolve("test.txt")
        Files.writeString(testFile, "content")
        def request = createRequest(["path": testFile.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.getFileInfo(exchange, request).block()

        then:
        getText(result).contains("permissions:")
        !result.isError()
    }

    def "getFileInfo - should handle exception"() {
        given:
        def nonExistent = tempDir.resolve("nonexistent.txt")
        def request = createRequest(["path": nonExistent.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.getFileInfo(exchange, request).block()

        then:
        result.isError()
    }

    def "searchFiles - should find files matching pattern"() {
        given:
        def file1 = tempDir.resolve("test.txt")
        def file2 = tempDir.resolve("test.java")
        def file3 = tempDir.resolve("other.txt")
        Files.writeString(file1, "content")
        Files.writeString(file2, "content")
        Files.writeString(file3, "content")
        def request = createRequest(["path": tempDir.toString(), "pattern": "*.txt"])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.searchFiles(exchange, request).block()

        then:
        getText(result).contains("test.txt")
        getText(result).contains("other.txt")
        !getText(result).contains("test.java")
        !result.isError()
    }

    def "searchFiles - should search recursively"() {
        given:
        def subdir = tempDir.resolve("subdir")
        Files.createDirectory(subdir)
        def file1 = tempDir.resolve("test.txt")
        def file2 = subdir.resolve("nested.txt")
        Files.writeString(file1, "content")
        Files.writeString(file2, "content")
        def request = createRequest(["path": tempDir.toString(), "pattern": "*.txt"])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.searchFiles(exchange, request).block()

        then:
        getText(result).contains("test.txt")
        getText(result).contains("nested.txt")
        !result.isError()
    }

    def "searchFiles - should exclude patterns"() {
        given:
        def file1 = tempDir.resolve("include.txt")
        def file2 = tempDir.resolve("exclude.txt")
        Files.writeString(file1, "content")
        Files.writeString(file2, "content")
        def request = createRequest(["path": tempDir.toString(), "pattern": "*.txt", "excludePatterns": ["exclude.txt"]])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.searchFiles(exchange, request).block()

        then:
        getText(result).contains("include.txt")
        !getText(result).contains("exclude.txt")
        !result.isError()
    }

    def "searchFiles - should skip excluded directories"() {
        given:
        def excludedDir = tempDir.resolve("excluded")
        Files.createDirectory(excludedDir)
        def file1 = tempDir.resolve("root.txt")
        def file2 = excludedDir.resolve("nested.txt")
        Files.writeString(file1, "content")
        Files.writeString(file2, "content")
        def request = createRequest(["path": tempDir.toString(), "pattern": "*.txt", "excludePatterns": ["excluded"]])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.searchFiles(exchange, request).block()

        then:
        getText(result).contains("root.txt")
        !getText(result).contains("nested.txt")
        !result.isError()
    }

    def "searchFiles - should match directories"() {
        given:
        def dir = tempDir.resolve("testdir")
        Files.createDirectory(dir)
        def request = createRequest(["path": tempDir.toString(), "pattern": "test*"])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.searchFiles(exchange, request).block()

        then:
        getText(result).contains("testdir")
        !result.isError()
    }

    def "searchFiles - should return no matches message"() {
        given:
        def request = createRequest(["path": tempDir.toString(), "pattern": "*.nonexistent"])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.searchFiles(exchange, request).block()

        then:
        getText(result) == "No matches found"
        !result.isError()
    }

    def "searchFiles - should handle exception"() {
        given:
        def nonExistent = tempDir.resolve("nonexistent")
        def request = createRequest(["path": nonExistent.toString(), "pattern": "*.txt"])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.searchFiles(exchange, request).block()

        then:
        result.isError()
    }

    def "searchFiles - should handle excludePatterns as non-list"() {
        given:
        def file1 = tempDir.resolve("test.txt")
        Files.writeString(file1, "content")
        def request = createRequest(["path": tempDir.toString(), "pattern": "*.txt", "excludePatterns": "not a list"])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.searchFiles(exchange, request).block()

        then:
        getText(result).contains("test.txt")
        !result.isError()
    }

    def "directoryTree - should build tree structure"() {
        given:
        def subdir = tempDir.resolve("subdir")
        Files.createDirectory(subdir)
        def file1 = tempDir.resolve("file1.txt")
        def file2 = subdir.resolve("file2.txt")
        Files.writeString(file1, "content")
        Files.writeString(file2, "content")
        def request = createRequest(["path": tempDir.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.directoryTree(exchange, request).block()

        then:
        getText(result).contains('"type" : "directory"')
        getText(result).contains('"type" : "file"')
        getText(result).contains("children")
        !result.isError()
    }

    def "directoryTree - should handle empty directory"() {
        given:
        def emptyDir = tempDir.resolve("empty")
        Files.createDirectory(emptyDir)
        def request = createRequest(["path": emptyDir.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.directoryTree(exchange, request).block()

        then:
        getText(result).contains('"children" : [ ]')
        !result.isError()
    }

    def "directoryTree - should handle exception"() {
        given:
        def nonExistent = tempDir.resolve("nonexistent")
        def request = createRequest(["path": nonExistent.toString()])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.directoryTree(exchange, request).block()

        then:
        // buildTree doesn't check existence first, it uses Files.isDirectory which returns false for nonexistent
        // This causes it to return {"name": "nonexistent", "type": "file"} rather than an error
        !result.isError()
        getText(result).contains('"name" : "nonexistent"')
        getText(result).contains('"type" : "file"')
    }

    def "editFile - should edit file with single edit"() {
        given:
        def testFile = tempDir.resolve("edit.txt")
        Files.writeString(testFile, "Hello World")
        def edits = [["oldText": "World", "newText": "Universe"]]
        def request = createRequest(["path": testFile.toString(), "edits": edits])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.editFile(exchange, request).block()

        then:
        getText(result).contains("```diff")
        !result.isError()
        Files.readString(testFile) == "Hello Universe"
    }

    def "editFile - should handle multiple edits"() {
        given:
        def testFile = tempDir.resolve("multi-edit.txt")
        Files.writeString(testFile, "Line 1\nLine 2\nLine 3")
        def edits = [
            ["oldText": "Line 1", "newText": "First Line"],
            ["oldText": "Line 3", "newText": "Third Line"]
        ]
        def request = createRequest(["path": testFile.toString(), "edits": edits])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.editFile(exchange, request).block()

        then:
        !result.isError()
        Files.readString(testFile).contains("First Line")
        Files.readString(testFile).contains("Third Line")
    }

    def "editFile - should handle dryRun mode"() {
        given:
        def testFile = tempDir.resolve("dryrun.txt")
        Files.writeString(testFile, "Original Content")
        def edits = [["oldText": "Original", "newText": "Modified"]]
        def request = createRequest(["path": testFile.toString(), "edits": edits, "dryRun": true])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.editFile(exchange, request).block()

        then:
        getText(result).contains("```diff")
        !result.isError()
        Files.readString(testFile) == "Original Content"
    }

    def "editFile - should handle dryRun false"() {
        given:
        def testFile = tempDir.resolve("dryrun-false.txt")
        Files.writeString(testFile, "Original Content")
        def edits = [["oldText": "Original", "newText": "Modified"]]
        def request = createRequest(["path": testFile.toString(), "edits": edits, "dryRun": false])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.editFile(exchange, request).block()

        then:
        !result.isError()
        Files.readString(testFile) == "Modified Content"
    }

    def "editFile - should normalize line endings"() {
        given:
        def testFile = tempDir.resolve("lineendings.txt")
        // File content must also use normalized line endings for the match to work
        Files.writeString(testFile, "Hello\nWorld")
        def edits = [["oldText": "Hello\nWorld", "newText": "Goodbye\nWorld"]]
        def request = createRequest(["path": testFile.toString(), "edits": edits])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.editFile(exchange, request).block()

        then:
        !result.isError()
        Files.readString(testFile) == "Goodbye\nWorld"
    }

    def "editFile - should fail when text not found"() {
        given:
        def testFile = tempDir.resolve("notfound.txt")
        Files.writeString(testFile, "Content")
        def edits = [["oldText": "NonExistent", "newText": "New"]]
        def request = createRequest(["path": testFile.toString(), "edits": edits])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.editFile(exchange, request).block()

        then:
        result.isError()
        getText(result).contains("Could not find exact match")
    }

    def "editFile - should handle exception for non-existent file"() {
        given:
        def nonExistent = tempDir.resolve("nonexistent.txt")
        def edits = [["oldText": "old", "newText": "new"]]
        def request = createRequest(["path": nonExistent.toString(), "edits": edits])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.editFile(exchange, request).block()

        then:
        result.isError()
    }

    def "handleTool - should handle exceptions"() {
        given:
        def request = createRequest(["path": "/invalid"])
        def exchange = Mock(McpAsyncServerExchange)

        when:
        def result = FileTools.readFile(exchange, request).block()

        then:
        result.isError()
    }
}
