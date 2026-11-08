package com.lexaid.auth;

import com.lexaid.common.BizException;
import com.lexaid.common.image.SanitizedImage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AvatarService {
    private final JdbcTemplate jdbc;

    String version(long userId) {
        return jdbc.query("SELECT version FROM user_avatar WHERE user_id = ?",
                (rs, i) -> rs.getString("version"), userId).stream().findFirst().orElse(null);
    }

    void replace(long userId, MultipartFile upload) {
        SanitizedImage image = SanitizedImage.read(upload, 256);
        jdbc.update("""
                INSERT INTO user_avatar (user_id, version, media_type, image_bytes)
                VALUES (?, ?, ?, ?) AS incoming ON DUPLICATE KEY UPDATE
                    version = incoming.version, media_type = incoming.media_type, image_bytes = incoming.image_bytes
                """, userId, UUID.randomUUID().toString(), image.mediaType(), image.bytes());
    }

    void remove(long userId) {
        jdbc.update("DELETE FROM user_avatar WHERE user_id = ?", userId);
    }

    SanitizedImage get(long userId) {
        return jdbc.query("SELECT media_type, image_bytes FROM user_avatar WHERE user_id = ?",
                (rs, i) -> new SanitizedImage(rs.getString("media_type"), rs.getBytes("image_bytes")), userId)
                .stream().findFirst().orElseThrow(() -> new BizException(404, "用户未设置头像"));
    }
}
