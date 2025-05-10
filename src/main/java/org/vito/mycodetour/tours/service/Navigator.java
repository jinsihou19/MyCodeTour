package org.vito.mycodetour.tours.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.domain.Tour;
import org.vito.mycodetour.tours.state.StateManager;
import org.vito.mycodetour.tours.state.StepSelectionNotifier;
import org.vito.mycodetour.tours.ui.CodeTourNotifier;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Navigator class that navigates the user to the location that a step indicates.
 * Also renders the Step's description to the editor (as notification for now)
 *
* @author vito
* Created on 2025/01/01
 */
public class Navigator {

    public static final String NAVIGATE = "navigate://";
    public static final String TOUR = "tour://";
    private static final String FILE_JBCEFBROWSER = "file:///jbcefbrowser/";

    public static void navigateLine(@NotNull Step step, @NotNull Project project, BiConsumer<Step, Project> renderStep) {
        if (project.getBasePath() == null) return;

        SlowOperations.allowSlowOperations(() -> {

            if (step.getFile() == null) {
                renderStep.accept(step, project);
                return;
            }

            // Try finding the appropriate file to navigate to
            final String stepFileName = Paths.get(step.getFile()).getFileName().toString();
            final List<VirtualFile> validVirtualFiles = FilenameIndex
                    .getVirtualFilesByName(stepFileName, GlobalSearchScope.projectScope(project)).stream()
                    .filter(file -> Utils.isFileMatchesStep(file, step))
                    .collect(Collectors.toList());

            if (validVirtualFiles.isEmpty()) {
                // Case for configured but not found file
                CodeTourNotifier.error(project, String.format("Could not locate navigation target '%s' for Step '%s'",
                        step.getFile(), step.getTitle()));
            } else if (validVirtualFiles.size() > 1) {
                // In case there is more than one file that matches with the Step, prompt User to pick the appropriate one
                final String prompt = "More Than One Target File Found! Select the One You Want to Navigate to:";
                JBPopupFactory.getInstance()
                        .createListPopup(new BaseListPopupStep<>(prompt, validVirtualFiles) {
                            @Override
                            public @Nullable PopupStep<?> onChosen(VirtualFile selectedValue, boolean finalChoice) {

                                navigateLine(step, project, selectedValue);

                                // Show a Popup
                                renderStep.accept(step, project);

                                return super.onChosen(selectedValue, finalChoice);
                            }
                        }).showInFocusCenter();

                // Notify user to be more specific
                CodeTourNotifier.warn(project, "Tip: A Step's file path can be more specific either by having a " +
                        "relative path ('file' property) or by setting the 'directory' property on Step's definition");
                return; // Make sure we return here, because PopUp runs on another Thread (no wait for User input)
            } else {
                // Case for exactly one match. Just use it
                navigateLine(step, project, validVirtualFiles.get(0));
            }

            // Show Step's popup and return
            renderStep.accept(step, project);
        });
    }

    /**
     * 导航代码形如 "MyJava.java:1"
     *
     * @param navigateUrl 导航url
     * @param project     工程
     */
    public static void navigateLine(@NotNull String navigateUrl, @NotNull Project project) {
        String fileName = navigateUrl;
        int line = 0;
        if (navigateUrl.contains(":")) {
            fileName = navigateUrl.substring(0, navigateUrl.indexOf(":"));
            line = Integer.parseInt(navigateUrl.substring(navigateUrl.indexOf(":") + 1));
        }

        final List<VirtualFile> validVirtualFiles = new ArrayList<>(FilenameIndex
                .getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(project)));

