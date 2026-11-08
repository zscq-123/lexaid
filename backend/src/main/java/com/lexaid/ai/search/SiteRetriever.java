package com.lexaid.ai.search;

import com.lexaid.ai.dto.ChatMessageVO;
import com.lexaid.ai.dto.ChatRequest;
import com.lexaid.common.BizException;
import com.lexaid.common.TextUtil;
import com.lexaid.relation.RelatedItem;
import com.lexaid.relation.RelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SiteRetriever {
    private static final int MAX_REFS = 14;
    private final SiteSources sources;
    private final SiteChatHistory history;
    private final SiteQueryPlanner planner;
    private final RelationService relations;

    public RelatedItem open(long viewer, String type, long id, String question) {
        String scope = SiteQueryPlanner.scope(type);
        if (scope == null || id <= 0 || (question != null && question.length() > 4000)) throw new BizException(400, "资料参数无效");
        List<String> terms = SiteQueryPlanner.local(question, scope).terms();
        RelatedItem item = find(viewer, scope, id, terms);
        if (item == null) throw new BizException(404, "资料不存在或已不可见");
        return item;
    }

    private RelatedItem find(long viewer, String type, long id, List<String> terms) {
        return "CHAT".equals(type) ? history.find(viewer, id, terms) : sources.find(viewer, type, id, terms);
    }

    public List<RelatedItem> retrieve(long viewer, ChatRequest req, List<ChatMessageVO> recent) {
        String scope = SiteQueryPlanner.scope(req.getContextType());
        Long id = req.getContextId();
        String question = req.getMessage();
        Map<String, RelatedItem> evidence = new LinkedHashMap<>();
        if (id != null) put(evidence, open(viewer, scope, id, question));
        boolean followup = isFollowup(question);
        List<String> previous = followup ? recent.stream().filter(row -> "USER".equals(row.role()))
                .map(row -> TextUtil.excerpt(row.content(), 300)).toList() : List.of();
        var plan = planner.plan(question, id == null ? scope : null, previous.stream().skip(Math.max(0, previous.size() - 2)).toList());
        if (id != null) {
            // Ask the same source for the passage nearest the model's keywords, even deep inside a long paper.
            RelatedItem match = find(viewer, scope, id, plan.terms());
            if (match == null) throw new BizException(404, "资料不存在或已不可见");
            put(evidence, match);
        } else if (followup) {
            for (int i = recent.size() - 1; i >= 0; i--) {
                ChatMessageVO row = recent.get(i);
                if (!"ASSISTANT".equals(row.role())) continue;
                for (RelatedItem ref : row.refs().stream().limit(3).toList()) {
                    if (scope == null || scope.equals(ref.type())) put(evidence, find(viewer, ref.type(), ref.id(), plan.terms()));
                }
                break;
            }
        }
        List<List<RelatedItem>> pools = new ArrayList<>();
        if (plan.scopes().isEmpty()) {
            pools.add(sources.search(viewer, plan.terms(), null, question, MAX_REFS));
            if (!plan.terms().isEmpty()) pools.add(history.search(viewer, plan.terms(), 2));
        } else {
            for (String type : plan.scopes()) {
                pools.add("CHAT".equals(type) ? history.search(viewer, plan.terms(), MAX_REFS)
                        : sources.search(viewer, plan.terms(), type, question, MAX_REFS));
            }
        }
        for (int i = 0; i < MAX_REFS && evidence.size() < MAX_REFS; i++) {
            for (List<RelatedItem> pool : pools) {
                if (i < pool.size() && evidence.size() < MAX_REFS) evidence.putIfAbsent(key(pool.get(i)), pool.get(i));
            }
        }
        if (evidence.isEmpty() && scope == null && !plan.scopes().isEmpty() && !plan.terms().isEmpty()
                && SiteQueryPlanner.local(question, null).scopes().isEmpty()) {
            for (RelatedItem item : sources.search(viewer, plan.terms(), null, question, MAX_REFS)) put(evidence, item);
        }
        if (id != null && SiteQueryPlanner.KNOWLEDGE.contains(scope) && evidence.size() < MAX_REFS) {
            var groups = relations.relatedGroups(scope, id);
            for (var group : List.of(groups.provisions(), groups.cases(), groups.viewpoints(), groups.questions())) {
                for (RelatedItem item : group) if (evidence.size() < MAX_REFS) evidence.putIfAbsent(key(item), item);
            }
        }
        return List.copyOf(evidence.values());
    }

    private static void put(Map<String, RelatedItem> evidence, RelatedItem item) {
        if (item != null) evidence.put(key(item), item);
    }

    private static String key(RelatedItem item) {
        return item.type() + ":" + item.id();
    }

    public static boolean isFollowup(String question) {
        return question.matches("(?is).*(?:上面|上述|刚才|前面提到|这(?:个|篇|条|道|份|些)|它的|继续|接着|还有哪些|再举|that (?:one|case|paper)|the previous).*");
    }
}
