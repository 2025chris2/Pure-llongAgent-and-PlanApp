package com.tzl.llongagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.tzl.llongagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.nio.file.Paths;


/***
 * 资源下载工具
 */
public class ResourceDownloadTool {

    @Tool(description = "Download a resource from a given URL")
    public String downloadResource(
            @ToolParam(description = "URL of the resource to download") String url,
            @ToolParam(description = "Name of file to save the download resource") String fileName
    ) {
        String FILE_DIR = Paths.get(FileConstant.FILE_SAVE_DIR, "download").toString();
        String filePath = Paths.get(FILE_DIR, fileName).toString();
        try{
            FileUtil.mkdir(FILE_DIR);
            HttpUtil.downloadFile(url, new File(filePath));
            return "Resource download successfully to : " + filePath;
        } catch (Exception e) {
            return "Error downloading resource " +e.getMessage();
        }
    }

}
