package com.lexaid.community.posts;

import com.lexaid.common.PageResult;
import com.lexaid.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/community/posts")
@RequiredArgsConstructor
public class PostController {
    public record CommentInput(String content) {}

    private final PostService posts;

    @GetMapping
    public Result<PageResult<PostService.Post>> feed(@RequestParam(defaultValue = "all") String scope,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return Result.ok(posts.feed(scope, page, size));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PostService.Post> create(@RequestParam(defaultValue = "") String caption,
            @RequestParam(defaultValue = "PUBLIC") String visibility,
            @RequestParam(required = false) List<MultipartFile> images) {
        return Result.ok(posts.create(caption, visibility, PostImage.readAll(images)));
    }

    @GetMapping("/{id}")
    public Result<PostService.Post> get(@PathVariable long id) {
        return Result.ok(posts.get(id));
    }

    @GetMapping("/{id}/images/{imageId}")
    public ResponseEntity<byte[]> image(@PathVariable long id, @PathVariable long imageId) {
        PostImage image = posts.image(id, imageId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mediaType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(image.bytes());
    }

    @GetMapping("/{id}/comments")
    public Result<PageResult<PostService.Comment>> comments(@PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return Result.ok(posts.comments(id, page, size));
    }

    @PostMapping("/{id}/comments")
    public Result<PostService.Comment> comment(@PathVariable long id, @RequestBody CommentInput request) {
        return Result.ok(posts.addComment(id, request.content()));
    }

    @PutMapping("/{id}/like")
    public Result<Void> like(@PathVariable long id) {
        posts.like(id, true);
        return Result.ok();
    }

    @DeleteMapping("/{id}/like")
    public Result<Void> unlike(@PathVariable long id) {
        posts.like(id, false);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        posts.delete(id);
        return Result.ok();
    }
}
