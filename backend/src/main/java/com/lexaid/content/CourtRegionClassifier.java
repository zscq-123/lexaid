package com.lexaid.content;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Classifies the court identified by the source, independently of the case's publishing institution. */
public final class CourtRegionClassifier {
    public static final String GUIDING_DATASET = "Chinese-Law-Corpus-Guiding-Cases";
    public record Court(String province, String city, String district, String name) {}

    private record Region(String province, String city, String district) {
        Region commonParent(Region other) {
            if (!Objects.equals(province, other.province)) return UNKNOWN;
            if (!Objects.equals(city, other.city)) return new Region(province, null, null);
            return new Region(province, city, Objects.equals(district, other.district) ? district : null);
        }
    }

    private static final Region UNKNOWN = new Region(null, null, null);
    private record TimedRegion(Region region, int first, int end) {
        boolean applies(int year) {
            return year == 0 || year >= first && (end == 0 || year < end);
        }
    }
    private static final Map<String, List<TimedRegion>> PLACES = loadPlaces();
    private static final Pattern CASE_YEAR = Pattern.compile("^[（(](\\d{4})[)）]");
    private static final Pattern COURT_SUFFIX = Pattern.compile(
            "(?:第[一二三四五六七八九十]+)?(?:高级|中级|基层)?人民法院(?:赔偿委员会)?$"
            + "|(?:知识产权|互联网|海事|金融|铁路运输(?:中级)?)法院$");
    // The exact docket must immediately follow the named court or a short judgment formula.
    private static final Pattern ISSUER = Pattern.compile(
            "(?:^|[。！？，；：,;:\\n])([\\p{IsHan}]{2,35}?(?:法院(?:赔偿委员会)?))"
            + "(?:经审理)?(?:于?\\d{4}年(?:\\d{1,2}月(?:\\d{1,2}日)?)?)?(?:作出(?:的|了)?|以)?$");
    private static final Pattern SPACE = Pattern.compile("[\\s\\p{Z}]+");
    private static final Set<String> SPECIAL_AREAS = Set.of(
            "高新技术产业开发区", "经济技术开发区", "经济开发区", "自由贸易试验区", "工业园区");
    private static final String NATIONAL = "最高人民法院";

    private CourtRegionClassifier() {}

    public static Court classify(String jurisdiction, String court, String caseNo, String holding, String sourceDataset) {
        String name = court == null || court.isBlank() ? null : court.strip();
        Region region = UNKNOWN;
        if ("CN".equals(jurisdiction)) {
            Matcher yearInDocket = CASE_YEAR.matcher(caseNo == null ? "" : caseNo.strip());
            int year = yearInDocket.find() ? Integer.parseInt(yearInDocket.group(1)) : 0;
            if (GUIDING_DATASET.equals(sourceDataset) || "最高人民法院（指导性案例）".equals(name)) {
                name = adjudicatingCourt(caseNo, holding, year);
            }
            if (name != null) {
                region = regionOf(name, year);
                // Imported judgments call the same intermediate court both 杭州市中级人民法院
                // and 浙江省杭州市中级人民法院. Give those spellings one filter entry.
                if (region.city != null && name.equals(region.city + "中级人民法院")) name = region.province + name;
            }
        }
        return new Court(region.province, region.city, region.district, name);
    }

    private static String normalize(String text) {
        return SPACE.matcher(text.replace('（', '(').replace('）', ')')).replaceAll("");
    }

    private static String adjudicatingCourt(String caseNo, String holding, int year) {
        if (caseNo == null || caseNo.isBlank() || holding == null) return null;
        String docket = normalize(caseNo);
        if (docket.isEmpty()) return null;
        String text = normalize(holding);
        Set<String> candidates = new LinkedHashSet<>();
        for (int i = text.indexOf(docket); i >= 0; i = text.indexOf(docket, i + docket.length())) {
            Matcher issuer = ISSUER.matcher(text.substring(0, i));
            if (issuer.find()) {
                String name = issuer.group(1);
                if (nationalCourt(name) || namedRegion(name, year, true) != null) candidates.add(name);
            }
        }
        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    private static boolean nationalCourt(String name) {
        return name.equals(NATIONAL) || name.equals(NATIONAL + "赔偿委员会");
    }

    private static Region regionOf(String name, int year) {
        if (nationalCourt(name)) return new Region("全国", null, null);
        Region region = namedRegion(name, year, false);
        return region == null ? UNKNOWN : region;
    }

    private static Region namedRegion(String name, int year, boolean requireCourtName) {
        Matcher suffix = COURT_SUFFIX.matcher(name);
        if (!suffix.find()) return null;
        String location = name.substring(0, suffix.start());
        // An explicitly qualified county/city wins over a shorter province name.
        // The remainder may designate e.g. an industrial park rather than an administrative county.
        for (int end = location.length(); end > 0; end--) {
            List<TimedRegion> matches = PLACES.get(location.substring(0, end));
            if (matches == null) continue;
            String extra = location.substring(end);
            if (requireCourtName && !extra.isEmpty() && !SPECIAL_AREAS.contains(extra)) return null;
            Region common = null;
            for (TimedRegion match : matches) {
                if (match.applies(year)) common = common == null ? match.region : common.commonParent(match.region);
            }
            if (common != null) return common;
        }
        return null;
    }

    private static Map<String, List<TimedRegion>> loadPlaces() {
        Map<String, List<TimedRegion>> places = new HashMap<>();
        try (var input = Objects.requireNonNull(CourtRegionClassifier.class.getResourceAsStream("/court-regions.tsv"));
             var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\\t", -1);
                String province = fields[0];
                String city = fields[1].isEmpty() ? null : fields[1];
                String district = fields[2].isEmpty() ? null : fields[2];
                TimedRegion region = new TimedRegion(new Region(province, city, district),
                        Integer.parseInt(fields[3]), Integer.parseInt(fields[4]));
                if (district != null) {
                    register(places, district, region);
                    register(places, province + district, region);
                    if (city != null) {
                        register(places, city + district, region);
                        register(places, province + city + district, region);
                    }
                } else {
                    String name = city == null ? province : city;
                    register(places, name, region);
                    if (city != null) register(places, province + city, region);
                    // E.g. 杭州互联网法院, 广州海事法院 and 北京知识产权法院.
                    if (name.endsWith("市")) register(places, name.substring(0, name.length() - 1), region);
                }
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        return Map.copyOf(places);
    }

    private static void register(Map<String, List<TimedRegion>> places, String name, TimedRegion region) {
        places.computeIfAbsent(name, ignored -> new ArrayList<>()).add(region);
    }
}
