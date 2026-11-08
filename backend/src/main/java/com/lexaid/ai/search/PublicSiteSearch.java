package com.lexaid.ai.search;

import com.lexaid.content.CourtRegionClassifier;
import com.lexaid.relation.RelatedItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Retrieves short passages from the public knowledge base; only server-defined columns are searchable. */
@Service
@RequiredArgsConstructor
public class PublicSiteSearch {
    private final NamedParameterJdbcTemplate db;
    private static final String IS_PAPER = "summary_kind IN ('author_abstract','metadata_only')";

    private enum Source {
        PROVISION("legal_provision", "CONCAT_WS(' ',law_name,article_no)",
                "law_name,article_no,chapter,category,jurisdiction,issuing_authority,status,effective_date,promulgation_date,language_code,source_dataset,source_url,license_name",
                "content", "category,jurisdiction,issuing_authority,status,CONCAT('公布日期 ',promulgation_date),"
                        + "CONCAT('生效日期 ',effective_date),chapter,source_dataset,source_url,license_name"),
        CASE("case_info", "title",
                "title,case_no,CASE WHEN source_dataset='Chinese-Law-Corpus-Guiding-Cases' THEN NULL ELSE court END,"
                        + "case_type,judge_date,`procedure`,jurisdiction,source_dataset,source_url,license_name,language_code,status",
                "CONCAT_WS('\\n',summary,holding,content)",
                "case_no,case_type,judge_date,jurisdiction,`procedure`,source_dataset,source_url"),
        VIEWPOINT("scholar_viewpoint", "CONCAT_WS(' · ',title,scholar_name)",
                "title,scholar_name,source,doi,publish_year,language_code,source_dataset,source_url,license_name",
                "CONCAT_WS('\\n',summary,content)",
                "CASE WHEN " + IS_PAPER + " THEN CASE WHEN CHAR_LENGTH(TRIM(content)) > 0 THEN '法学论文（已收录正文）'"
                        + " WHEN summary_kind='author_abstract' AND CHAR_LENGTH(TRIM(summary)) > 0 THEN '法学论文（收录摘要，未收录全文）'"
                        + " ELSE '法学论文（仅收录题录，未收录全文）' END ELSE '学者观点' END,"
                        + "source,publish_year,doi,language_code,source_dataset,source_url,license_name"),
        QUESTION("question", "LEFT(stem,80)", "stem,category,type,difficulty",
                "CONCAT_WS('\\n',stem,options)", "category,type,CONCAT('难度 ',difficulty)");

        final String table;
        final String title;
        final String searchableHeader;
        final String body;
        final String description;

        Source(String table, String title, String headerFields, String body, String descriptionFields) {
            this.table = table;
            this.title = title;
            this.searchableHeader = "CONCAT_WS(' '," + headerFields + ",tag_names)";
            this.body = body;
            this.description = "CONCAT_WS(' · '," + descriptionFields + ",tag_names)";
        }

        String taggedTable() {
            return table + " LEFT JOIN (SELECT et.target_id,GROUP_CONCAT(t.name ORDER BY t.name SEPARATOR '、') AS tag_names"
                    + " FROM entity_tag et JOIN tag t ON t.id=et.tag_id WHERE et.target_type=:type"
                    + " GROUP BY et.target_id) AS tags ON tags.target_id=" + table + ".id";
        }
    }

    private record Hit(Source source, long id, int score) {}

    public List<RelatedItem> search(List<String> terms, String scope, int limit) {
        if (limit <= 0) return List.of();
        int take = Math.min(limit, 24);
        List<String> keywords = keywords(terms);
        if (scope == null && keywords.isEmpty()) return List.of();
        List<Hit> candidates = new ArrayList<>();
        boolean papersOnly = "PAPER".equals(scope);
        String entityScope = papersOnly ? Source.VIEWPOINT.name() : scope;
        for (Source source : Source.values()) {
            if (entityScope == null || source.name().equals(entityScope)) {
                candidates.addAll(rank(source, keywords, take, papersOnly));
            }
        }
        if (scope == null) candidates.sort(Comparator.comparingInt(Hit::score).reversed()
                .thenComparing(Hit::source).thenComparingLong(Hit::id));

        // Reserve room for one hit from each matching collection, then fill by relevance.
        Map<Source, Hit> best = new EnumMap<>(Source.class);
        for (Hit hit : candidates) best.putIfAbsent(hit.source(), hit);
        LinkedHashSet<Hit> selected = new LinkedHashSet<>();
        for (Hit hit : candidates) if (hit.equals(best.get(hit.source())) && selected.size() < take) selected.add(hit);
        for (Hit hit : candidates) if (selected.size() < take) selected.add(hit);
        Map<Source, Map<Long, RelatedItem>> documents = new EnumMap<>(Source.class);
        for (Source source : best.keySet()) {
            List<Long> ids = selected.stream().filter(hit -> hit.source() == source).map(Hit::id).toList();
            if (!ids.isEmpty()) documents.put(source, passages(source, ids, keywords, 850, papersOnly));
        }
        List<RelatedItem> results = new ArrayList<>();
        for (Hit hit : selected) {
            RelatedItem item = documents.get(hit.source()).get(hit.id());
            if (item != null) results.add(item);
        }
        return results;
    }

