package org.vito.mycodetour.tours.service;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiNameHelper;
import org.apache.commons.lang3.StringUtils;
import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor;
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor;
import org.intellij.markdown.html.HtmlGenerator;
import org.intellij.markdown.parser.MarkdownParser;
import org.jetbrains.annotations.NotNull;
import org.vito.mycodetour.tours.domain.Props;
import org.vito.mycodetour.tours.domain.Step;

import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 一些暂时的工具
 *
 * @author vito
 * Created on 2025/1/1
 */
public class Utils {

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
            sb.append("<hr/>");
            sb.append(DocumentationMarkup.DEFINITION_START);
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

        // 预处理 PlantUML 代码块
        if (processedMarkdown.contains("@startuml")) {
            processedMarkdown = processedMarkdown.replaceAll(
                    "@startuml\\s*([\\s\\S]*?)@enduml",
                    "<div class='plantuml'>$1</div>"
            );
        }
        // 预处理Mermaid代码块
        if (processedMarkdown.contains("```mermaid")) {
            processedMarkdown = processedMarkdown.replaceAll(
                    "```mermaid\\s*([\\s\\S]*?)```",
                    "<div class='mermaid'>$1</div>"
            );
        }

        final MarkdownFlavourDescriptor flavour = new GFMFlavourDescriptor();
        final ASTNode parsedTree = new MarkdownParser(flavour).buildMarkdownTreeFromString(processedMarkdown);
        String html = new HtmlGenerator(processedMarkdown, parsedTree, flavour, false).generateHtml(TAG_RENDERER);

        // 包裹 markdown-body
        html = "<article class=\"markdown-body\">" + html + "</article>";

        // 引入暗黑CSS
        String darkCss = """
                    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/github-markdown-css@5.5.1/github-markdown-dark.min.css">
                    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css">
                    <style>
                      body, .markdown-body {
                        background: #23272e !important;
                        color: #e6e6e6 !important;
                      }
                      .mermaid, .mermaid svg, .plantuml, .plantuml svg {
                        background: transparent !important;
                        color: #e6e6e6 !important;
                      }
                      /* 滚动条美化 */
                      .markdown-body ::-webkit-scrollbar {
                        width: 12px;
                        background: #23272e;
                      }
                      .markdown-body ::-webkit-scrollbar-thumb {
                        background: #444950;
                        border-radius: 6px;
                        border: 2px solid #23272e;
                      }
                      .markdown-body ::-webkit-scrollbar-thumb:hover {
                        background: #5c6370;
                      }
                      .markdown-body ::-webkit-scrollbar-track {
                        background: #23272e;
                      }
                      ::-webkit-scrollbar {
                          width: 14.0px;
                          height: 14.0px;
                      }
                
                      ::-webkit-scrollbar-track {
                          background-color:
                                  rgba(128, 128, 128, 0.0);
                      }
                
                      ::-webkit-scrollbar-track:hover {
                          background-color:rgba(128, 128, 128, 0.0);
                      }
                
                      ::-webkit-scrollbar-thumb {
                          background-color:
                                  rgba(255, 255, 255, 0.14901960784313725);
                          border-radius:14.0px;
                          border-width: 3.0px;
                          border-style: solid;
                          border-color: rgba(128, 128, 128, 0.0);
                          background-clip: padding-box;
                          outline: 1px solid rgba(38, 38, 38, 0.34901960784313724);
                          outline-offset: -3.0px;
                      }
                
                      ::-webkit-scrollbar-thumb:hover {
                          background-color:rgba(255, 255, 255, 0.30196078431372547);
                          border-radius:14.0px;
                          border-width: 3.0px;
                          border-style: solid;
                          border-color: rgba(128, 128, 128, 0.0);
                          background-clip: padding-box;
                          outline: 1px solid rgba(38, 38, 38, 0.5490196078431373);
                          outline-offset: -3.0px;
                      }
                
                      ::-webkit-scrollbar-button {
                          display: none;
                      }
                
                      ::-webkit-scrollbar-corner {
                          background-color: rgba(63, 68, 66, 1.0);
                      }
                    </style>
                """;

        // 添加必要的脚本
        StringBuilder scripts = new StringBuilder();

        // 添加 highlight.js
        scripts.append("""
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/java.min.js"></script>
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/javascript.min.js"></script>
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/python.min.js"></script>
                    <script>
                        document.addEventListener('DOMContentLoaded', function() {
                            hljs.configure({
                                languages: ['java', 'javascript', 'python', 'xml', 'html', 'css', 'json', 'bash', 'shell']
                            });
                            document.querySelectorAll('pre code').forEach((block) => {
                                hljs.highlightElement(block);
                            });
                        });
                    </script>
                """);

        if (html.contains("class='mermaid'")) {
            scripts.append("""
                        <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
                        <script>
                            document.addEventListener('DOMContentLoaded', function() {
                                if (typeof mermaid !== 'undefined') {
                                    mermaid.initialize({
                                        startOnLoad: true,
                                        theme: 'dark',
                                    });
                                    mermaid.init(undefined, document.querySelectorAll('.mermaid'));
                                }
                            });
                        </script>
                    """);
        }

        if (html.contains("class='plantuml'")) {
            scripts.append("""
                        <script src="https://cdn.jsdelivr.net/npm/plantuml-encoder@1.4.0/dist/plantuml-encoder.min.js"></script>
                        <script>
                            document.addEventListener('DOMContentLoaded', function() {
                                const plantumlElements = document.querySelectorAll('.plantuml');
                                plantumlElements.forEach(function(element) {
                                    const encoded = plantumlEncoder.encode(element.textContent);
                                    const img = document.createElement('img');
                                    img.src = 'https://www.plantuml.com/plantuml/dsvg/' + encoded;
                                    img.style.maxWidth = '100%';
                                    element.innerHTML = '';
                                    element.appendChild(img);
                                });
                            });
                        </script>
                    """);
        }

        html = darkCss + scripts + html;

        return html;
    }

    public static boolean isFileMatchesStep(VirtualFile file, @NotNull Step step) {
        if (file.isDirectory())
            return false;

        final String stepDirectory = step.getDirectory() != null ? step.getDirectory() : "";
        final String stepFilePath = Paths.get(stepDirectory, step.getFile()).toString();
        final String filePath = Paths.get(file.getPath()).toString();

        return filePath.endsWith(stepFilePath);
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


}