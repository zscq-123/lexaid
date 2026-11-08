package com.lexaid.community.friends;

import com.lexaid.common.Result;
import com.lexaid.common.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

@RestController
@RequestMapping("/api/community/friends")
@RequiredArgsConstructor
@Validated
public class FriendController {
    private final FriendService service;

    public record Send(@NotNull @Positive Long userId, @Size(max=120) String message) {}
    public record Decision(@NotNull Boolean accept) {}

    @GetMapping
    public Result<List<FriendService.Person>> contacts() {
        return Result.ok(service.friends(UserContext.userId()));
    }

    @GetMapping("/search")
    public Result<List<FriendService.Person>> search(@RequestParam(defaultValue="") @Size(max=50) String q) {
        return Result.ok(service.search(UserContext.userId(), q));
    }

    @GetMapping("/users/{userId}")
    public Result<FriendService.Person> contact(@PathVariable @Positive long userId) {
        return Result.ok(service.contact(UserContext.userId(), userId));
    }

    @GetMapping("/requests")
    public Result<List<FriendService.Invitation>> requests() {
        return Result.ok(service.pending(UserContext.userId()));
    }

    @PostMapping("/requests")
    public Result<String> send(@Valid @RequestBody Send body) {
        return Result.ok(service.request(UserContext.userId(), body.userId(), body.message()));
    }

    @PutMapping("/requests/{requestId}")
    public Result<Void> decide(@PathVariable String requestId, @Valid @RequestBody Decision body) {
        service.decide(UserContext.userId(), requestId, body.accept());
        return Result.ok();
    }

    @DeleteMapping("/{userId}")
    public Result<Void> remove(@PathVariable @Positive long userId) {
        service.remove(UserContext.userId(), userId);
        return Result.ok();
    }
}