        if (validVirtualFiles.isEmpty()) {
            CodeTourNotifier.error(project, String.format("Could not locate navigation target '%s'", navigateUrl));
        } else if (validVirtualFiles.size() > 1) {
            final String prompt = "More Than One Target File Found! Select the One You Want to Navigate To:";
            int finalLine = line;
            JBPopupFactory.getInstance()
                    .createListPopup(new BaseListPopupStep<>(prompt, validVirtualFiles) {
                        @Override
                        public @Nullable PopupStep<?> onChosen(VirtualFile selectedValue, boolean finalChoice) {
                            navigateLine(finalLine, project, selectedValue);
                            return super.onChosen(selectedValue, finalChoice);
                        }
                    }).showInFocusCenter();

            CodeTourNotifier.warn(project, "Tip: A file path can be more specific either by having a " +
                    "relative path ('file' property) or by setting the 'directory' property on definition");
        } else {
            navigateLine(line, project, validVirtualFiles.get(0));
        }
    }

    /**
     * 导航到代码
     *
     * @param navigateUrl 导航链接
     * @param project     工程
     */
    public static void navigateCode(@NotNull String navigateUrl, @NotNull Project project) {

        ApplicationManager.getApplication().invokeLater(() -> {
            String url = navigateUrl;
            if (navigateUrl.startsWith(NAVIGATE)) {
                url = navigateUrl.substring(NAVIGATE.length());
            } else if (navigateUrl.startsWith(FILE_JBCEFBROWSER)) {
                url = navigateUrl.substring(FILE_JBCEFBROWSER.length());
            }
            if (url.contains(":")) {
                navigateLine(url, project);
            } else {
                navigateJavaPsi(url, project);
            }
        }, ModalityState.defaultModalityState());
    }

    /**
     * 导航代码，形如
     * 1. MyClass#myMethod
     * 2. com.fr.MyClass
     *
     * @param navigateUrl 导航url
     * @param project     工程
     */
    public static void navigateJavaPsi(@NotNull String navigateUrl, @NotNull Project project) {

        String[] parts = navigateUrl.split("#");
        if (parts.length == 2) {
            String className = parts[0];
            String methodName = parts[1];
            navigateToMethod(className, methodName, project);
        } else {
            navigateToClass(parts[0], project);
        }
    }

    private static void navigateLine(@NotNull Step step, @NotNull Project project, VirtualFile targetVirtualFile) {
        final int line = step.getLine() != null ? step.getLine() - 1 : 0;
        new OpenFileDescriptor(project, targetVirtualFile, Math.max(line, 0), 1)
                .navigate(true);
    }

    private static void navigateLine(int line, @NotNull Project project, VirtualFile targetVirtualFile) {
        new OpenFileDescriptor(project, targetVirtualFile, Math.max(line - 1, 0), 1)
                .navigate(true);
    }

    /**
     * 导航到指定的类和方法
     */
    private static void navigateToMethod(String className, String methodName, @NotNull Project project) {
        PsiClass psiClass = JavaPsiFacade.getInstance(project)
                .findClass(className, GlobalSearchScope.allScope(project));

        if (psiClass == null) {
            CodeTourNotifier.error(project, String.format("Could not locate navigation target class '%s'", className));
            return;
        }

        for (PsiMethod method : psiClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                Navigatable navigatable = (Navigatable) method.getNavigationElement();
                if (navigatable.canNavigate()) {
                    navigatable.navigate(true);
                    return;
                }
            }
        }

        // 没有这个方法就导航到类吧
        Navigatable navigatable = (Navigatable) psiClass.getNavigationElement();
        if (navigatable.canNavigate()) {
            navigatable.navigate(true);
        }
    }

    /**
     * 导航到指定的类
     */
    private static void navigateToClass(String className, @NotNull Project project) {
        PsiClass psiClass = JavaPsiFacade.getInstance(project)
                .findClass(className, GlobalSearchScope.allScope(project));

        if (psiClass == null) {
            CodeTourNotifier.error(project, String.format("Could not locate navigation target class '%s'", className));
            return;
        }

        Navigatable navigatable = (Navigatable) psiClass.getNavigationElement();
        if (navigatable.canNavigate()) {
            navigatable.navigate(true);
        }
    }

    /**
     * 导航到指定的 tour 和 step
     * 格式：tour:abc.tour#stepTitle
     */
    public static void navigateTour(String tourUrl, Project project) {
        if(!tourUrl.startsWith(TOUR)) {
            return;
        }

        String[] parts = tourUrl.substring(TOUR.length()).split("#");
        if (parts.length != 2) {
            CodeTourNotifier.error(project, "Invalid tour navigation format. Expected format: tour:abc.tour#stepTitle");
            return;
        }

        String tourFile = parts[0];
        String stepTitle = parts[1];

        // 查找 tour
        Optional<Tour> tour = StateManager.getInstance().getState(project).getTours().stream()
                .filter(t -> t.getTourFile().equals(tourFile))
                .findFirst();

        if (tour.isEmpty()) {
            CodeTourNotifier.error(project, String.format("Could not find tour '%s'", tourFile));
            return;
        }

        // 查找 step
        Optional<Step> step = tour.get().getSteps().stream()
                .filter(s -> s.getTitle().equals(stepTitle))
                .findFirst();

        if (step.isEmpty()) {
            CodeTourNotifier.error(project, String.format("Could not find step '%s' in tour '%s'", stepTitle, tourFile));
            return;
        }

        // 激活 tour 并导航到 step
        StateManager.getInstance().getState(project).setActiveTour(tour.get());
        project.getMessageBus().syncPublisher(StepSelectionNotifier.TOPIC).selectStep(step.get());
    }

}
