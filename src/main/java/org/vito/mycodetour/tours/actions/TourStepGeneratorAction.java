package org.vito.mycodetour.tours.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.domain.Tour;
import org.vito.mycodetour.tours.service.PsiHelper;
import org.vito.mycodetour.tours.state.StateManager;
import org.vito.mycodetour.tours.state.TourUpdateNotifier;
import org.vito.mycodetour.tours.ui.StepEditor;
import org.vito.mycodetour.tours.ui.TourSelectionDialogWrapper;

import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * 从编辑器中上下文菜单选择PSI元素生成 Step
 *
 * @author vito
 * @since 11.0
 * Created on 2025/5/10
 */
public class TourStepGeneratorAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (isNull(project) || isNull(project.getBasePath()))
            return;


        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile == null) {
            return;
        }

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);
        PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiClass.class, PsiField.class, PsiMethod.class);

        Step step;
        if (element instanceof PsiIdentifier
                && element.getContext() != null
                && element.getContext().equals(parent)) {
            step = Step.with(PsiHelper.getReference(parent));
        } else {
            LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
            final int line = logicalPosition.line + 1;

            final VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
            if (virtualFile == null) {
                return;
            }
            step = Step.with(virtualFile.getName(), line);
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


            // Provide a dialog for Step editing
            final StepEditor stepEditor = new StepEditor(project, step);
            final boolean okSelected = stepEditor.showAndGet();
            if (!okSelected) return; // i.e. cancel the step creation

            final Step updatedStep = stepEditor.getUpdatedStep();
            activeTour.get().addStep(updatedStep);
            StateManager.getInstance().getState(project).updateTour(activeTour.get());

            // Notify UI to re-render
            project.getMessageBus().syncPublisher(TourUpdateNotifier.TOPIC).tourUpdated(activeTour.get());
        }

    }


}