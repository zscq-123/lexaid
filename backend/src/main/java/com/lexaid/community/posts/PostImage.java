package com.lexaid.community.posts;

import com.lexaid.common.BizException;
import com.lexaid.common.image.SanitizedImage;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

record PostImage(String mediaType, byte[] bytes) {
    static List<PostImage> readAll(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return List.of();
        if (files.size() > 9) throw new BizException("每条帖子最多上传 9 张图片");
        long total = 0;
        for (MultipartFile file : files) {
            SanitizedImage.checkSize(file);
            total += file.getSize();
        }
        if (total > 20L * 1024 * 1024) throw new BizException("图片总大小不能超过 20 MB");
        List<PostImage> images = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            SanitizedImage image = SanitizedImage.read(file, 1600);
            images.add(new PostImage(image.mediaType(), image.bytes()));
        }
        return images;
    }
}
