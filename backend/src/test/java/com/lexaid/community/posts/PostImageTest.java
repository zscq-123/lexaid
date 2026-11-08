package com.lexaid.community.posts;

import com.lexaid.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostImageTest {
    private static byte[] picture(String format) throws Exception {
        var pixels = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(pixels, format, output));
        return output.toByteArray();
    }

    private static MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("images", "client-supplied.jpg", "image/jpeg", bytes);
    }

    @Test
    void detectsActualFormatsAndStripsPayloadWhilePreservingGalleryOrder() throws Exception {
        byte[] png = picture("png");
        byte[] trailer = "not-part-of-the-picture".getBytes(StandardCharsets.US_ASCII);
        byte[] tainted = Arrays.copyOf(png, png.length + trailer.length);
        System.arraycopy(trailer, 0, tainted, png.length, trailer.length);
        var images = PostImage.readAll(List.of(upload(tainted), upload(picture("jpg"))));
        assertEquals(List.of("image/png", "image/jpeg"), images.stream().map(PostImage::mediaType).toList());
        assertFalse(new String(images.get(0).bytes(), StandardCharsets.ISO_8859_1).contains("not-part-of-the-picture"));
    }

    @Test
    void displaysPhonePhotosUprightAfterRemovingExif() throws Exception {
        byte[] original = picture("jpg");
        byte[] app1 = java.util.HexFormat.of().parseHex(
                "ffe1002245786966000049492a0008000000010012010300010000000600000000000000");
        ByteArrayOutputStream tagged = new ByteArrayOutputStream();
        int firstSegmentEnd = 4 + Short.toUnsignedInt(ByteBuffer.wrap(original).getShort(4));
        tagged.write(original, 0, firstSegmentEnd);
        tagged.write(app1);
        tagged.write(original, firstSegmentEnd, original.length - firstSegmentEnd);
        byte[] result = PostImage.readAll(List.of(upload(tagged.toByteArray()))).get(0).bytes();
        BufferedImage displayed = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(2, displayed.getWidth());
        assertEquals(3, displayed.getHeight());
        assertFalse(new String(result, StandardCharsets.ISO_8859_1).contains("Exif"));
    }

    @Test
    void rejectsSpoofedSvgGifAndOversizedDimensionsBeforeReadingPixels() throws Exception {
        assertThrows(BizException.class, () -> PostImage.readAll(List.of(upload("<svg/>".getBytes(StandardCharsets.UTF_8)))));
        assertThrows(BizException.class, () -> PostImage.readAll(List.of(upload(picture("gif")))));
        byte[] png = picture("png");
        ByteBuffer.wrap(png).putInt(16, 50_000).putInt(20, 50_000);
        assertThrows(BizException.class, () -> PostImage.readAll(List.of(upload(png))));
    }

    @Test
    void enforcesPhotoCountAndAggregateSizeBeforeDecoding() throws Exception {
        assertThrows(BizException.class, () -> PostImage.readAll(Collections.nCopies(10, upload(picture("png")))));
        var large = upload(new byte[4 * 1024 * 1024]);
        BizException ex = assertThrows(BizException.class, () -> PostImage.readAll(Collections.nCopies(6, large)));
        assertTrue(ex.getMessage().contains("20 MB"));
        assertEquals(List.of(), PostImage.readAll(null));
    }
}
