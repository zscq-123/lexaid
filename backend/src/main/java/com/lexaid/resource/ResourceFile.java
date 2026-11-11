package com.lexaid.resource;

import com.lexaid.common.BizException;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A validated learning-resource upload. The original bytes are kept because documents must stay
 * byte-for-byte downloadable; the extension, declared media type and magic bytes are checked
 * before anything reaches the database.
 */
public record ResourceFile(String fileName, String extension, String mediaType, byte[] bytes) {

    private static final int MAX_FILE_NAME_CODE_POINTS = 180;
    private static final long MAX_ZIP_ENTRIES = 50_000;

    private static final Map<String, String> MEDIA_TYPES = Map.of(
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "txt", "text/plain",
            "md", "text/markdown"
    );

    public static ResourceFile read(MultipartFile upload) {
        if (upload == null || upload.isEmpty() || upload.getSize() <= 0) {
            throw new BizException("请选择要上传的学习资源文件");
        }
        byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException e) {
            throw new BizException("文件读取失败，请重新选择后上传");
        }
        if (bytes.length == 0) {
            throw new BizException("学习资源文件不能为空");
        }

        String original = upload.getOriginalFilename();
        String safeName = safeName(original);
        int dot = safeName.lastIndexOf('.');
        if (dot <= 0 || dot == safeName.length() - 1) {
            throw new BizException("请选择带有扩展名的 PDF、Word、PowerPoint、Excel、TXT 或 Markdown 文件");
        }
        String extension = safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mediaType = MEDIA_TYPES.get(extension);
        if (mediaType == null) {
            throw new BizException("仅支持 PDF、DOCX、PPTX、XLSX、TXT 或 MD 文件");
        }
        verifyContent(extension, bytes);
        return new ResourceFile(shorten(safeName, extension), extension, mediaType, bytes);
    }

    public String baseName() {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String safeName(String original) {
        String name = original == null ? "" : original;
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            throw new BizException("请选择带有扩展名的学习资源文件");
        }
        return name;
    }

    private static String shorten(String name, String extension) {
        if (name.codePointCount(0, name.length()) <= MAX_FILE_NAME_CODE_POINTS) return name;
        int keep = Math.max(1, MAX_FILE_NAME_CODE_POINTS - extension.length() - 1);
        String base = name.substring(0, name.length() - extension.length() - 1);
        String shortened = base.codePoints().limit(keep)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        return shortened + "." + extension;
    }

    private static void verifyContent(String extension, byte[] bytes) {
        switch (extension) {
            case "pdf" -> {
                if (!startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                    throw mismatched();
                }
            }
            case "docx" -> verifyZip(bytes, "word/document.xml");
            case "pptx" -> verifyZip(bytes, "ppt/presentation.xml");
            case "xlsx" -> verifyZip(bytes, "xl/workbook.xml");
            case "txt", "md" -> verifyUtf8Text(bytes);
            default -> throw mismatched();
        }
    }

    private static void verifyZip(byte[] bytes, String mainPart) {
        if (!startsWith(bytes, new byte[]{'P', 'K', 3, 4})) throw mismatched();
        boolean types = false;
        boolean main = false;
        long entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) throw mismatched();
                String name = entry.getName();
                if ("[Content_Types].xml".equals(name)) types = true;
                if (mainPart.equals(name)) main = true;
                if (types && main) return;
            }
        } catch (IOException e) {
            throw mismatched();
        }
        if (!types || !main) throw mismatched();
    }

    private static void verifyUtf8Text(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) throw new BizException("TXT 或 Markdown 文件须为 UTF-8 纯文本");
        }
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT))) {
            char[] buffer = new char[8192];
            while (reader.read(buffer) != -1) {
                // Validate without materialising the whole text in another large array.
            }
        } catch (IOException e) {
            throw new BizException("TXT 或 Markdown 文件须为 UTF-8 编码");
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }

    private static BizException mismatched() {
        return new BizException("文件内容与扩展名不符，请上传真实的 PDF、DOCX、PPTX、XLSX、TXT 或 MD 文件");
    }
}
