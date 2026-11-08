package com.lexaid.ai.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.ai.ChatMessage;
import com.lexaid.ai.ChatMessageMapper;
import com.lexaid.ai.dto.ChatMessageVO;
import com.lexaid.common.TextUtil;
import com.lexaid.relation.RelatedItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SiteChatHistory {
    private static final String HIDDEN = "这条历史回答引用的资料已不可查看。";
    private final ChatMessageMapper messages;
    private final ObjectMapper json;
    private final SiteSources sources;

    public List<ChatMessageVO> recent(long viewer) {
        List<ChatMessage> rows = messages.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, viewer).orderByDesc(ChatMessage::getId).last("LIMIT 20"));
        Collections.reverse(rows);
        Map<String, Boolean> access = new HashMap<>();
        return rows.stream().map(row -> {
            List<RelatedItem> refs = refs(row);
            boolean visible = readable(viewer, refs, access, 0);
            return new ChatMessageVO(row.getId(), row.getRole(), visible ? row.getContent() : HIDDEN,
                    visible ? refs : List.of(), row.getCreatedAt());
        }).toList();
    }

    public RelatedItem find(long viewer, long id, List<String> terms) {
        return cite(viewer, owned(viewer, id), terms, new HashMap<>());
    }

    public List<RelatedItem> search(long viewer, List<String> terms, int limit) {
        LambdaQueryWrapper<ChatMessage> query = new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getUserId, viewer);
        if (!terms.isEmpty()) {
            query.and(w -> {
                for (String term : terms) w.or().apply("LOCATE({0},content) > 0", term);
            });
        }
        List<ChatMessage> rows = messages.selectList(query.orderByDesc(ChatMessage::getId).last("LIMIT 40"));
        Map<String, Boolean> access = new HashMap<>();
        List<RelatedItem> hits = new ArrayList<>();
        for (ChatMessage row : rows) {
            RelatedItem hit = cite(viewer, row, terms, access);
            if (hit != null) hits.add(hit);
            if (hits.size() == limit) break;
        }
        return hits;
    }

    private ChatMessage owned(long viewer, long id) {
        return messages.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, viewer).eq(ChatMessage::getId, id));
    }

    private RelatedItem cite(long viewer, ChatMessage row, List<String> terms, Map<String, Boolean> access) {
        if (row == null || !readable(viewer, refs(row), access, 0)) return null;
        String content = row.getContent();
        int offset = 0;
        for (String term : terms) {
            int at = content.indexOf(term);
            if (at >= 0) { offset = Math.max(0, at - 120); break; }
        }
        String role = "USER".equals(row.getRole()) ? "我的提问" : "历史 AI 回答";
        return new RelatedItem("CHAT", row.getId(), role + " · " + TextUtil.excerpt(content, 40),
                (offset > 0 ? "…" : "") + TextUtil.excerpt(content.substring(offset), 1300));
    }

    /** A saved answer can quote another answer. Recheck the whole chain before returning its text. */
    private boolean readable(long viewer, List<RelatedItem> refs, Map<String, Boolean> access, int depth) {
        if (refs == null || depth > 8) return false;
        for (RelatedItem ref : refs) {
            if (ref == null || ref.type() == null || ref.id() == null) return false;
            if (SiteQueryPlanner.KNOWLEDGE.contains(ref.type())) continue;
            String key = ref.type() + ":" + ref.id();
            Boolean allowed = access.get(key);
            if (allowed == null) {
                access.put(key, false);
                if ("CHAT".equals(ref.type())) {
                    ChatMessage target = owned(viewer, ref.id());
                    allowed = target != null && readable(viewer, refs(target), access, depth + 1);
                } else {
                    allowed = sources.find(viewer, ref.type(), ref.id(), List.of()) != null;
                }
                access.put(key, allowed);
            }
            if (!allowed) return false;
        }
        return true;
    }

    private List<RelatedItem> refs(ChatMessage row) {
        if (row.getRefs() == null) return List.of();
        try {
            return json.readValue(row.getRefs(), new TypeReference<List<RelatedItem>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            return null;
        }
    }
}
