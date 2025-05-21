package org.vito.mycodetour.tours.service;


import java.util.Map;
import java.util.regex.*;

/**
 * 微型模板引擎
 *
 * @author vito
 * @since 11.0
 * Created on 2025/5/21
 */
public class TinyTemplateEngine {

    // 正则匹配 ${...} 格式的占位符（非贪婪模式）
    private static final Pattern PATTERN = Pattern.compile("\\$\\{(.*?)\\}");

    /**
     * 模板渲染方法
     *
     * @param template 模板字符串，例如 "Hello, ${name}!"
     * @param values   键值对，例如 Map.of("name", "Alice")
     * @return 替换后的字符串，例如 "Hello, Alice!"
     */
    public static String renderHtml(String template, Map<String, String> values) {
        Matcher matcher = PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1); // 提取 ${} 内部的变量名
            // 从 Map 中获取值，若不存在则替换为空字符串
            String replacement = values.getOrDefault(key, "");
            // 处理正则特殊字符（如 $、\ 等）
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 模板渲染方法
     *
     * @param template 模板路径
     * @param values   键值对，例如 Map.of("name", "Alice")
     * @return 替换后的字符串，例如 "Hello, Alice!"
     */
    public static String render(String template, Map<String, String> values) {
        String html = Utils.readFile(template);
        return TinyTemplateEngine.renderHtml(html, values);
    }

}
