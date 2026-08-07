package com.lexaid.note;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexaid.common.BizException;
import com.lexaid.common.TextUtil;
import com.lexaid.content.CaseInfo;
import com.lexaid.content.CaseInfoMapper;
import com.lexaid.content.EntityChecker;
import com.lexaid.content.Provision;
import com.lexaid.content.ProvisionMapper;
import com.lexaid.content.ScholarViewpoint;
import com.lexaid.content.ScholarViewpointMapper;
import com.lexaid.exam.Question;
import com.lexaid.exam.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏服务
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final EntityChecker entityChecker;
    private final ProvisionMapper provisionMapper;
    private final CaseInfoMapper caseInfoMapper;
    private final ScholarViewpointMapper viewpointMapper;
    private final QuestionMapper questionMapper;

    public boolean isFavorite(Long userId, String type, Long id) {
        if (userId == null) {
            return false;
        }
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, type)
                .eq(Favorite::getTargetId, id));
        return count != null && count > 0;
    }

    /**
     * 添加收藏（幂等）
     */
    public void add(Long userId, String type, Long id) {
        entityChecker.checkExists(type, id);
        try {
            Favorite f = new Favorite();
            f.setUserId(userId);
            f.setTargetType(type);
            f.setTargetId(id);
            favoriteMapper.insert(f);
        } catch (Exception ignored) {
            // 唯一约束：已收藏
        }
    }

    public void remove(Long userId, Long favoriteId) {
        Favorite f = favoriteMapper.selectById(favoriteId);
        if (f == null) {
            throw new BizException("收藏不存在");
        }
        if (!f.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作他人的收藏");
        }
        favoriteMapper.deleteById(favoriteId);
    }

    /**
     * 收藏列表（可带类型筛选），附带目标标题
     */
    public List<FavoriteVO> list(Long userId, String type) {
        LambdaQueryWrapper<Favorite> qw = new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getId);
        if (StringUtils.hasText(type)) {
            qw.eq(Favorite::getTargetType, type);
        }
        List<Favorite> favs = favoriteMapper.selectList(qw);
        Map<String, List<Favorite>> byType = new HashMap<>();
        for (Favorite f : favs) {
            byType.computeIfAbsent(f.getTargetType(), k -> new ArrayList<>()).add(f);
        }
        Map<String, Map<Long, String>> titles = new HashMap<>();
        titles.put(EntityChecker.PROVISION, provisionTitles(byType.get(EntityChecker.PROVISION)));
        titles.put(EntityChecker.CASE, caseTitles(byType.get(EntityChecker.CASE)));
        titles.put(EntityChecker.VIEWPOINT, viewpointTitles(byType.get(EntityChecker.VIEWPOINT)));
        titles.put(EntityChecker.QUESTION, questionTitles(byType.get(EntityChecker.QUESTION)));

        List<FavoriteVO> vos = new ArrayList<>();
        for (Favorite f : favs) {
            String title = titles.getOrDefault(f.getTargetType(), Map.of())
                    .getOrDefault(f.getTargetId(), "内容已删除");
            vos.add(new FavoriteVO(f.getId(), f.getTargetType(), f.getTargetId(), title, f.getCreatedAt()));
        }
        return vos;
    }

    private Map<Long, String> provisionTitles(List<Favorite> favs) {
        Map<Long, String> m = new HashMap<>();
        if (favs == null || favs.isEmpty()) {
            return m;
        }
        List<Long> ids = favs.stream().map(Favorite::getTargetId).toList();
        for (Provision p : provisionMapper.selectBatchIds(ids)) {
            m.put(p.getId(), p.getLawName() + " " + p.getArticleNo());
        }
        return m;
    }

    private Map<Long, String> caseTitles(List<Favorite> favs) {
        Map<Long, String> m = new HashMap<>();
        if (favs == null || favs.isEmpty()) {
            return m;
        }
        List<Long> ids = favs.stream().map(Favorite::getTargetId).toList();
        for (CaseInfo c : caseInfoMapper.selectBatchIds(ids)) {
            m.put(c.getId(), c.getTitle());
        }
        return m;
    }

    private Map<Long, String> viewpointTitles(List<Favorite> favs) {
        Map<Long, String> m = new HashMap<>();
        if (favs == null || favs.isEmpty()) {
            return m;
        }
        List<Long> ids = favs.stream().map(Favorite::getTargetId).toList();
        for (ScholarViewpoint v : viewpointMapper.selectBatchIds(ids)) {
            m.put(v.getId(), v.getTitle() + "（" + v.getScholarName() + "）");
        }
        return m;
    }

    private Map<Long, String> questionTitles(List<Favorite> favs) {
        Map<Long, String> m = new HashMap<>();
        if (favs == null || favs.isEmpty()) {
            return m;
        }
        List<Long> ids = favs.stream().map(Favorite::getTargetId).toList();
        for (Question q : questionMapper.selectBatchIds(ids)) {
            m.put(q.getId(), TextUtil.excerpt(q.getStem(), 60));
        }
        return m;
    }
}
