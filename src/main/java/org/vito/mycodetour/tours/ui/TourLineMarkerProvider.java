package org.vito.mycodetour.tours.ui;

import com.intellij.codeInsight.daemon.GutterName;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import icons.Icons;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vito.mycodetour.tours.service.PsiHelper;
import org.vito.mycodetour.tours.state.StateManager;
import org.vito.mycodetour.tours.state.StepSelectionNotifier;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author vito
 * Created on 2025/1/1
 */
public class TourLineMarkerProvider extends LineMarkerProviderDescriptor {

//    private final Map<String, PsiElement> markedLines = new HashMap<>();

    @Override
    public @Nullable("null means disabled") @GutterName String getName() {
        return "CodeTour step";
    }

    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
//        final PsiFile containingFile = element.getContainingFile();
//        final Project project = element.getProject();
//
//        if (element instanceof LeafPsiElement && containingFile != null &&
//                StateManager.getInstance().getState(project).isFileIncludedInAnyStep(containingFile.getName())) {
//            final Document document = PsiDocumentManager.getInstance(project).getDocument(containingFile);
//            if (document != null) {
//                final int lineNumber = document.getLineNumber(element.getTextOffset()) + 1;
//                final String fileLine = String.format("%s:%s", containingFile.getName(), lineNumber);
//                if (StateManager.getInstance().getState(project).isValidStep(containingFile.getName(), lineNumber)) {
//                    if (!markedLines.containsKey(fileLine) || element.equals(markedLines.get(fileLine))) {
//                        markedLines.put(fileLine, element);
//                        return new LineMarkerInfo<>(element, element.getTextRange(),
//                                CodeTourIcons.STEP,
//                                psiElement -> "Code Tour Step",
//                                (e, elt) -> {
//                                    StateManager.getInstance().getState(project).findStepByFileLine(containingFile.getName(),
//                                            lineNumber).ifPresent(step -> {
//                                        // Notify UI to select the step which will trigger its navigation
//                                        project.getMessageBus().syncPublisher(StepSelectionNotifier.TOPIC)
//                                                .selectStep(step);
//                                    });
//                                },
//                                GutterIconRenderer.Alignment.CENTER,
//                                () -> "Code Tour Step accessible");
//                    }
//                }
//            }
//        }
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
        super.collectSlowLineMarkers(elements, result);
        final Map<String, PsiElement> markedLines = new HashMap<>();

        for (PsiElement element : elements) {
            final Project project = element.getProject();

            // 获取元素的标识符
            String elementIdentifier = getElementIdentifier(element);
            if (elementIdentifier == null) {
                continue;
            }

            // 检查是否是有效的步骤（同时检查相对路径和文件名）
            if (StateManager.getInstance().getState(project).isValidStep(elementIdentifier)) {
                if (!markedLines.containsKey(elementIdentifier) || element.equals(markedLines.get(elementIdentifier))) {
                    markedLines.put(elementIdentifier, element);
                    StateManager.getInstance().getState(project)
                            .findStepByReference(elementIdentifier)
                            .ifPresent(step -> result.add(new LineMarkerInfo<>(
                                    element,
                                    element.getTextRange(),
                                    Icons.STEP_12,
                                    psiElement -> step.getTitle(),
                                    (e, elt) -> project
                                            .getMessageBus()
                                            .syncPublisher(StepSelectionNotifier.TOPIC)
                                            .selectStep(step),
                                    GutterIconRenderer.Alignment.CENTER,
                                    () -> "Code Tour Step accessible")));
                }
            }
        }
        markedLines.clear();
    }

    /**
     * 获取 PSI 元素的唯一标识符
     *
     * @param element PSI 元素
     * @return 元素的标识符，格式如下：
     * - 方法：className#methodName
     * - 类：fullyQualifiedClassName
     * - 字段：className#fieldName
     * - 其他：返回两个标识符，用逗号分隔：
     *   1. relativePath:lineNumber（相对于源码根目录的路径）
     *   2. fileName:lineNumber（仅文件名）
     */
    private @Nullable String getElementIdentifier(@NotNull PsiElement element) {
        String reference = PsiHelper.getReference(element);
        if (StringUtils.isNotEmpty(reference)) {
            return reference;
        }

        // 对于其他类型的元素，使用文件行号作为标识符
        PsiFile containingFile = element.getContainingFile();
        if (containingFile != null) {
            Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(containingFile);
            if (document != null) {
                int lineNumber = document.getLineNumber(element.getTextOffset()) + 1;
                // 获取文件相对于源码根目录的路径
                String relativePath = ReadAction.compute(() -> {
                    Project project = element.getProject();
                    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
                    VirtualFile virtualFile = containingFile.getVirtualFile();
                    if (virtualFile != null) {
                        VirtualFile sourceRoot = fileIndex.getSourceRootForFile(virtualFile);
                        if (sourceRoot != null) {
                            String fullPath = virtualFile.getPath();
                            String sourceRootPath = sourceRoot.getPath();
                            if (fullPath.startsWith(sourceRootPath)) {
                                return fullPath.substring(sourceRootPath.length() + 1);
                            }
                        }
                    }
                    return null;
                });

                // 返回两个标识符，用逗号分隔
                String fileName = containingFile.getName();
                if (relativePath != null) {
                    return String.format("%s:%d;%s:%d", relativePath, lineNumber, fileName, lineNumber);
                }
                return String.format("%s:%d", fileName, lineNumber);
            }
        }
        return null;
    }
}