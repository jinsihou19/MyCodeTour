package org.vito.mycodetour.tours.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.service.ResourceHandler;
import org.vito.mycodetour.tours.state.StateManager;

import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.vito.mycodetour.tours.service.Utils.equalInt;
import static org.vito.mycodetour.tours.service.Utils.equalStr;
import static org.vito.mycodetour.tours.service.Utils.renderFullDoc;

/**
 * Editor (as dialog) for Step editing. Supports preview
 *
 * @author vito
 * Created on 2025/1/1
 */
public class StepEditor extends DialogWrapper {

    private JTabbedPane pane;
    private final Project project;
    private final Step step;

    private JBTextField titleTextField;
    private JBTextField referenceTextField;
    private JBCefBrowser editorBrowser;
    private JBCefBrowser previewBrowser;
    private String stepDoc;
    private String currentMarkdown;
    private JBCefJSQuery jsQuery;

    public StepEditor(Project project, Step step) {
        super(project);
        this.project = project;
        this.step = step;
        this.currentMarkdown = step.getDescription();
        init();
        setTitle("Step Editor");
        getRootPane().setDefaultButton(null);
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected @NotNull JComponent createCenterPanel() {
        pane = new JBTabbedPane(SwingConstants.TOP);
        pane.addTab("Step Info", createEditorPanel());
        pane.addTab("Preview", createPreviewPanel());
        pane.addChangeListener(e -> {
            if (pane.getSelectedIndex() == 1)
                updatePreviewComponent();
        });
        return JBUI.Panels.simplePanel(pane);
    }

    private JComponent createEditorPanel() {
        // 创建编辑器浏览器
        editorBrowser = JBCefBrowser.createBuilder()
                .setUrl("about:blank")
                .build();

        editorBrowser.getJBCefClient().addRequestHandler(new CefRequestHandlerAdapter() {

            @Override
            public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String target_url, boolean user_gesture) {
                return true;
            }

            @Override
            public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request, boolean isNavigation, boolean isDownload, String requestInitiator, BoolRef disableDefaultHandling) {
                return new CefResourceRequestHandlerAdapter() {
                    @Override
                    public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
                        String url = request.getURL();
                        if (url.startsWith("file:///") && !isIndex(url)) {
                            return new ResourceHandler(project);
                        }
                        // 放行非必要处理请求
                        return null;
                    }
                };
            }
        }, editorBrowser.getCefBrowser());

        // 创建 JavaScript 查询处理器
        jsQuery = JBCefJSQuery.create((JBCefBrowserBase) editorBrowser);
        jsQuery.addHandler((query) -> {
            currentMarkdown = query;
            updatePreviewComponent();
            return null;
        });

        boolean isDark = !JBColor.isBright() || UIUtil.isUnderIntelliJLaF();
        String bgColor = isDark ? "#2B2B2B" : "#FFFFFF";
        String textColor = isDark ? "#A9B7C6" : "#000000";
        String toolbarBg = isDark ? "#3C3F41" : "#F5F5F5";
        String borderColor = isDark ? "#515151" : "#E0E0E0";

        // 初始化编辑器
        String editorHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset='UTF-8'>
                <link rel='stylesheet' href='file:///mycodetour/public/easymde.min.css'>
                <link rel="stylesheet" href="file:///mycodetour/public/github-markdown-dark.min.css">
                <link rel="stylesheet" href="file:///mycodetour/public/github-dark.min.css">
                <script src='file:///mycodetour/public/easymde.min.js'></script>
                <script src='file:///mycodetour/public/marked.min.js'></script>
                <script src='file:///mycodetour/public/mermaid.min.js'></script>
                """+
                "<style>" +
                "body { margin: 0; padding: 0; background-color: " + bgColor + "; }" +
                ".EasyMDEContainer { background-color: " + bgColor + "; }" +
                ".editor-toolbar { border-width: 0 !important; background-color: " + toolbarBg + " !important;}" +
                ".editor-toolbar button { color: " + textColor + " !important; }" +
                ".editor-toolbar button:hover { background-color: #3C3F41 !important; }" +
                ".editor-toolbar button.active { background-color: #3C3F41 !important; }" +
                ".CodeMirror { background-color: " + bgColor + " !important; color: " + textColor + " !important; border-width: 0 !important;}" +
                ".CodeMirror-gutters { background-color: #313335 !important; border-color: " + borderColor + " !important; }" +
                ".CodeMirror-linenumber { color: #606366 !important; }" +
                ".CodeMirror-cursor { border-left: 1px solid " + textColor + " !important; }" +
                ".CodeMirror-selected { background-color: #3A3D41 !important; }" +
                ".CodeMirror-focused .CodeMirror-selected { background-color: #3A3D41 !important; }" +
                ".editor-preview, .editor-preview-side { background-color: " + bgColor + " !important; color: " + textColor + " !important; }" +
                ".markdown-body { background-color: " + bgColor + " !important; }" +
                """
                        ::-webkit-scrollbar {
                            width: 14.0px;
                            height: 14.0px;
                            background-color: rgba(63, 68, 66, 1.0);
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
                            display:
                                    none;
                        }
                        
                        ::-webkit-scrollbar-corner {
                            background-color: rgba(63, 68, 66, 1.0);
                        }
                        """ +
                "</style>" +
                "</head>" +
                "<body>" +
                "<textarea id='editor'></textarea>" +
                "<script>" +
                "mermaid.initialize({ " +
                "  startOnLoad: true, " +
                "  theme: 'dark', " +
                "});" +
                "var initialValue = '" + escapeJavaScript(currentMarkdown) + "';" +
                """
                            var easyMDE = new EasyMDE({
                                element: document.getElementById('editor'),
                                initialValue: initialValue,
                                autofocus: true,
                                spellChecker: false,
                                status: false,
                                insertTexts: {
                                    link: ["[", "](navigate://)"],
                                },
                                toolbar: ['bold', 'italic', 'heading', '|', 'quote', 'unordered-list', 'ordered-list', 'clean-block', 'table', 'code', '|', 'link', 'image', '|', 'undo', 'redo', 'fullscreen'],
                                previewRender: function (plainText) {
                                    var preview = document.createElement('div');
                                    preview.className = 'markdown-body';
                                    preview.innerHTML = marked.parse(plainText);
                                    mermaid.init(undefined, preview.querySelectorAll('language-mermaid'));
                                    return preview.innerHTML;
                                },
                                renderingConfig: {
                                    codeSyntaxHighlighting: true,
                                }
                            });
                        """ +
                "easyMDE.codemirror.on('change', function() {" +
                "  " + jsQuery.inject("easyMDE.value()") + ";" +
                "});" +
                "</script>" +
                "</body>" +
                "</html>";

        String encodedHtml = Base64.getEncoder().encodeToString(editorHtml.getBytes(StandardCharsets.UTF_8));
        editorBrowser.loadHTML(editorHtml);

        final JBScrollPane editorPane = new JBScrollPane(editorBrowser.getComponent());

        titleTextField = new JBTextField(step.getTitle());
        referenceTextField = new JBTextField(step.reference());

        final JPanel textFieldsGridPanel = UI.PanelFactory.grid()
                .add(UI.PanelFactory.panel(titleTextField)
                        .withLabel("&Title:")
                        .withComment("Step title"))
                .add(UI.PanelFactory.panel(referenceTextField)
                        .withLabel("&Navigation reference:")
                        .withComment("Code location where this step will Navigate to on click (optional)"))
                .createPanel();

        final JPanel textAreaPanel = UI.PanelFactory.panel(editorPane)
                .withLabel("Step description:")
                .anchorLabelOn(UI.Anchor.Top)
                .resizeX(true)
                .resizeY(true)
                .createPanel();

        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.emptyTop(5));
        panel.add(textFieldsGridPanel);
        panel.add(textAreaPanel);

        return panel;
    }

    private String escapeJavaScript(String str) {
        return str.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private JComponent createPreviewPanel() {
        stepDoc = renderFullDoc(
                StateManager.getInstance().getState(project).getStepMetaLabel(titleTextField.getText()),
                currentMarkdown,
                referenceTextField.getText());

        previewBrowser = JBCefBrowser.createBuilder()
                .setUrl("about:blank")
                .build();

        previewBrowser.getJBCefClient().addRequestHandler(new CefRequestHandlerAdapter() {

            @Override
            public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String target_url, boolean user_gesture) {
                return true;
            }

            @Override
            public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request, boolean isNavigation, boolean isDownload, String requestInitiator, BoolRef disableDefaultHandling) {
                return new CefResourceRequestHandlerAdapter() {
                    @Override
                    public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
                        String url = request.getURL();
                        if (url.startsWith("file:///") && !isIndex(url)) {
                            return new ResourceHandler(project);
                        }
                        // 放行非必要处理请求
                        return null;
                    }
                };
            }
        }, previewBrowser.getCefBrowser());

        updatePreviewContent();

        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.emptyTop(5));
        panel.add(previewBrowser.getComponent());

        return panel;
    }

    private boolean isIndex(String url) {
        return url.startsWith("file:///jbcefbrowser/") && url.endsWith("url=about:blank");
    }

    private void updatePreviewComponent() {
        stepDoc = renderFullDoc(
                StateManager.getInstance().getState(project).getStepMetaLabel(titleTextField.getText()),
                currentMarkdown,
                referenceTextField.getText());
        updatePreviewContent();
    }

    private void updatePreviewContent() {
        previewBrowser.loadHTML(stepDoc);
    }

    public Step getUpdatedStep() {
        final String[] reference = referenceTextField.getText().trim().split(":");

        step.setTitle(titleTextField.getText().trim());
        step.setDescription(currentMarkdown.trim());

        // optional file:line
        final String file = reference[0] != null && !reference[0].isEmpty() ? reference[0] : null;
        final Integer line = reference.length > 1 && reference[1] != null && !reference[1].isEmpty()
                ? Integer.parseInt(reference[1])
                : null;

        step.setFile(file);
        step.setLine(line);

        return step;
    }

    public boolean isDirty() {
        final String[] reference = referenceTextField.getText().trim().split(":");
        return !equalStr(step.getTitle(), titleTextField.getText())
                || !equalStr(step.getDescription(), currentMarkdown)
                || !equalStr(step.getFile(), reference[0])
                || !equalInt(step.getLine(), reference.length > 1 ? Integer.parseInt(reference[1]) : null);
    }
}