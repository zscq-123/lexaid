package com.lexaid.note;

import com.lexaid.common.Result;
import com.lexaid.common.UserContext;
import jakarta.validation.Valid;
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
 * 笔记接口
 */
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    public Result<List<NoteVO>> list(@RequestParam(required = false) String targetType,
                                     @RequestParam(required = false) Long targetId) {
        return Result.ok(noteService.list(UserContext.userId(), targetType, targetId));
    }

    @PostMapping
    public Result<NoteVO> create(@Valid @RequestBody NoteRequest req) {
        return Result.ok(noteService.create(UserContext.userId(), req));
    }

    @PutMapping("/{id}")
    public Result<NoteVO> update(@PathVariable Long id, @Valid @RequestBody NoteRequest req) {
        return Result.ok(noteService.update(UserContext.userId(), id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noteService.delete(UserContext.userId(), id);
        return Result.ok();
    }
}
