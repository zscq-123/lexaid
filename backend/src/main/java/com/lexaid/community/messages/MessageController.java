package com.lexaid.community.messages;

import com.lexaid.common.Result;
import com.lexaid.common.UserContext;
import com.lexaid.note.FavoriteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Validated
public class MessageController {

    public record SendRequest(
            @NotNull(message = "请选择接收人") @Positive(message = "接收人无效") Long recipientId,
            @NotBlank(message = "消息内容不能为空") @Size(max = 4000, message = "消息内容过长") String content,
            Long quoteId) {}

    public record ForwardRequest(@NotNull(message = "请选择接收人") @Positive(message = "接收人无效") Long recipientId) {}

    public record RemindRequest(@NotNull(message = "请选择提醒时间") @Positive(message = "提醒时间无效") Integer minutes) {}

    public record Unread(long total) {}

    public record WsTicket(String ticket, long expiresIn) {}

    public record SettingsRequest(@NotNull(message = "请选择免打扰状态") Boolean muted,
                                  @NotNull(message = "请选择置顶状态") Boolean pinned) {}

    private final MessageService messages;
    private final MessageNotifier notifier;
    private final MessageTicketService tickets;
    private final FavoriteService favorites;
    private final MessageAiService messageAi;
    private final MessageReminderService reminders;

    @GetMapping("/conversations")
    public Result<List<MessageService.Conversation>> conversations() {
        return Result.ok(messages.conversations(UserContext.userId()));
    }

    @GetMapping("/conversations/{peerId}")
    public Result<MessageService.History> history(@PathVariable @Positive long peerId,
                                                  @RequestParam(required = false) Long beforeId,
                                                  @RequestParam(defaultValue = "30") @Min(1) @Max(50) int size) {
        return Result.ok(messages.history(UserContext.userId(), peerId, beforeId, size));
    }

    @PostMapping
    public Result<MessageService.Message> send(@Valid @RequestBody SendRequest request) {
        long viewer = UserContext.userId();
        MessageService.Message message = messages.send(viewer, request.recipientId(), request.content(), request.quoteId());
        notifier.messageCreated(message);
        return Result.ok(message);
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<MessageService.Message> sendAttachment(
            @RequestParam("recipientId") @Positive(message = "接收人无效") Long recipientId,
            @RequestParam(defaultValue = "") String content,
            @RequestParam(defaultValue = "FILE") String kind,
            @RequestParam(required = false) Integer duration,
            @RequestParam(required = false) Long quoteId,
            @RequestParam("file") MultipartFile file) {
        long viewer = UserContext.userId();
        MessageService.Message message = messages.sendAttachment(viewer, recipientId, kind, content, duration, file, quoteId);
        notifier.messageCreated(message);
        return Result.ok(message);
    }

    @GetMapping("/{id}/attachment")
    public ResponseEntity<byte[]> attachment(@PathVariable @Positive long id) {
        MessageService.Attachment file = messages.attachment(UserContext.userId(), id);
        ContentDisposition.Builder builder = MessageService.KIND_IMAGE.equals(file.kind())
                || MessageService.KIND_VOICE.equals(file.kind())
                ? ContentDisposition.inline() : ContentDisposition.attachment();
        ContentDisposition disposition = builder.filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Download-Options", "noopen")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(file.bytes());
    }

    @PostMapping("/{id}/forward")
    public Result<MessageService.Message> forward(@PathVariable @Positive long id,
                                                   @Valid @RequestBody ForwardRequest request) {
        long viewer = UserContext.userId();
        MessageService.Message message = messages.forward(viewer, id, request.recipientId());
        notifier.messageCreated(message);
        return Result.ok(message);
    }

    @PostMapping("/{id}/favorite")
    public Result<Void> favorite(@PathVariable @Positive long id) {
        long viewer = UserContext.userId();
        messages.visible(viewer, id);
        favorites.addMessageFavorite(viewer, id);
        return Result.ok();
    }

    @PostMapping("/{id}/remind")
    public Result<MessageReminderService.ReminderResult> remind(@PathVariable @Positive long id,
                                                               @Valid @RequestBody RemindRequest request) {
        return Result.ok(reminders.remind(UserContext.userId(), id, request.minutes()));
    }

    @PostMapping("/{id}/translate")
    public Result<MessageAiService.Translation> translate(@PathVariable @Positive long id) {
        return Result.ok(messageAi.translate(UserContext.userId(), id));
    }

    @DeleteMapping("/{id}")
    public Result<MessageService.DeleteResult> delete(@PathVariable @Positive long id) {
        long viewer = UserContext.userId();
        MessageService.DeleteResult result = messages.deleteForUser(viewer, id);
        notifier.messageDeleted(viewer, id, result.unreadTotal());
        return Result.ok(result);
    }

    @PutMapping("/{id}/recall")
    public Result<MessageService.Message> recall(@PathVariable @Positive long id) {
        long viewer = UserContext.userId();
        MessageService.Message message = messages.recall(viewer, id);
        notifier.messageRecalled(message);
        return Result.ok(message);
    }

    @GetMapping("/conversations/{peerId}/settings")
    public Result<MessageService.ConversationSettings> settings(@PathVariable @Positive long peerId) {
        return Result.ok(messages.settings(UserContext.userId(), peerId));
    }

    @PutMapping("/conversations/{peerId}/settings")
    public Result<MessageService.ConversationSettings> updateSettings(@PathVariable @Positive long peerId,
                                                                      @Valid @RequestBody SettingsRequest request) {
        return Result.ok(messages.updateSettings(UserContext.userId(), peerId, request.muted(), request.pinned()));
    }

    @GetMapping("/conversations/{peerId}/search")
    public Result<List<MessageService.Message>> search(@PathVariable @Positive long peerId,
                                                       @RequestParam(defaultValue = "") String keyword,
                                                       @RequestParam(defaultValue = "30") @Min(1) @Max(50) int limit) {
        return Result.ok(messages.search(UserContext.userId(), peerId, keyword, limit));
    }

    @DeleteMapping("/conversations/{peerId}")
    public Result<MessageService.ClearResult> clearConversation(@PathVariable @Positive long peerId) {
        long viewer = UserContext.userId();
        MessageService.ClearResult result = messages.clear(viewer, peerId);
        notifier.conversationCleared(viewer, peerId, result.unreadTotal());
        return Result.ok(result);
    }

    @PutMapping("/conversations/{peerId}/read")
    public Result<MessageService.ReadResult> read(@PathVariable @Positive long peerId) {
        long viewer = UserContext.userId();
        MessageService.ReadResult result = messages.markRead(viewer, peerId);
        if (result.updated() > 0) notifier.messagesRead(viewer, peerId, result.readAt());
        return Result.ok(result);
    }

    @GetMapping("/unread")
    public Result<Unread> unread() {
        return Result.ok(new Unread(messages.unreadTotal(UserContext.userId())));
    }

    @GetMapping("/ws-ticket")
    public ResponseEntity<Result<WsTicket>> ticket() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.ok(new WsTicket(tickets.issue(UserContext.userId()), tickets.ttlSeconds())));
    }
}
