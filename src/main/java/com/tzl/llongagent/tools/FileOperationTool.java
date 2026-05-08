package com.tzl.llongagent.tools;

import cn.hutool.core.io.FileUtil;
import com.tzl.llongagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Paths;

/***
 * 文件操作工具类(提供文件读写功能)
 */
public class FileOperationTool {

    // 存储路径
    private final String FILE_DIR = Paths.get(FileConstant.FILE_SAVE_DIR, "file").toString();

    @Tool(description = "Read content from file")
    public String readFile(@ToolParam(description = "Name of the file to read") String fileName) {
        // 判断文件名是否合法
        if(!invalidFileName(fileName))
            return "Invalid filename!";

        String filePath = Paths.get(FILE_DIR, fileName).toString();

        try{
           return FileUtil.readUtf8String(filePath);
        } catch(Exception e) {
            return "Error read file" + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file")
    public String writeFile(@ToolParam(description = "Name of the file to write") String fileName,
                            @ToolParam(description = "Content to write to the file")String content) {
        // 判断文件名是否合法
        if(!invalidFileName(fileName))
            return "Invalid filename!";

        String filePath = Paths.get(FILE_DIR, fileName).toString();

        try{
            // 如果文件夹存在,则没有什么影响,如果文件夹不存在,则创建新文件夹
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath);
            return "File written successfully to: " + filePath;

        } catch(Exception e) {
            return "Error writing to file " + e.getMessage();
        }

    }

    private boolean invalidFileName(String fileName){
        return fileName != null && !fileName.isEmpty()
                && !fileName.contains("..")
                && !fileName.contains("/")
                && !fileName.contains("\\");
    }

}
