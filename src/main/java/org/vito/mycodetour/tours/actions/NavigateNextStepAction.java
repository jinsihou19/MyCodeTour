package org.vito.mycodetour.tours.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.vito.mycodetour.tours.state.StateManager;
import org.vito.mycodetour.tours.state.StepSelectionNotifier;
import org.vito.mycodetour.tours.ui.ToolPaneWindow;

/**
 * Triggers Navigation to the next Step.
 * Navigation is handled through {@link ToolPaneWindow}
 *
 * @author vito
 */
public class NavigateNextStepAction extends AnAction {
   @Override
   public void actionPerformed(@NotNull AnActionEvent e) {
      final Project project = e.getProject();
      if (project == null) return;

      StateManager.getInstance().getState(project).getNextStep().ifPresent(step -> {
         // Notify UI to select the step which will trigger its navigation
         project.getMessageBus().syncPublisher(StepSelectionNotifier.TOPIC).selectStep(step);
      });
   }
}