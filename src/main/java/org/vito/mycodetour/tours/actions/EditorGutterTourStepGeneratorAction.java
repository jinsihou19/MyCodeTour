package org.vito.mycodetour.tours.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.domain.Tour;
import org.vito.mycodetour.tours.state.StateManager;
import org.vito.mycodetour.tours.state.TourUpdateNotifier;
import org.vito.mycodetour.tours.ui.StepEditor;
import org.vito.mycodetour.tours.ui.TourSelectionDialogWrapper;

import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * 从编辑器行数边栏处的上下文菜单中生成 Step
 * 文件行数的引用形式 file：line
 *
 * @author vito
 * @since 11.0
 * Created on 2025/5/13
 */
public class EditorGutterTourStepGeneratorAction extends AnAction {


    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (isNull(project) || isNull(project.getBasePath())) {
            return;
        }

        final Integer lineObj = e.getData(DataKey.create("EditorGutter.LOGICAL_LINE_AT_CURSOR"));
        final int line = (lineObj != null ? lineObj : 1) + 1;

        final VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (virtualFile == null) {
            return;
        }


        // If no activeTour is present, prompt to select one
        if (StateManager.getInstance().getState(project).getActiveTour().isEmpty()) {
            final TourSelectionDialogWrapper dialog = new TourSelectionDialogWrapper(project,
                    "Please Select the Tour to add the Step to");
            if (dialog.showAndGet()) {
                final Optional<Tour> selected = dialog.getSelected();
                selected.ifPresent(StateManager.getInstance().getState(project)::setActiveTour);
            }
        }

        final Optional<Tour> activeTour = StateManager.getInstance().getState(project).getActiveTour();
        if (activeTour.isPresent()) {
            final Step step = Step.with(virtualFile.getName(), line);

            // Provide a dialog for Step editing
            final StepEditor stepEditor = new StepEditor(project, step);
            final boolean okSelected = stepEditor.showAndGet();
            if (!okSelected) return; // i.e. cancel the step creation

            final Step updatedStep = stepEditor.getUpdatedStep();
            activeTour.get().getSteps().add(updatedStep);
            StateManager.getInstance().getState(project).updateTour(activeTour.get());

            // Notify UI to re-render
            project.getMessageBus().syncPublisher(TourUpdateNotifier.TOPIC).tourUpdated(activeTour.get());
        }
    }
}
