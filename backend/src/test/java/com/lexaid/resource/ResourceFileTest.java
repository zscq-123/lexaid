package com.lexaid.resource;

import com.lexaid.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ResourceFileTest {

    private static MockMultipartFile upload(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    private static byte[] pdf() {
        return "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zip(Map<String, String> entries) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] office(String mainPart) throws Exception {
        return zip(Map.of("[Content_Types].xml", "<Types/>", mainPart, "<document/>"));
    }

    @Test
    void acceptsSupportedDocumentsAndKeepsTheOriginalBytes() throws Exception {
        var pdf = ResourceFile.read(upload("复习资料.PDF", pdf()));
        assertEquals("pdf", pdf.extension());
        assertEquals("application/pdf", pdf.mediaType());
        assertEquals("复习资料", pdf.baseName());
        assertArrayEquals(ResourceFile.read(upload("复习资料.pdf", pdf())).bytes(), pdf.bytes());

        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                ResourceFile.read(upload("讲义.docx", office("word/document.xml"))).mediaType());
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                ResourceFile.read(upload("讲义.pptx", office("ppt/presentation.xml"))).mediaType());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                ResourceFile.read(upload("成绩.xlsx", office("xl/workbook.xml"))).mediaType());
        assertEquals("text/plain", ResourceFile.read(upload("笔记.txt", text("正当防卫"))).mediaType());
        assertEquals("text/markdown", ResourceFile.read(upload("笔记.md", text("# 正当防卫"))).mediaType());
    }

    @Test
    void stripsClientPathsAndShortensOnlyTheBaseName() {
        var upload = new MockMultipartFile("file", "C:\\fakepath\\".repeat(30) + "读书笔记.md",
                "text/markdown", text("# 笔记"));
        var file = ResourceFile.read(upload);
        assertFalse(file.fileName().contains("\\"));
        assertTrue(file.fileName().endsWith(".md"));
        assertTrue(file.fileName().codePointCount(0, file.fileName().length()) <= 180);
    }

    @Test
    void rejectsMissingOrUnsupportedExtensionsBeforeStoring() {
        assertThrows(BizException.class, () -> ResourceFile.read(upload("", text("x"))));
        assertThrows(BizException.class, () -> ResourceFile.read(upload("readme", text("x"))));
        assertThrows(BizException.class, () -> ResourceFile.read(upload(".pdf", pdf())));
        assertThrows(BizException.class, () -> ResourceFile.read(upload("setup.exe", text("x"))));
        assertThrows(BizException.class, () -> ResourceFile.read(upload("empty.pdf", new byte[0])));
    }

    @Test
    void rejectsSpoofedOrMalformedContent() throws Exception {
        assertThrows(BizException.class, () -> ResourceFile.read(upload("fake.pdf", text("<svg/>"))));
        assertThrows(BizException.class, () -> ResourceFile.read(upload("fake.docx", pdf())));
        assertThrows(BizException.class, () -> ResourceFile.read(upload("fake.txt", new byte[]{'a', 0, 'b'})));
        assertThrows(BizException.class, () -> ResourceFile.read(upload("fake.md", new byte[]{(byte) 0xc3, 0x28})));
        assertThrows(BizException.class, () -> ResourceFile.read(upload("fake.pptx", office("word/document.xml"))));
        assertThrows(BizException.class, () -> ResourceFile.read(upload("fake.xlsx",
                zip(Map.of("[Content_Types].xml", "<Types/>")))));
    }

    @Test
    void acceptsFilesOfAnySizeBecauseTheLibrarySetsNoUploadCeiling() {
        // A 21 MB text file used to exceed the former 20 MB application limit.
        byte[] large = new byte[21 * 1024 * 1024];
        java.util.Arrays.fill(large, (byte) 'a');
        var file = ResourceFile.read(upload("大文件.txt", large));
        assertEquals(large.length, file.bytes().length);
    }
}
