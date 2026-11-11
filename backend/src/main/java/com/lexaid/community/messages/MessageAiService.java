package com.lexaid.community.messages;

import com.lexaid.ai.DeepSeekClient;
import com.lexaid.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 私信文本翻译。使用现有 AI 客户端，不持久化译文。
 */
@Service
@RequiredArgsConstructor
public class MessageAiService {

    private final MessageService messages;
    private final DeepSeekClient ai;

    public record Translation(long messageId, String translation) {}

    public Translation translate(long viewer, long messageId) {
        MessageService.Message message = messages.visible(viewer, messageId);
        String text = message.content();
        if (text == null || text.isBlank()) {
            throw new BizException("这条消息没有可翻译的文字");
        }
        if (!ai.isConfigured()) {
            throw new BizException(503, "翻译服务尚未配置 AI 密钥");
        }
        try {
            String translation = ai.complete(List.of(
                    Map.of("role", "system", "content",
                            "你是法律学习助手。把用户给出的聊天消息翻译成中文；如果原文已经是中文，"
                                    + "则翻译成英文。只输出译文，不要解释、不要加引号。"),
                    Map.of("role", "user", "content", text)), false).strip();
            if (translation.isEmpty()) throw new BizException(502, "翻译服务没有返回内容");
            return new Translation(messageId, translation);
        } catch (BizException e) {
            throw e;
        } catch (DeepSeekClient.UnavailableException e) {
            throw new BizException(503, e.getMessage());
        } catch (RuntimeException e) {
            throw new BizException(502, "翻译失败，请稍后再试");
        }
    }
}
