package com.lexaid.note;

import com.lexaid.common.Result;
import com.lexaid.common.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
