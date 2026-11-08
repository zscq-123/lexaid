package com.lexaid.admin;

import com.lexaid.common.PageResult;
import com.lexaid.common.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/overview")
    public Result<AdminService.Overview> overview(
            @RequestParam(defaultValue = "14") @Min(7) @Max(90) int days) {
        return Result.ok(adminService.overview(days));
    }

    @GetMapping("/users")
    public Result<PageResult<AdminService.UserSummary>> users(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "all") String activity,
            @RequestParam(defaultValue = "lastActive") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(adminService.users(keyword, role, activity, sort, order, page, size));
    }

    @GetMapping("/users/{id}")
    public Result<AdminService.UserDetail> user(@PathVariable @Min(1) long id) {
        return Result.ok(adminService.user(id));
    }
}
