package org.vito.mycodetour.tours.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory class to generate the related Tool Pane Window
 *
 * @author vito
 * Created on 2025/1/1
 */
public class ToolPaneWindowFactory implements ToolWindowFactory {


    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        final ToolPaneWindow toursNavigationWindow = new ToolPaneWindow(project, toolWindow);
        final ContentFactory contentFactory = ContentFactory.getInstance();
        final Content content = contentFactory.createContent(toursNavigationWindow.getContent(), null, false);
        toolWindow.getContentManager().addContent(content);
    }
}