    public RelatedItem find(String type, long id, List<String> terms) {
        boolean papersOnly = "PAPER".equals(type);
        String entityType = papersOnly ? Source.VIEWPOINT.name() : type;
        for (Source source : Source.values()) {
            if (source.name().equals(entityType)) return passages(source, List.of(id), keywords(terms), 3600, papersOnly).get(id);
        }
        return null;
    }

    private List<Hit> rank(Source source, List<String> terms, int limit, boolean papersOnly) {
        MapSqlParameterSource params = parameters(terms).addValue("limit", limit).addValue("type", source.name());
        List<String> scores = new ArrayList<>();
        for (int i = 0; i < terms.size(); i++) {
            String param = ":t" + i;
            String term = terms.get(i);
            // A match in two independent terms outranks a single hit. Earlier terms break ties.
            scores.add("(CASE WHEN " + contains(source.searchableHeader, param, term) + " THEN " + (118-i)
                    + " WHEN " + contains(source.body, param, term) + " THEN " + (105-i) + " ELSE 0 END)");
        }
        String score = scores.isEmpty() ? "0" : String.join("+", scores);
        String order = scores.isEmpty() ? switch (source) {
            case PROVISION -> "COALESCE(promulgation_date,effective_date) DESC,id DESC";
            case CASE -> "judge_date DESC,id DESC";
            case VIEWPOINT -> "publish_year DESC,id DESC";
            case QUESTION -> "id DESC";
        } : "relevance DESC,id";
        String sql = "SELECT id," + score + " AS relevance FROM " + (scores.isEmpty() ? source.table : source.taggedTable())
                + (papersOnly ? " WHERE " + IS_PAPER : "")
                + (scores.isEmpty() ? "" : " HAVING relevance > 0")
                + " ORDER BY " + order + " LIMIT :limit";
        return db.query(sql, params, (rs, row) -> new Hit(source, rs.getLong("id"), rs.getInt("relevance")));
    }

    private static String contains(String field, String parameter, String keyword) {
        // A byte-wise search avoids costly linguistic collation on the entire corpus.
        // Fold case when searching text containing letters with case (e.g. English or a DOI).
        boolean needsCaseFold = !keyword.equals(keyword.toUpperCase(Locale.ROOT));
        String haystack = needsCaseFold ? "LOWER(" + field + ")" : field;
        return "LOCATE(BINARY " + parameter + ", BINARY " + haystack + ") > 0";
    }

    private Map<Long, RelatedItem> passages(Source source, List<Long> ids, List<String> terms, int length, boolean papersOnly) {
        MapSqlParameterSource params = parameters(terms).addValue("ids", ids).addValue("length", length).addValue("type", source.name());
        List<String> positions = new ArrayList<>();
        // Prefer a mention in the body that isn't already explained by the title/author.
        for (int i = 0; i < terms.size(); i++) {
            String term = ":t" + i;
            positions.add("CASE WHEN LOCATE(" + term + "," + source.searchableHeader + ")=0"
                    + " THEN NULLIF(LOCATE(" + term + "," + source.body + "),0) END");
        }
        for (int i = 0; i < terms.size(); i++) positions.add("NULLIF(LOCATE(:t" + i + "," + source.body + "),0)");
        String position = positions.isEmpty() ? "1" : "COALESCE(" + String.join(",", positions) + ",1)";
        // LOCATE on the text columns returns a character position; SUBSTRING uses that same unit.
        String sql = "SELECT id,LEFT(" + source.title + ",220) AS title,LEFT(" + source.description + ",380) AS header,"
                + (source == Source.CASE ? "jurisdiction,court,case_no,source_dataset,"
                + "CASE WHEN source_dataset='Chinese-Law-Corpus-Guiding-Cases' THEN holding END AS judgment," : "")
                + "SUBSTRING(" + source.body + ",GREATEST(1," + position + "-140),:length) AS passage FROM "
                + source.taggedTable() + " WHERE id IN (:ids)" + (papersOnly ? " AND " + IS_PAPER : "");
        Map<Long, RelatedItem> hits = new HashMap<>();
        db.query(sql, params, rs -> {
            long id = rs.getLong("id");
            String passage = rs.getString("passage");
            String header = rs.getString("header");
            if (source == Source.CASE) {
                String court = CourtRegionClassifier.classify(rs.getString("jurisdiction"), rs.getString("court"),
                        rs.getString("case_no"), rs.getString("judgment"), rs.getString("source_dataset")).name();
                if (court != null) header = "审理法院：" + court + " · " + header;
            }
            String summary = (header == null || header.isBlank() ? "" : header + "\n") + (passage == null ? "" : passage);
            hits.put(id, new RelatedItem(source.name(), id, rs.getString("title"), summary.strip()));
        });
        return hits;
    }

    private static MapSqlParameterSource parameters(List<String> terms) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (int i = 0; i < terms.size(); i++) params.addValue("t" + i, terms.get(i));
        return params;
    }

    private static List<String> keywords(List<String> terms) {
        if (terms == null) return List.of();
        return terms.stream().filter(term -> term != null && !term.isBlank())
                .map(term -> term.strip().toLowerCase(Locale.ROOT)).distinct().limit(4).toList();
    }
}
