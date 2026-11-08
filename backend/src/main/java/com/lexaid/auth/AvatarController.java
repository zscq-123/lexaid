package com.lexaid.auth;

import com.lexaid.auth.dto.UserVO;
import com.lexaid.common.Result;
import com.lexaid.common.UserContext;
import com.lexaid.common.image.SanitizedImage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AvatarController {
    private final AvatarService avatars;
    private final AuthService auth;

    @PutMapping(value = "/auth/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UserVO> replace(@RequestParam("avatar") MultipartFile file) {
        return Result.ok(auth.replaceAvatar(UserContext.userId(), file));
    }

    @DeleteMapping("/auth/me/avatar")
    public Result<UserVO> remove() {
        return Result.ok(auth.removeAvatar(UserContext.userId()));
    }

    @GetMapping("/users/{userId}/avatar")
    public ResponseEntity<byte[]> image(@PathVariable long userId) {
        SanitizedImage image = avatars.get(userId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mediaType()))
                .cacheControl(CacheControl.noStore().cachePrivate()).header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}
