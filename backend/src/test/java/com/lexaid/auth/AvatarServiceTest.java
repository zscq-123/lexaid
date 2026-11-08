package com.lexaid.auth;

import com.lexaid.common.BizException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class AvatarServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AvatarService avatars = new AvatarService(jdbc);

    @Test
    void invalidUploadCannotReplaceThePreviousAvatar() {
        var spoofed = new MockMultipartFile("avatar", "avatar.png", "image/png",
                "<svg onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8));
        assertThrows(BizException.class, () -> avatars.replace(42, spoofed));
        assertThrows(BizException.class, () -> avatars.replace(42,
                new MockMultipartFile("avatar", new byte[4 * 1024 * 1024 + 1])));
        verifyNoInteractions(jdbc);
    }

    @Test
    void replaceWritesOnlyTheProcessedImageWithANewVersion() throws Exception {
        var pixels = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        var source = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(pixels, "png", source));
        source.write("private trailer".getBytes(StandardCharsets.US_ASCII));
        var upload = new MockMultipartFile("avatar", source.toByteArray());
        avatars.replace(42, upload);
        avatars.replace(42, upload);

        var version = ArgumentCaptor.forClass(String.class);
        var bytes = ArgumentCaptor.forClass(byte[].class);
        verify(jdbc, times(2)).update(startsWith("INSERT INTO user_avatar"), eq(42L),
                version.capture(), eq("image/png"), bytes.capture());
        assertDoesNotThrow(() -> UUID.fromString(version.getValue()));
        assertNotEquals(version.getAllValues().get(0), version.getValue());
        var thumbnail = ImageIO.read(new ByteArrayInputStream(bytes.getValue()));
        assertEquals(256, thumbnail.getWidth());
        assertEquals(128, thumbnail.getHeight());
        assertFalse(new String(bytes.getValue(), StandardCharsets.ISO_8859_1).contains("private trailer"));
    }
}
