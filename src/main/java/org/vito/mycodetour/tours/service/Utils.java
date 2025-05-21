package org.vito.mycodetour.tours.service;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiNameHelper;
import org.apache.commons.lang3.StringUtils;
import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor;
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor;
import org.intellij.markdown.html.HtmlGenerator;
import org.intellij.markdown.parser.MarkdownParser;
import org.jetbrains.annotations.NotNull;
import org.vito.mycodetour.tours.domain.Props;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 一些暂时的工具
 *
 * @author vito
 * Created on 2025/1/1
 */
public class Utils {

    private static final Logger LOG = Logger.getInstance(Utils.class);

    public static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^]]+)]]");

    /**
     * Custom TagRenderer for md to html, as for some strange reason there is no default implementation now
     * in the related Jetbrains library
     */
    private static final HtmlGenerator.TagRenderer TAG_RENDERER = new HtmlGenerator.TagRenderer() {
        @NotNull
        @Override
        public CharSequence printHtml(@NotNull CharSequence charSequence) {
            return charSequence;
        }

        @NotNull
        @Override
        public CharSequence openTag(@NotNull ASTNode astNode, @NotNull CharSequence tagName,
                                    @NotNull CharSequence[] attributes, boolean autoClose) {
            StringBuilder builder = new StringBuilder();
            builder.append("<").append(tagName);
            for (CharSequence attribute : attributes) {
                if (attribute == null || attribute.isEmpty()) {
                    continue;
                }
                builder.append(" ").append(attribute);
            }
            if (autoClose) {
                builder.append(" />");
            } else {
                builder.append(">");
            }

            return builder.toString();
        }

        @NotNull
        @Override
        public CharSequence closeTag(@NotNull CharSequence charSequence) {
            return String.format("</%s>", charSequence);
        }
    };

    /**
     * Removes whitespaces and transforms the given title in camelCase
     * e.g. Basket Items Issue Reproduce --> basketItemsIssueReproduce.tour
     *
     * @param title The given title
     * @return The suggested filename for this title, in camelCase without whitespaces
     */
    public static String fileNameFromTitle(String title) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < title.length(); i++) {
            if (StringUtils.isWhitespace(title.charAt(i) + "")) {
                if (i < title.length() - 1 && !StringUtils.isWhitespace(title.charAt(i + 1) + "")) {
                    sb.append(StringUtils.capitalize(title.charAt(i + 1) + ""));
                    i++; // skip the next
                }
            } else
                sb.append(title.charAt(i));
        }

        return StringUtils.uncapitalize(sb.append(Props.TOUR_EXTENSION_FULL).toString());
    }

    public static boolean equalStr(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        return getOrDef(s1, "").equals(getOrDef(s2, ""));
    }

    public static boolean equalInt(Integer i1, Integer i2) {
        if (i1 == null && i2 == null) return true;
        return getOrDef(i1, Integer.MIN_VALUE).equals(getOrDef(i2, Integer.MIN_VALUE));
    }

    public static String renderFullDoc(String title, String description, String file) {
        StringBuilder sb = new StringBuilder();
        sb.append(DocumentationMarkup.DEFINITION_START);
        sb.append(title);
        sb.append(DocumentationMarkup.DEFINITION_END);
        sb.append(DocumentationMarkup.CONTENT_START);
        if (description != null)
            // For formatting purposes, add <br/> tag when there are 2 consecutive empty lines
            description = description.replaceAll("\\n\\n\\n", "\n\n<br/>\n\n");
        sb.append("\n\n").append(description).append("\n");
        sb.append(DocumentationMarkup.CONTENT_END);
        pageFooterIfNeed(file, sb);
        return mdToHtml(sb.toString());
    }

    private static void pageFooterIfNeed(String file, StringBuilder sb) {
        if (StringUtils.isNotEmpty(file)) {
            sb.append("<div class='definition footer'><pre>");
            sb.append("Reference: ");
            sb.append(createLink(file));
            sb.append(DocumentationMarkup.DEFINITION_END);
        }
    }

    public static String mdToHtml(String markdown) {
        String processedMarkdown = markdown;
        // 预处理 ![[]] 语法
        if (processedMarkdown.contains("![[")) {
            processedMarkdown = processedMarkdown.replaceAll(
                    "!\\[\\[([^]]+)]]",
                    "<img src=\"file:///$1\" alt=\"$1\">"
            );
        }
        // 预处理 [[xx]] 语法
        if (processedMarkdown.contains("[[")) {
            processedMarkdown = WIKI_LINK.matcher(processedMarkdown).replaceAll(matchResult -> {
                String group = matchResult.group(1);
                if (group.contains(".tour")) {
                    return group.replaceAll("([^#:]+)\\.tour#([^]]+)", "<a href=\"tour://$1.tour#$2\">$1.tour#$2</a>");
                }
                return "<a href=\"navigate://$1\">$1</a>";
            });
        }

        // 预处理 PlantUML 代码块，先把@startuml转为代码块
        if (processedMarkdown.contains("@startuml")) {
            processedMarkdown = processedMarkdown.replaceAll(
                    "@startuml\\s*([\\s\\S]*?)@enduml",
                    "```startuml\n$1```"
            );
        }

        final MarkdownFlavourDescriptor flavour = new GFMFlavourDescriptor();
        final ASTNode parsedTree = new MarkdownParser(flavour).buildMarkdownTreeFromString(processedMarkdown);
        String html = new HtmlGenerator(processedMarkdown, parsedTree, flavour, false).generateHtml(TAG_RENDERER);

        // 预处理 PlantUML 代码块
        if (html.contains("class=\"language-startuml\"")) {
            html = html.replaceAll(
                    "<pre><code class=\"language-startuml\">\\s*([\\s\\S]*?)</code></pre>",
                    "<div class='plantuml'>$1</div>"
            );
        }

        // 预处理Mermaid代码块
        if (html.contains("class=\"language-mermaid\"")) {
            html = html.replaceAll(
                    "<pre><code class=\"language-mermaid\">\\s*([\\s\\S]*?)</code></pre>",
                    "<div class='mermaid'>$1</div>"
            );
        }

        return TinyTemplateEngine.render("/public/index.html",
                Map.of("markdownHtml", html));
    }

    private static String createLink(String value) {
        return String.format("<a href='navigate://%s'>%s</a>", value, shortName(value));
    }

    private static String shortName(String value) {
        String displayValue = value;
        if (value.contains(":")) {
            displayValue = value.replace(".java", "");
        }
        return PsiNameHelper.getShortClassName(displayValue);
    }

    /**
     * normalize (remove nulls)
     */
    private static <T> T getOrDef(T s, T def) {
        return s != null ? s : def;
    }

    /**
     * 读取文件
     *
     * @param filePath 文件路径
     * @return 内容
     */
    public static String readFile(String filePath) {
        try (InputStream is = Utils.class.getResourceAsStream(filePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (IOException e) {
            LOG.error(e);
        }
        return "";
    }
}