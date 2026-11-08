package com.lexaid.common;

/**
 * 文本工具：摘要截取
 */
public final class TextUtil {

    private TextUtil() {
    }

    /**
     * 截取前 len 个字符作为摘要，超长加省略号
     */
    public static String excerpt(String text, int len) {
        if (text == null) {
            return "";
        }
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) <= ' ') start++;
        while (end > start && text.charAt(end - 1) <= ' ') end--;

        // Build only the displayed prefix, even when the source is a full paper or judgment.
        StringBuilder snippet = new StringBuilder(Math.min(len, end - start));
        while (start < end) {
            if (snippet.length() == len) return snippet.append('…').toString();
            char c = text.charAt(start++);
            if (isSpace(c)) {
                c = ' ';
                while (start < end && isSpace(text.charAt(start))) start++;
            }
            snippet.append(c);
        }
        return snippet.toString();
    }

    private static boolean isSpace(char c) {
        return c == ' ' || (c >= '\t' && c <= '\r');
    }
}
