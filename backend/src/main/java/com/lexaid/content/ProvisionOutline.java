package com.lexaid.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexaid.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProvisionOutline {
    static final String SEP = " · ";
    private static final Pattern HEADING = Pattern.compile("^(第[〇零一二三四五六七八九十百千万两0-9]+(?:分编|编|章|节))\\s*");
    private final ProvisionMapper mapper;
    private List<Code> catalog;
    private long catalogReadAt;

    public record Code(String jurisdiction, String lawName) {}
    public record SeedPath(long id, String path) {}
    public record Node(String label, String path, List<Node> children) {}

    static List<String> parts(String raw) {
        if (!StringUtils.hasText(raw)) return List.of();
        // Corpus paths use a spaced separator. Older Chinese teaching records omit the spaces.
        return Arrays.stream(raw.split("\\s+·\\s+|·(?=\\s*第)"))
                .map(String::strip).filter(s -> !s.isEmpty())
                .map(s -> HEADING.matcher(s).replaceFirst("$1 ").strip()).toList();
    }

    public synchronized List<Code> codes(String jurisdiction) {
        // The public corpus changes on import. Share one compact law list across requests.
        if (catalog == null || System.nanoTime() - catalogReadAt >= TimeUnit.HOURS.toNanos(1)) {
            catalog = List.copyOf(mapper.outlineCodes());
            catalogReadAt = System.nanoTime();
        }
        return StringUtils.hasText(jurisdiction)
                ? catalog.stream().filter(c -> jurisdiction.equalsIgnoreCase(c.jurisdiction())).toList() : catalog;
    }

    private record Branch(String label, String path, Map<String, Branch> children) {
        Node node() { return new Node(label, path, children.values().stream().map(Branch::node).toList()); }
    }

    static List<Node> tree(List<String> rawPaths) {
        Map<String, Branch> roots = new LinkedHashMap<>();
        for (String raw : rawPaths) {
            Map<String, Branch> level = roots;
            String path = "";
            for (String label : parts(raw)) {
                String key = path.isEmpty() ? label : path + SEP + label;
                Branch branch = level.computeIfAbsent(label, k -> new Branch(label, key, new LinkedHashMap<>()));
                level = branch.children;
                path = key;
            }
        }
        return roots.values().stream().map(Branch::node).toList();
    }

    public List<Node> tree(String jurisdiction, String code) {
        List<String> paths = new ArrayList<>(mapper.outlinePaths(jurisdiction, code));
        mapper.seedPaths(jurisdiction, code, null).forEach(p -> paths.add(p.path));
        return tree(paths);
    }

    static boolean within(String raw, String selected) {
        String path = String.join(SEP, parts(raw));
        return selected.equals(path) || path.startsWith(selected + SEP);
    }

    public void filter(LambdaQueryWrapper<Provision> query, String jurisdiction, String code, String path) {
        if (!StringUtils.hasText(code)) {
            if (StringUtils.hasText(path)) throw new BizException("请先选择法典或法律");
            return;
        }
        if (!StringUtils.hasText(jurisdiction)) throw new BizException("法典目录缺少法域");
        query.eq(Provision::getLawName, code).eq(Provision::getJurisdiction, jurisdiction);
        if (!StringUtils.hasText(path)) return;
        List<String> chapters = mapper.outlinePaths(jurisdiction, code).stream().filter(p -> within(p, path)).toList();
        List<Long> seeds = mapper.seedPaths(jurisdiction, code, null).stream()
                .filter(p -> within(p.path, path)).map(SeedPath::id).toList();
        if (chapters.isEmpty() && seeds.isEmpty()) {
            query.apply("1=0");
            return;
        }
        query.and(w -> {
            if (!chapters.isEmpty()) w.isNotNull(Provision::getSourceDataset).in(Provision::getChapter, chapters);
            if (!seeds.isEmpty()) {
                if (!chapters.isEmpty()) w.or();
                w.in(Provision::getId, seeds);
            }
        });
    }

    void fillLegacyPaths(List<Provision> provisions) {
        Map<Long, Provision> seeds = new HashMap<>();
        for (Provision p : provisions) if (p.getSourceDataset() == null) seeds.put(p.getId(), p);
        if (!seeds.isEmpty()) mapper.seedPaths(null, null, seeds.keySet())
                .forEach(p -> seeds.get(p.id).setChapter(p.path));
    }
}
