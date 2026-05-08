package com.tzl.llongagent.tools;


import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class TerminalOperationTool {
    // 定义一个公共类 TerminalOperationTool，封装终端操作相关功能

    @Tool(description = "Execute a command in the terminal")
    // Spring AI 的 @Tool 注解，将该方法声明为可被 AI 调用的工具（Function Calling）
    // description 帮助 AI 理解该工具的用途
    public String executeTerminalCommand(
            @ToolParam(description = "Command to execute in the terminal") String command) {
        // 定义公共方法，返回命令执行结果字符串
        // @ToolParam 标注参数信息，帮助 AI 知道调用时需要传入什么
        // String command 接收要在终端执行的命令（如 ls -la）

        StringBuilder output = new StringBuilder();
        // 创建 StringBuilder 对象，用于拼接命令执行过程中产生的所有输出文本

        try {
            // 开始异常捕获块，因为执行外部命令可能抛出 IOException 或 InterruptedException

            ProcessBuilder builder = new ProcessBuilder("cmd", "/c", command);
            Process process = builder.start();
            // 启动子进程执行系统命令：


            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                // 使用 try-with-resources 读取命令的标准输出：
                // process.getInputStream() 获取子进程的标准输出流
                // InputStreamReader 将字节流转换为字符流
                // BufferedReader 提供按行读取的能力
                // 括号内的资源会在代码块结束时自动关闭

                String line;
                // 声明字符串变量，用于临时存储读取到的每一行输出

                while ((line = reader.readLine()) != null) {
                    // 循环逐行读取命令输出，直到读取完毕（返回 null）

                    output.append(line).append("\n");
                    // 将读取到的每一行追加到 StringBuilder
                    // 并在末尾添加换行符，保留原始输出格式
                }
            }
            // 结束 try-with-resources 块，BufferedReader 在此处自动关闭

            int exitCode = process.waitFor();
            // 阻塞等待子进程执行完毕，并获取其退出码（exit code）
            // 0 通常表示成功，非 0 表示失败

            if (exitCode != 0) {
                // 判断命令是否执行失败（退出码非零）

                output.append("Command execute failed with exit code: ").append(exitCode);
                // 在输出末尾追加错误信息，提示用户命令执行失败及具体的退出码
            }
        } catch (IOException | InterruptedException e) {
            // 捕获两种异常：
            // IOException：命令启动或读取输出时发生 I/O 错误
            // InterruptedException：当前线程在等待进程结束时被打断

            output.append("Error executing command: ").append(e.getMessage());
            // 将异常信息追加到输出结果中，方便排查问题
        }

        return output.toString();
        // 返回最终拼接的字符串，包含命令的标准输出以及可能的错误/异常信息
    }
}
