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
        String plain = text.replaceAll("\\s+", " ").trim();
        return plain.length() <= len ? plain : plain.substring(0, len) + "…";
    }
}
