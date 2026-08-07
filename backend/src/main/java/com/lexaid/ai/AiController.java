package com.lexaid.ai;

import com.lexaid.ai.dto.ChatMessageVO;
import com.lexaid.ai.dto.ChatRequest;
import com.lexaid.ai.dto.ChatResponse;
import com.lexaid.common.Result;
import com.lexaid.common.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 问答接口
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/chat")
    public Result<ChatResponse> chat(@Valid @RequestBody ChatRequest req) {
        return Result.ok(aiService.chat(UserContext.userId(), req));
    }

    @GetMapping("/history")
    public Result<List<ChatMessageVO>> history() {
        return Result.ok(aiService.history(UserContext.userId()));
    }
}
