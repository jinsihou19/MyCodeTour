package org.vito.mycodetour.tours.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.domain.Tour;
import org.vito.mycodetour.tours.state.StateManager;
import org.vito.mycodetour.tours.state.StepSelectionNotifier;
import org.vito.mycodetour.tours.ui.CodeTourNotifier;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.vito.mycodetour.tours.service.PsiHelper.methodWithParameter;

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
    private static final String FILE_EXCALIDRAW_SUFFIX = ".excalidraw";

    /**
     * 导航到指定行
     *
     * @param step    步骤
     * @param project 工程
     */
    public static void navigateLine(@NotNull Step step, @NotNull Project project) {
        if (project.getBasePath() == null) return;

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (step.getFile() == null) {
                return;
            }

            if (step.getLine() == null) {
                navigateJavaPsi(step.getFile(), project);
                return;
            }

            // 使用 ProjectRootManager 查找文件
            String relativePath = step.getFile();
            VirtualFile targetFile = ReadAction.compute(() -> {
                ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
                // 遍历所有源码根目录
                for (VirtualFile sourceRoot : rootManager.getContentSourceRoots()) {
                    VirtualFile file = sourceRoot.findFileByRelativePath(relativePath);
                    if (file != null) {
                        return file;
                    }
                }
                return null;
            });

            if (targetFile != null) {
                final int line = step.getLine() != null ? step.getLine() : 0;
                navigateLine(line, project, targetFile);
            } else {
                // 如果找不到文件，回退到原来的文件名搜索逻辑
                final String stepFileName = Paths.get(step.getFile()).getFileName().toString();
                final List<VirtualFile> validVirtualFiles = ReadAction.compute(() -> new ArrayList<>(FilenameIndex
                        .getVirtualFilesByName(stepFileName, GlobalSearchScope.projectScope(project))));

                if (validVirtualFiles.isEmpty()) {
                    CodeTourNotifier.error(project, String.format("Could not locate navigation target '%s' for Step '%s'",
                            step.getFile(), step.getTitle()));
                } else if (validVirtualFiles.size() > 1) {
                    // In case there is more than one file that matches with the Step, prompt User to pick the appropriate one
                    final String prompt = "More Than One Target File Found! Select the One You Want to Navigate to:";
                    ApplicationManager.getApplication().invokeLater(() -> JBPopupFactory.getInstance()
                            .createListPopup(new BaseListPopupStep<>(prompt, validVirtualFiles) {
                                @Override
                                public @Nullable PopupStep<?> onChosen(VirtualFile selectedValue, boolean finalChoice) {
                                    final int line = step.getLine() != null ? step.getLine() : 0;
                                    navigateLine(line, project, selectedValue);
                                    return super.onChosen(selectedValue, finalChoice);
                                }
                            }).showInFocusCenter());

                    // Notify user to be more specific
                    CodeTourNotifier.warn(project, "Tip: A Step's file path can be more specific either by having a " +
                            "relative path ('file' property) or by setting the 'directory' property on Step's definition");
                } else {
                    final int line = step.getLine() != null ? step.getLine() : 0;
                    navigateLine(line, project, validVirtualFiles.get(0));
                }
            }
        });
    }

    /**
     * 导航到代码
     *
     * @param navigateUrl 导航链接
     * @param project     工程
     */
    public static void navigateCode(@NotNull String navigateUrl, @NotNull Project project) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String url;
            if (navigateUrl.startsWith(NAVIGATE)) {
                url = navigateUrl.substring(NAVIGATE.length());
            } else if (navigateUrl.startsWith(FILE_JBCEFBROWSER)) {
                url = navigateUrl.substring(FILE_JBCEFBROWSER.length());
            } else {
                url = navigateUrl;
            }

            if (url.endsWith(FILE_EXCALIDRAW_SUFFIX)) {
                navigateExcalidraw(project, url);
            } else if (url.contains(":")) {
                navigateLine(url, project);
            } else {
                navigateJavaPsi(url, project);
            }
        });
    }

    /**
     * 简单处理excalidraw文件的链接
     *
     * @param project 工程
     * @param url
     */
    private static void navigateExcalidraw(@NotNull Project project, String url) {
        VirtualFile targetFile = ReadAction.compute(() ->
                VirtualFileManager.getInstance().findFileByNioPath(new File(url).toPath()));
        if (targetFile != null) {
            ApplicationManager.getApplication().invokeLater(() ->
                    new OpenFileDescriptor(project, targetFile, 0).navigate(true));
        }
    }

    /**
     * 导航代码形如 "MyJava.java:1"
     *
     * @param navigateUrl 导航url
     * @param project     工程
     */
    private static void navigateLine(@NotNull String navigateUrl, @NotNull Project project) {
        String fileName;
        int line = 0;
        if (navigateUrl.contains(":")) {
            fileName = navigateUrl.substring(0, navigateUrl.indexOf(":"));
            line = Integer.parseInt(navigateUrl.substring(navigateUrl.indexOf(":") + 1));
        } else {
            fileName = navigateUrl;
        }

        // 使用 ProjectRootManager 查找文件
        VirtualFile targetFile = ReadAction.compute(() -> {
            ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
            // 遍历所有源码根目录
            for (VirtualFile sourceRoot : rootManager.getContentSourceRoots()) {
                VirtualFile file = sourceRoot.findFileByRelativePath(fileName);
                if (file != null) {
                    return file;
                }
            }
            return null;
        });

        if (targetFile != null) {
            navigateLine(line, project, targetFile);
        } else {

            Collection<VirtualFile> files = ReadAction.compute(() ->
                    FilenameIndex.getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(project)));
            final List<VirtualFile> validVirtualFiles = new ArrayList<>(files);

            if (validVirtualFiles.isEmpty()) {
                CodeTourNotifier.error(project, String.format("Could not locate navigation target '%s'", navigateUrl));
            } else if (validVirtualFiles.size() > 1) {
                final String prompt = "More Than One Target File Found! Select the One You Want to Navigate To:";
                int finalLine = line;
                ApplicationManager.getApplication().invokeLater(() -> JBPopupFactory.getInstance()
                        .createListPopup(new BaseListPopupStep<>(prompt, validVirtualFiles) {
                            @Override
                            public @Nullable PopupStep<?> onChosen(VirtualFile selectedValue, boolean finalChoice) {
                                navigateLine(finalLine, project, selectedValue);
                                return super.onChosen(selectedValue, finalChoice);
                            }
                        }).showInFocusCenter());

                CodeTourNotifier.warn(project, "Tip: A file path can be more specific either by having a " +
                        "relative path ('file' property) or by setting the 'directory' property on definition");
            } else {
                navigateLine(line, project, validVirtualFiles.get(0));
            }
        }
    }


    /**
     * 导航代码，形如
     * 1. MyClass#myMethod
     * 2. com.fr.MyClass
     *
     * @param navigateUrl 导航url
     * @param project     工程
     */
    private static void navigateJavaPsi(@NotNull String navigateUrl, @NotNull Project project) {

        String[] parts = navigateUrl.split("#");
        if (parts.length == 2) {
            String className = parts[0];
            String methodName = parts[1];
            navigateToMethodField(className, methodName, project);
        } else {
            navigateToClass(parts[0], project);
        }
    }

    private static void navigateLine(int line, @NotNull Project project, VirtualFile targetVirtualFile) {
        ApplicationManager.getApplication().invokeLater(() ->
                new OpenFileDescriptor(project, targetVirtualFile, Math.max(line - 1, 0), 1)
                        .navigate(true));
    }

    /**
     * 导航到指定的类和方法
     */
    private static void navigateToMethodField(String className, String methodName, @NotNull Project project) {
        ReadAction.run(() -> {
            PsiClass psiClass = JavaPsiFacade
                    .getInstance(project)
                    .findClass(className, GlobalSearchScope.allScope(project));

            if (psiClass == null) {
                CodeTourNotifier.error(project, String.format("Could not locate navigation target class '%s'", className));
                return;
            }

            // 携带签名的方法引用
            if (methodName.contains("(")) {
                String methodNameDecode = URLDecoder.decode(methodName, StandardCharsets.UTF_8);
                for (PsiMethod method : psiClass.getMethods()) {
                    if (methodWithParameter(method).equals(methodNameDecode)) {
                        if (navigatePsiElement(method)) {
                            return;
                        }
                    }
                }
            } else {
                // 先找方法
                for (PsiMethod method : psiClass.getMethods()) {
                    if (method.getName().equals(methodName)) {
                        if (navigatePsiElement(method)) {
                            return;
                        }
                    }
                }

                // 从字段中再找一下
                for (PsiField field : psiClass.getFields()) {
                    if (field.getName().equals(methodName)) {
                        if (navigatePsiElement(field)) {
                            return;
                        }
                    }
                }
            }

            // 没有这个方法就导航到类吧
            navigatePsiElement(psiClass);
        });
    }

    private static boolean navigatePsiElement(PsiElement element) {
        Navigatable navigatable = (Navigatable) element.getNavigationElement();
        if (navigatable.canNavigate()) {
            ApplicationManager.getApplication().invokeLater(() -> navigatable.navigate(true));
            return true;
        }
        return false;
    }

    /**
     * 导航到指定的类
     */
    private static void navigateToClass(String className, @NotNull Project project) {
        PsiClass psiClass = ReadAction.compute(() -> JavaPsiFacade.getInstance(project)
                .findClass(className, GlobalSearchScope.allScope(project)));

        if (psiClass == null) {
            CodeTourNotifier.error(project, String.format("Could not locate navigation target class '%s'", className));
            return;
        }

        navigatePsiElement(psiClass);
    }

    /**
     * 导航到指定的 tour 和 step
     * 格式：tour:abc.tour#stepTitle
     */
    public static void navigateTour(String tourUrl, Project project) {
        if (!tourUrl.startsWith(TOUR)) {
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
