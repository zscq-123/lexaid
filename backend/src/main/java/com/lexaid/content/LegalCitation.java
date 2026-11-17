package com.lexaid.content;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Recognizes one explicit Chinese statute/article citation; never guesses a statute from a topic. */
public record LegalCitation(String law, String article, String numericArticle, Integer versionYear) {
    public LegalCitation(String law, String article, String numericArticle) { this(law,article,numericArticle,null); }
    private static final String NUMBER = "[0-9零〇一二两三四五六七八九十百千]{1,12}";
    private static final Pattern ARTICLE = Pattern.compile("第\\s*(" + NUMBER + ")\\s*条(?:\\s*之\\s*(" + NUMBER + "))?");
    private static final Pattern LAW = Pattern.compile("[\\p{IsHan}]{1,60}(?:法典|法|条例|办法)");
    private static final Pattern YEAR = Pattern.compile("((?:19|20)[0-9]{2})年?\\s*(?:修正|修订|版)");
    // Only remove request words at the boundary; never remove words inside a statute title.
    private static final Pattern REQUEST_PREFIX = Pattern.compile("^(?:(?:帮我看看|解释一下|解读一下|查一下|找一下|请问|能不能|能否|帮我|告诉我|给我|查询|查找|搜索|找找|看看|列出|解释|解读|关于|有关|根据|现行|最新|我国|请)\\s*)+");
    private static final Pattern LAW_JOIN = Pattern.compile("(法典|法|条例|办法)\\s*(?:和|与|及|、|或)\\s*");

    public static LegalCitation parse(String text) {
        return fromTerms(literalTerms(text));
    }

    /** Keep every named law and distinct article ahead of numeral aliases when a query is shortened. */
    public static List<String> literalTerms(String text) {
        if (text == null) return List.of();
        var terms = new java.util.ArrayList<String>();
        var quoted = Pattern.compile("《([^》]+)》").matcher(text);
        while (quoted.find()) terms.add(titleWithoutVersion(quoted.group(1)));
        var articles = ARTICLE.matcher(text);
        int previousEnd = 0;
        while (articles.find()) {
            terms.add(articles.group());
            String leading = titleWithoutVersion(text.substring(previousEnd,articles.start()))
                    .replaceFirst("^[\\s，,；;、]*(?:和|与|及|或)?\\s*", "");
            leading = REQUEST_PREFIX.matcher(leading).replaceFirst("")
                    .replaceFirst("(?:的)?\\s*$", "");
            for (String name : LAW_JOIN.matcher(leading).replaceAll("$1\n").split("\n")) {
                if (LAW.matcher(name).matches()) terms.add(name);
            }
            previousEnd = articles.end();
        }
        var version = YEAR.matcher(text);
        while (version.find()) terms.add(version.group());
        var ordered = new LinkedHashSet<String>();
        terms.stream().filter(t -> LAW.matcher(t).matches()).forEach(ordered::add);
        var numeric = new LinkedHashSet<String>();
        for (String term : terms) {
            var m = ARTICLE.matcher(term);
            while (m.find()) {
                int n = number(m.group(1));
                int extra = m.group(2) == null ? 0 : number(m.group(2));
                if (n <= 0 || n > 9999 || extra < 0) continue;
                ordered.add("第" + chinese(n) + "条" + (extra == 0 ? "" : "之" + chinese(extra)));
                numeric.add("第" + n + "条" + (extra == 0 ? "" : "之" + extra));
            }
        }
        ordered.addAll(numeric);
        terms.stream().filter(t -> YEAR.matcher(t).matches()).forEach(ordered::add);
        return List.copyOf(ordered);
    }

    public static boolean hasMultipleCitations(String text) {
        var terms = literalTerms(text);
        return fromTerms(terms) == null && terms.stream().anyMatch(t -> LAW.matcher(t).matches())
                && terms.stream().anyMatch(t -> ARTICLE.matcher(t).matches());
    }

    private static String titleWithoutVersion(String text) {
        // A year may precede/follow the title or be enclosed in the title's parentheses.
        return YEAR.matcher(text).replaceAll("").replaceAll("[（(]\\s*[）)]", "").strip();
    }

    public static LegalCitation fromTerms(List<String> terms) {
        var laws = new LinkedHashSet<String>();
        var articles = new LinkedHashSet<String>();
        String numeric = null;
        var years = new LinkedHashSet<Integer>();
        for (String term : terms) {
            var year = YEAR.matcher(term);
            while (year.find()) years.add(Integer.valueOf(year.group(1)));
            if (LAW.matcher(term).matches()) laws.add(term.replaceFirst("^中华人民共和国", ""));
            var m = ARTICLE.matcher(term);
            while (m.find()) {
                int n = number(m.group(1));
                int extra = m.group(2) == null ? 0 : number(m.group(2));
                if (n <= 0 || n > 9999 || extra < 0) continue;
                articles.add("第" + chinese(n) + "条" + (extra == 0 ? "" : "之" + chinese(extra)));
                numeric = "第" + n + "条" + (extra == 0 ? "" : "之" + extra);
            }
        }
        return laws.size() == 1 && articles.size() == 1
                ? new LegalCitation(laws.iterator().next(), articles.iterator().next(), numeric,
                    years.size() == 1 ? years.iterator().next() : null) : null;
    }

    public List<String> lawNames() { return List.of(law, "中华人民共和国" + law); }
    public List<String> articleNames() { return List.of(article, numericArticle); }

    private static int number(String value) {
        if (value.matches("[0-9]+")) {
            try { return Integer.parseInt(value); } catch (NumberFormatException e) { return -1; }
        }
        int total = 0, digit = 0;
        for (char c : value.replace('〇','零').replace('两','二').toCharArray()) {
            int unit = switch (c) { case '十' -> 10; case '百' -> 100; case '千' -> 1000; default -> 0; };
            if (unit != 0) { total += (digit == 0 ? 1 : digit) * unit; digit = 0; }
            else digit = "零一二三四五六七八九".indexOf(c);
        }
        return total + digit;
    }

    private static String chinese(int number) {
        StringBuilder out = new StringBuilder();
        boolean gap = false;
        for (int divisor = 1000, i = 0; divisor > 0; divisor /= 10, i++) {
            int digit = number / divisor % 10;
            if (digit == 0) { gap |= !out.isEmpty(); continue; }
            if (gap) out.append('零');
            if (!(out.isEmpty() && divisor == 10 && digit == 1)) out.append("零一二三四五六七八九".charAt(digit));
            if (divisor > 1) out.append("千百十".charAt(i));
            gap = false;
        }
        return out.toString();
    }
}
