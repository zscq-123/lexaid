package com.lexaid.note;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lexaid.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FavoriteFolderService {
    private static final Pattern OUTER_WHITESPACE = Pattern.compile("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$");
    private final FavoriteFolderMapper folderMapper;
    private final FavoriteMapper favoriteMapper;

    public record FolderView(Long id, String name) {}

    public List<FolderView> list(Long userId) {
        return folderMapper.selectList(new LambdaQueryWrapper<FavoriteFolder>()
                        .eq(FavoriteFolder::getUserId, userId).orderByAsc(FavoriteFolder::getId))
                .stream().map(f -> new FolderView(f.getId(), f.getName())).toList();
    }

    public FolderView create(Long userId, String name) {
        FavoriteFolder folder = new FavoriteFolder();
        folder.setName(cleanName(name));
        folder.setUserId(userId);
        try {
            folderMapper.insert(folder);
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "分区名称已存在");
        }
        return new FolderView(folder.getId(), folder.getName());
    }

    @Transactional
    public FolderView rename(Long userId, Long id, String name) {
        String cleaned = cleanName(name);
        lockFolder(userId, id);
        try {
            folderMapper.update(null, new LambdaUpdateWrapper<FavoriteFolder>()
                    .eq(FavoriteFolder::getUserId, userId).eq(FavoriteFolder::getId, id)
                    .set(FavoriteFolder::getName, cleaned));
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "分区名称已存在");
        }
        return new FolderView(id, cleaned);
    }

    public void delete(Long userId, Long id) {
        // The folder_id foreign key returns the favorites to the unfiled section atomically.
        if (folderMapper.delete(new LambdaQueryWrapper<FavoriteFolder>()
                .eq(FavoriteFolder::getUserId, userId).eq(FavoriteFolder::getId, id)) == 0) {
            throw new BizException(404, "分区不存在");
        }
    }

    @Transactional
    public void move(Long userId, Long favoriteId, Long folderId) {
        if (folderId != null) lockFolder(userId, folderId);
        Favorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getId, favoriteId).last("FOR UPDATE"));
        if (favorite == null) throw new BizException(404, "收藏不存在");
        favoriteMapper.update(null, new LambdaUpdateWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getId, favoriteId)
                .set(Favorite::getFolderId, folderId));
    }

    private void lockFolder(Long userId, Long id) {
        FavoriteFolder folder = folderMapper.selectOne(new LambdaQueryWrapper<FavoriteFolder>()
                .eq(FavoriteFolder::getUserId, userId).eq(FavoriteFolder::getId, id).last("FOR UPDATE"));
        if (folder == null) throw new BizException(404, "分区不存在");
    }

    private static String cleanName(String name) {
        String cleaned = name == null ? "" : OUTER_WHITESPACE.matcher(name).replaceAll("");
        if (cleaned.isEmpty() || cleaned.codePointCount(0, cleaned.length()) > 40) {
            throw new BizException("分区名称需为 1～40 个字符");
        }
        if (cleaned.equals("未分区") || cleaned.equals("全部分区")) {
            throw new BizException("该名称已由系统使用");
        }
        return cleaned;
    }
}
