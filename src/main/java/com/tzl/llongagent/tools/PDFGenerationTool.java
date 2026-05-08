package com.tzl.llongagent.tools;


import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.tzl.llongagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * PDF 生成工具
 * 该类封装了使用 iText 7 库生成 PDF 文件的功能
 */
public class PDFGenerationTool {

    @Tool(description = "Generate a PDF file with given content", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generate PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content
    ) {
        String FILE_DIR = Paths.get(FileConstant.FILE_SAVE_DIR, "pdf").toString();
        String filePath = Paths.get(FILE_DIR, fileName).toString();
        try{

            FileUtil.mkdir(FILE_DIR);

            try(PdfWriter writer = new PdfWriter(filePath);
                PdfDocument pdf = new PdfDocument(writer);
                Document document = new Document(pdf)){

                // 使用内置字体（当前使用）】
                // 使用 iText 7 内置的 Adobe 中文字体 STSongStd-Light
                // "UniGB-UCS2-H" 是编码标识，表示使用 Unicode 的 GB 编码子集，支持中文显示
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");

                // 为整个文档设置默认字体，后续添加的所有文本都将使用此字体渲染
                document.setFont(font);

                // 创建 Paragraph（段落）对象，将传入的内容包装成 PDF 段落
                Paragraph paragraph = new Paragraph(content);

                // 将段落添加到文档中，此时内容被写入 PDF 页面
                document.add(paragraph);
            }

            return "PDF generated successfully to: " + filePath;

        } catch(IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

}
