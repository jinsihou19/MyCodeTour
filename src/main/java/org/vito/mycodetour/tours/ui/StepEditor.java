package org.vito.mycodetour.tours.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NotNull;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.service.TinyTemplateEngine;
import org.vito.mycodetour.tours.service.Utils;
import org.vito.mycodetour.tours.state.StateManager;

import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import java.util.Map;

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
    private JBCefBrowser previewBrowser;
    private String stepDoc;
    private String currentMarkdown;

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
        JBCefBrowser editorBrowser = Utils.createNormalJBCefBrowser(project);

        // 创建 JavaScript 查询处理器
        JBCefJSQuery jsQuery = JBCefJSQuery.create((JBCefBrowserBase) editorBrowser);
        jsQuery.addHandler((query) -> {
            currentMarkdown = query;
            updatePreviewComponent();
            return null;
        });

        try {
            String rendered = TinyTemplateEngine.render(
                    "/public/editor/index.html",
                    Map.of("editor", jsQuery.inject("easyMDE.value()"),
                            "markdown", escapeJavaScript(currentMarkdown)));
            editorBrowser.loadHTML(rendered);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


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

        previewBrowser = Utils.createNormalJBCefBrowser(project);

        updatePreviewContent();

        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.emptyTop(5));
        panel.add(previewBrowser.getComponent());

        return panel;
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