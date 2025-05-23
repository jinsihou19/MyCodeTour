package org.vito.mycodetour.tours.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.service.Utils;
import org.vito.mycodetour.tours.state.StateManager;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Map;

public class StepEditorPane extends JPanel {
    private final Project project;
    private final Step step;
    private String currentMarkdown;
    private final Runnable onBack;

    public StepEditorPane(Project project, Step step, Runnable onBack) {
        super(new BorderLayout());
        this.project = project;
        this.step = step;
        this.currentMarkdown = step.getDescription();
        this.onBack = onBack;
        init();
    }

    private void init() {
        JBCefBrowser editorBrowser = new JBCefBrowser();

        // 创建 JavaScript 查询处理器
        JBCefJSQuery jsQuery = JBCefJSQuery.create((JBCefBrowserBase) editorBrowser);
        jsQuery.addHandler((query) -> {
            currentMarkdown = query;
            return null;
        });

        try {
            Utils.addRequestHandler(
                    editorBrowser,
                    project,
                    Map.of("editor", jsQuery.inject("easyMDE.value()"),
                            "markdown", Utils.escapeJavaScript(currentMarkdown)));

            editorBrowser.loadURL("file:///mycodetour/public/editor/index.html");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 移除滚动面板，直接添加编辑器组件
        add(editorBrowser.getComponent(), BorderLayout.CENTER);

        // 创建工具栏面板
        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // 创建保存按钮
        JButton saveButton = new JButton(AllIcons.Actions.MenuSaveall);
        saveButton.setToolTipText("Save the current step");
        saveButton.addActionListener(e -> {
            saveChanges();
            onBack.run();
        });

        // 创建返回按钮
        JButton backButton = new JButton(AllIcons.Actions.Back);
        backButton.setToolTipText("Back the view mode");
        backButton.addActionListener(e -> onBack.run());

        // 添加按钮到工具栏
        toolbarPanel.add(saveButton);
        toolbarPanel.add(backButton);

        // 添加工具栏到底部
        add(toolbarPanel, BorderLayout.SOUTH);
    }

    private void saveChanges() {
        step.setDescription(currentMarkdown.trim());
        StateManager.getInstance().getState(project).updateTour(step.getOwner());
    }
}
