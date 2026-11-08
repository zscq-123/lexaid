package com.lexaid.common.image;

import com.lexaid.common.BizException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/** A decoded-and-reencoded image. Do not store original uploads or their metadata. */
public record SanitizedImage(String mediaType, byte[] bytes) {
    public static void checkSize(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 4L * 1024 * 1024) {
            throw new BizException("每张图片须在 4 MB 以内且不能为空");
        }
    }

    public static SanitizedImage read(MultipartFile file, int displayEdge) {
        checkSize(file);
        try (var stream = file.getInputStream(); var input = new MemoryCacheImageInputStream(stream)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) throw invalidImage();
                int orientation = format.equals("jpeg") ? JpegOrientation.read(input) : 1;
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int longest = Math.max(width, height);
                if (width < 1 || height < 1 || longest > 8192 || (long) width * height > 16_000_000) {
                    throw new BizException("图片分辨率过大，最长边须在 8192 像素以内，面积不超过 1600 万像素");
                }
                var params = reader.getDefaultReadParam();
                int step = Math.max(1, longest / displayEdge);
                params.setSourceSubsampling(step, step, 0, 0);
                BufferedImage decoded = reader.read(0, params);
                double scale = Math.min(1.0, displayEdge / (double) longest);
                int type = format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
                int scaledWidth = Math.max(1, (int) Math.round(width * scale));
                int scaledHeight = Math.max(1, (int) Math.round(height * scale));
                BufferedImage clean = new BufferedImage(orientation >= 5 ? scaledHeight : scaledWidth,
                        orientation >= 5 ? scaledWidth : scaledHeight, type);
                Graphics2D g = clean.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.transform(JpegOrientation.transform(orientation, scaledWidth, scaledHeight));
                    g.drawImage(decoded, 0, 0, scaledWidth, scaledHeight, null);
                } finally {
                    g.dispose();
                    decoded.flush();
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(clean, format, output)) throw invalidImage();
                return new SanitizedImage("image/" + format, output.toByteArray());
            } finally {
                reader.dispose();
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalidImage();
        }
    }

    private static BizException invalidImage() {
        return new BizException("图片损坏或格式不支持，请选择有效的 JPEG 或 PNG 图片");
    }
}
