package com.lexaid.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexaid.content.dto.CourtOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.lexaid.content.CourtRegionClassifier.GUIDING_DATASET;

@Service
@RequiredArgsConstructor
public class CaseCourtDirectory {
    private static final Map<String, String> JURISDICTIONS = Map.of(
            "CN", "中国", "US", "美国", "ECHR", "欧洲人权法院", "EU", "欧盟", "GB", "英国", "INTL", "国际");

    private final CaseInfoMapper mapper;

    public List<CourtOption> options() {
        return load(null).options();
    }

    public boolean applyFilter(LambdaQueryWrapper<CaseInfo> query, String key) {
        int separator = key.indexOf('/');
        if (separator < 0) {
            query.eq(CaseInfo::getJurisdiction, key);
            return true;
        }
        String jurisdiction = key.substring(0, separator);
        if (jurisdiction.isBlank()) return false;
        Node node = load(jurisdiction).byKey.get(key);
        if (node == null) return false;
        query.eq(CaseInfo::getJurisdiction, jurisdiction);
        node.restrictCases(query);
        return true;
    }

    private Directory load(String jurisdiction) {
        Directory directory = new Directory();
        if (jurisdiction == null || !"CN".equals(jurisdiction)) {
            var international = new LambdaQueryWrapper<CaseInfo>()
                    .select(CaseInfo::getJurisdiction, CaseInfo::getCourt)
                    .ne(CaseInfo::getJurisdiction, "CN")
                    .eq(jurisdiction != null, CaseInfo::getJurisdiction, jurisdiction)
                    .groupBy(CaseInfo::getJurisdiction, CaseInfo::getCourt);
            mapper.selectList(international).forEach(directory::add);
        }
        if (jurisdiction == null || "CN".equals(jurisdiction)) {
            // The docket can disambiguate a historical place name. Only guiding cases
            // additionally need the holding in order to identify the deciding court.
            var domestic = new LambdaQueryWrapper<CaseInfo>()
                    .select(CaseInfo::getId, CaseInfo::getJurisdiction, CaseInfo::getCourt,
                            CaseInfo::getCaseNo, CaseInfo::getSourceDataset)
                    .eq(CaseInfo::getJurisdiction, "CN")
                    .and(s -> s.isNull(CaseInfo::getSourceDataset).or().ne(CaseInfo::getSourceDataset, GUIDING_DATASET));
            var guiding = new LambdaQueryWrapper<CaseInfo>()
                    .select(CaseInfo::getId, CaseInfo::getJurisdiction, CaseInfo::getCourt,
                            CaseInfo::getCaseNo, CaseInfo::getSourceDataset, CaseInfo::getHolding)
                    .eq(CaseInfo::getJurisdiction, "CN")
                    .eq(CaseInfo::getSourceDataset, GUIDING_DATASET);
            mapper.selectList(domestic).forEach(directory::add);
            mapper.selectList(guiding).forEach(directory::add);
        }
        return directory;
    }

    static CourtRegionClassifier.Court classify(CaseInfo row) {
        return CourtRegionClassifier.classify(row.getJurisdiction(), row.getCourt(), row.getCaseNo(),
                row.getHolding(), row.getSourceDataset());
    }

    private static final class Directory {
        private final Map<String, Node> roots = new HashMap<>();
        private final Map<String, Node> byKey = new HashMap<>();

        private void add(CaseInfo row) {
            String jurisdiction = row.getJurisdiction();
            Node parent = roots.computeIfAbsent(jurisdiction, key ->
                    new Node(key, JURISDICTIONS.getOrDefault(key, key)));
            CourtRegionClassifier.Court court = classify(row);
            parent = append(parent, "province", court.province(), row);
            parent = append(parent, "city", court.city(), row);
            parent = append(parent, "district", court.district(), row);
            append(parent, "court", court.name() != null ? court.name() : "审理法院未注明", row);
        }

        private Node append(Node parent, String level, String label, CaseInfo row) {
            if (!StringUtils.hasText(label)) return parent;
            String segment = label.replace("%", "%25").replace("/", "%2F");
            String key = parent.key + "/" + level + ":" + segment;
            Node node = byKey.computeIfAbsent(key, k -> {
                Node child = new Node(k, label);
                parent.children.add(child);
                return child;
            });
            if ("CN".equals(row.getJurisdiction())) node.caseIds.add(row.getId());
            else if (row.getCourt() == null) node.includesNullCourt = true;
            else node.courtNames.add(row.getCourt());
            return node;
        }

        private List<CourtOption> options() {
            Collator collation = Collator.getInstance(Locale.CHINA);
            Comparator<Node> order = Comparator.comparing((Node n) -> n.label, collation).thenComparing(n -> n.key);
            return roots.values().stream().sorted(order).map(n -> n.option(order)).toList();
        }
    }

    private static final class Node {
        private final String key;
        private final String label;
        private final List<Node> children = new ArrayList<>();
        private final List<Long> caseIds = new ArrayList<>();
        private final Set<String> courtNames = new LinkedHashSet<>();
        private boolean includesNullCourt;

        private Node(String key, String label) {
            this.key = key;
            this.label = label;
        }

        private CourtOption option(Comparator<Node> order) {
            return new CourtOption(key, label, children.stream().sorted(order).map(n -> n.option(order)).toList());
        }

        private void restrictCases(LambdaQueryWrapper<CaseInfo> query) {
            if (!caseIds.isEmpty()) {
                query.in(CaseInfo::getId, caseIds);
                return;
            }
            query.and(c -> {
                c.in(!courtNames.isEmpty(), CaseInfo::getCourt, courtNames);
                if (includesNullCourt) c.or(!courtNames.isEmpty()).isNull(CaseInfo::getCourt);
            });
        }
    }
}
