package com.lexaid.resource;

import com.lexaid.common.BizException;
import com.lexaid.common.PageResult;
import com.lexaid.common.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
@Validated
public class ResourceController {

    public record UpdateRequest(String title, String description, String category, String visibility) {}

    private final ResourceService resources;

    @GetMapping
    public Result<PageResult<ResourceService.ResourceSummary>> list(
            @RequestParam(defaultValue = "community") String scope,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String format,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return Result.ok(resources.list(scope, keyword, category, format, sort, page, size));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResourceService.ResourceSummary> create(
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") String description,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String visibility,
            @RequestParam("file") MultipartFile file) {
        return Result.ok(resources.create(title, description, category, visibility, file));
    }

    @PutMapping("/{id}")
    public Result<ResourceService.ResourceSummary> update(@PathVariable long id,
                                                          @RequestBody UpdateRequest request) {
        if (request == null) throw new BizException("请求内容格式不正确");
        return Result.ok(resources.update(id, request.title(), request.description(),
                request.category(), request.visibility()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        resources.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable long id) {
        ResourceService.Download file = resources.download(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Download-Options", "noopen")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(file.bytes());
    }
}
