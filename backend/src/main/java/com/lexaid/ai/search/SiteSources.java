package com.lexaid.ai.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexaid.auth.User;
import com.lexaid.auth.UserMapper;
import com.lexaid.relation.RelatedItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteSources {
    private final PublicSiteSearch knowledge;
    private final PersonalSiteSearch personal;
    private final SiteCatalog catalog;
    private final UserMapper users;

    public List<RelatedItem> search(long viewer, List<String> terms, String scope, String query, int limit) {
        if (scope == null) {
            List<RelatedItem> publicHits = knowledge.search(terms, null, limit);
            List<RelatedItem> privateHits = personal.search(viewer, terms, null, limit);
            // Interleave matches: a common term in statutes should not hide a post or the reader's note.
            List<RelatedItem> hits = new ArrayList<>();
            for (int i = 0; i < Math.max(publicHits.size(), privateHits.size()); i++) {
                if (i < publicHits.size()) hits.add(publicHits.get(i));
                if (i < privateHits.size()) hits.add(privateHits.get(i));
            }
            return hits.stream().limit(limit).toList();
        }
        if (SiteQueryPlanner.KNOWLEDGE.contains(scope) || "PAPER".equals(scope)) return knowledge.search(terms, scope, limit);
        if (SiteQueryPlanner.PERSONAL.contains(scope)) return personal.search(viewer, terms, scope, limit);
        if ("CATALOG".equals(scope)) return catalog.search(viewer, query, terms);
        if ("PROFILE".equals(scope)) {
            RelatedItem profile = profile(viewer);
            return profile == null ? List.of() : List.of(profile);
        }
        return List.of();
    }

    public RelatedItem find(long viewer, String type, long id, List<String> terms) {
        if (SiteQueryPlanner.KNOWLEDGE.contains(type) || "PAPER".equals(type)) return knowledge.find(type, id, terms);
        if (SiteQueryPlanner.PERSONAL.contains(type)) return personal.find(viewer, type, id, terms);
        if ("PROFILE".equals(type) && viewer == id) return profile(viewer);
        if ("CATALOG".equals(type)) return catalog.find(viewer, id);
        return null;
    }

    private RelatedItem profile(long viewer) {
        User u = users.selectOne(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getUsername, User::getNickname, User::getRole).eq(User::getId, viewer));
        if (u == null) return null;
        return new RelatedItem("PROFILE", viewer, "我的个人资料", "用户名：" + u.getUsername() + "，昵称："
                + (u.getNickname() == null ? "未设置" : u.getNickname()) + "，角色："
                + ("ADMIN".equals(u.getRole()) ? "管理员" : "学习者") + "。");
    }
}
