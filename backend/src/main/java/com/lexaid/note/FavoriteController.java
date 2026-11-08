package com.lexaid.note;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lexaid.common.Result;
import com.lexaid.common.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收藏接口
 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final FavoriteFolderService folderService;

    public record FolderNameRequest(String name) {}
    public record MoveRequest(@JsonProperty(value = "folderId", required = true) @Positive Long folderId) {}

    @GetMapping("/folders")
    public Result<List<FavoriteFolderService.FolderView>> folders() {
        return Result.ok(folderService.list(UserContext.userId()));
    }

    @PostMapping("/folders")
    public Result<FavoriteFolderService.FolderView> createFolder(@RequestBody FolderNameRequest req) {
        return Result.ok(folderService.create(UserContext.userId(), req.name()));
    }

    @PutMapping("/folders/{id}")
    public Result<FavoriteFolderService.FolderView> renameFolder(@PathVariable Long id, @RequestBody FolderNameRequest req) {
        return Result.ok(folderService.rename(UserContext.userId(), id, req.name()));
    }

    @DeleteMapping("/folders/{id}")
    public Result<Void> deleteFolder(@PathVariable Long id) {
        folderService.delete(UserContext.userId(), id);
        return Result.ok();
    }

    @PutMapping("/{id}/folder")
    public Result<Void> move(@PathVariable Long id, @Valid @RequestBody MoveRequest req) {
        folderService.move(UserContext.userId(), id, req.folderId());
        return Result.ok();
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody FavoriteRequest req) {
        favoriteService.add(UserContext.userId(), req.getTargetType(), req.getTargetId());
        return Result.ok();
    }

    @GetMapping
    public Result<List<FavoriteVO>> list(@RequestParam(required = false) String type) {
        return Result.ok(favoriteService.list(UserContext.userId(), type));
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        favoriteService.remove(UserContext.userId(), id);
        return Result.ok();
    }
}
