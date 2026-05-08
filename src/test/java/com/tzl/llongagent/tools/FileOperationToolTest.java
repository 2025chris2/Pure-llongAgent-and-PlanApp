package com.tzl.llongagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileOperationToolTest {

    FileOperationTool tool = new FileOperationTool();

    @Test
    void readFile() {
        String content =  tool.readFile("ttt");
        System.out.println(content);

    }

    @Test
    void writeFile() {
        tool.writeFile("ttt", "我很好");
    }
}