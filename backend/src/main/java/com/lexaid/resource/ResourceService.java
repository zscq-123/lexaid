package com.lexaid.resource;

import com.lexaid.common.BizException;
import com.lexaid.common.PageResult;
import com.lexaid.common.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Learning-resource library. A resource is always owned by one user; PRIVATE files are only
 * readable by that owner, while PUBLIC files form the community library. File bytes never enter
 * list responses.
 */
@Service
@RequiredArgsConstructor
public class ResourceService {

    public record ResourceSummary(long id, long ownerId, String ownerName, String ownerAvatarVersion,
                                  String title, String description, String category, String visibility,
                                  String fileName, String fileExtension, String mediaType, long fileSize,
                                  long downloadCount, LocalDateTime createdAt, LocalDateTime updatedAt,
                                  boolean owned) {}

    public record Download(String fileName, String mediaType, byte[] bytes) {}

    private static final Set<String> CATEGORIES = Set.of(
            "法考资料", "课程讲义", "案例研习", "复习笔记", "真题试卷", "其他");
    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "PUBLIC");
    private static final Set<String> FORMATS = Set.of("pdf", "docx", "pptx", "xlsx", "txt", "md");
    private static final Set<String> SCOPES = Set.of("community", "mine");
    private static final Set<String> SORTS = Set.of("latest", "popular");

    private static final String SELECT = """
            SELECT r.id, r.owner_id, COALESCE(NULLIF(u.nickname,''), u.username) AS owner_name,
                   a.version AS owner_avatar_version, r.title, r.description, r.category, r.visibility,
                   r.file_name, r.file_ext, r.media_type, r.file_size, r.download_count,
                   r.created_at, r.updated_at
            FROM learning_resource r
            JOIN `user` u ON u.id = r.owner_id
            LEFT JOIN user_avatar a ON a.user_id = r.owner_id
            """;

    private final NamedParameterJdbcTemplate db;

    @Transactional(readOnly = true)
    public PageResult<ResourceSummary> list(String scope, String keyword, String category, String format,
                                            String sort, int page, int size) {
        long viewer = UserContext.userId();
        String audience = normalizeScope(scope);
        if (page < 1 || size < 1 || size > 50) throw new BizException("页码须为正数，每页最多 50 条");
        String text = limit(keyword == null ? "" : keyword.strip(), "搜索关键词", 120);
        String selectedCategory = category == null ? "" : category.strip();
        if (!selectedCategory.isEmpty() && !CATEGORIES.contains(selectedCategory)) {
            throw new BizException("未知的资源分类");
        }
        String selectedFormat = format == null ? "" : format.strip().toLowerCase(Locale.ROOT);
        if (!selectedFormat.isEmpty() && !FORMATS.contains(selectedFormat)) {
            throw new BizException("未知的资源格式");
        }
        String order = switch (sort == null ? "latest" : sort.strip().toLowerCase(Locale.ROOT)) {
            case "latest" -> "r.id DESC";
            case "popular" -> "r.download_count DESC, r.id DESC";
            default -> throw new BizException("未知的资源排序方式");
        };

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("viewer", viewer);
        StringBuilder where = new StringBuilder(" WHERE ").append("community".equals(audience)
                ? "r.visibility = 'PUBLIC'" : "r.owner_id = :viewer");
        if (!text.isEmpty()) {
            where.append(" AND (r.title LIKE :keyword OR r.description LIKE :keyword OR r.file_name LIKE :keyword)");
            params.put("keyword", "%" + escapeLike(text) + "%");
        }
        if (!selectedCategory.isEmpty()) {
            where.append(" AND r.category = :category");
            params.put("category", selectedCategory);
        }
        if (!selectedFormat.isEmpty()) {
            where.append(" AND r.file_ext = :format");
            params.put("format", selectedFormat);
        }
        params.put("size", size);
        params.put("skip", (long) (page - 1) * size);

        Long total = db.queryForObject("SELECT COUNT(*) FROM learning_resource r" + where, params, Long.class);
        List<ResourceSummary> records = db.query(
                SELECT + where + " ORDER BY " + order + " LIMIT :size OFFSET :skip",
                params, summaryMapper(viewer));
        return new PageResult<>(records, total == null ? 0 : total);
    }

    @Transactional
    public ResourceSummary create(String title, String description, String category, String visibility,
                                  MultipartFile upload) {
        long viewer = UserContext.userId();
        String suppliedTitle = limit(title == null ? "" : title.strip(), "资源标题", 120);
        String cleanDescription = limit(description == null ? "" : description.strip(), "资源说明", 1000);
        String cleanCategory = category(category, true);
        String cleanVisibility = visibility(visibility, true);
        ResourceFile file = ResourceFile.read(upload);
        String name = suppliedTitle.isEmpty() ? limit(file.baseName(), "资源标题", 120, true) : suppliedTitle;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("owner", viewer)
                .addValue("title", name)
                .addValue("description", cleanDescription)
                .addValue("category", cleanCategory)
                .addValue("visibility", cleanVisibility)
                .addValue("fileName", file.fileName())
                .addValue("extension", file.extension())
                .addValue("mediaType", file.mediaType())
                .addValue("fileSize", (long) file.bytes().length)
                .addValue("fileBytes", file.bytes());
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        db.update("""
                INSERT INTO learning_resource
                    (owner_id,title,description,category,visibility,file_name,file_ext,media_type,file_size,file_bytes)
                VALUES
                    (:owner,:title,:description,:category,:visibility,:fileName,:extension,:mediaType,:fileSize,:fileBytes)
                """, params, keys, new String[]{"id"});
        long id = Objects.requireNonNull(keys.getKey()).longValue();
        return summary(id, viewer);
    }

    @Transactional
    public ResourceSummary update(long id, String title, String description, String category, String visibility) {
        long viewer = UserContext.userId();
        String name = limit(title == null ? "" : title.strip(), "资源标题", 120, true);
        String cleanDescription = limit(description == null ? "" : description.strip(), "资源说明", 1000);
        String cleanCategory = category(category, false);
        String cleanVisibility = visibility(visibility, false);
        int changed = db.update("""
                UPDATE learning_resource
                SET title = :title, description = :description, category = :category, visibility = :visibility
                WHERE id = :id AND owner_id = :viewer
                """, Map.of("id", id, "viewer", viewer, "title", name, "description", cleanDescription,
                "category", cleanCategory, "visibility", cleanVisibility));
        if (changed == 0) throw notFound();
        return summary(id, viewer);
    }

    @Transactional
    public void delete(long id) {
        long viewer = UserContext.userId();
        if (db.update("DELETE FROM learning_resource WHERE id = :id AND owner_id = :viewer",
                Map.of("id", id, "viewer", viewer)) == 0) {
            throw notFound();
        }
    }

    @Transactional
    public Download download(long id) {
        long viewer = UserContext.userId();
        if (id <= 0) throw notFound();
        Download file = db.query("""
                        SELECT r.file_name, r.media_type, r.file_bytes
                        FROM learning_resource r
                        WHERE r.id = :id AND (r.visibility = 'PUBLIC' OR r.owner_id = :viewer)
                        """, Map.of("id", id, "viewer", viewer),
                (rs, row) -> new Download(rs.getString("file_name"), rs.getString("media_type"),
                        rs.getBytes("file_bytes")))
                .stream().findFirst().orElseThrow(ResourceService::notFound);
        // Keep updated_at stable: a download is not an edit.
        db.update("UPDATE learning_resource SET download_count = download_count + 1, updated_at = updated_at "
                + "WHERE id = :id", Map.of("id", id));
        return file;
    }

    private ResourceSummary summary(long id, long viewer) {
        return db.query(SELECT + " WHERE r.id = :id AND (r.visibility = 'PUBLIC' OR r.owner_id = :viewer)",
                        Map.of("id", id, "viewer", viewer), summaryMapper(viewer))
                .stream().findFirst().orElseThrow(ResourceService::notFound);
    }

    private static RowMapper<ResourceSummary> summaryMapper(long viewer) {
        return (rs, row) -> {
            long owner = rs.getLong("owner_id");
            Timestamp created = rs.getTimestamp("created_at");
            Timestamp updated = rs.getTimestamp("updated_at");
            return new ResourceSummary(rs.getLong("id"), owner, rs.getString("owner_name"),
                    rs.getString("owner_avatar_version"), rs.getString("title"), rs.getString("description"),
                    rs.getString("category"), rs.getString("visibility"), rs.getString("file_name"),
                    rs.getString("file_ext"), rs.getString("media_type"), rs.getLong("file_size"),
                    rs.getLong("download_count"),
                    created == null ? null : created.toLocalDateTime(),
                    updated == null ? null : updated.toLocalDateTime(),
                    owner == viewer);
        };
    }

    private static String normalizeScope(String value) {
        String scope = value == null ? "community" : value.strip().toLowerCase(Locale.ROOT);
        if (!SCOPES.contains(scope)) throw new BizException("未知的资源范围");
        return scope;
    }

    private static String category(String value, boolean allowDefault) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() && allowDefault) return "其他";
        if (!CATEGORIES.contains(text)) throw new BizException("请选择有效的资源分类");
        return text;
    }

    private static String visibility(String value, boolean allowDefault) {
        String text = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (text.isEmpty() && allowDefault) return "PRIVATE";
        if (!VISIBILITIES.contains(text)) throw new BizException("请选择资源可见范围");
        return text;
    }

    private static String limit(String value, String label, int max) {
        return limit(value, label, max, false);
    }

    private static String limit(String value, String label, int max, boolean required) {
        String text = value == null ? "" : value.strip();
        int length = text.codePointCount(0, text.length());
        if (required && length == 0) throw new BizException("请填写" + label);
        if (length > max) throw new BizException(label + "不能超过 " + max + " 字");
        return text;
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static BizException notFound() {
        return new BizException(404, "资源不存在或不可见");
    }
}
