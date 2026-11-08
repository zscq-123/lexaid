package com.lexaid.note;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexaid.common.BizException;
import com.lexaid.content.EntityChecker;
import com.lexaid.content.EntityTitleService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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
    private final EntityTitleService titleService;

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
        } catch (DuplicateKeyException ignored) {
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
        Map<String, List<Long>> idsByType = new HashMap<>();
        for (Favorite favorite : favs) {
            idsByType.computeIfAbsent(favorite.getTargetType(), k -> new ArrayList<>()).add(favorite.getTargetId());
        }
        Map<String, Map<Long, String>> titles = new HashMap<>();
        idsByType.forEach((targetType, ids) -> titles.put(targetType, titleService.batch(targetType, ids)));
        return favs.stream().map(favorite -> {
            String title = titles.get(favorite.getTargetType()).getOrDefault(favorite.getTargetId(), "内容已删除");
            return new FavoriteVO(favorite.getId(), favorite.getTargetType(), favorite.getTargetId(),
                    title, favorite.getCreatedAt(), favorite.getFolderId());
        }).toList();
    }
}
