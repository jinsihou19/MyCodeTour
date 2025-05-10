package org.vito.mycodetour.tours.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
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
 * Action that generates a Step for the selected file:line from Gutter's Editor context menu
 *
 * @author vito
 */
public class TourStepGeneratorAction extends AnAction {

   private static final Logger LOG = Logger.getInstance(TourStepGeneratorAction.class);

   @Override
   public void actionPerformed(@NotNull AnActionEvent e) {
      final Project project = e.getProject();
      if (isNull(project) || isNull(project.getBasePath()))
         return;

      final Object lineObj = e.getDataContext().getData("EditorGutter.LOGICAL_LINE_AT_CURSOR");
      final int line = (lineObj != null ? Integer.parseInt(lineObj.toString()) : 1) + 1;

      final VirtualFile virtualFile = e.getDataContext().getData(CommonDataKeys.VIRTUAL_FILE);
      if (virtualFile == null)
         return;


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
         final Step step = generateStep(virtualFile, line);

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

   private Step generateStep(VirtualFile virtualFile, int line) {
      final String title = String.format("%s:%s", virtualFile.getName(), line);
      LOG.info("Generating Step: " + title);
      return Step.builder()
            .title(title)
            .description("Simple Navigation to " + title)
            .file(virtualFile.getName())
            .line(line)
            .build();
   }

}