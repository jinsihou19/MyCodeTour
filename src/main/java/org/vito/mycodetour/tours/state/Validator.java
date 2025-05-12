package org.vito.mycodetour.tours.state;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.vito.mycodetour.tours.domain.OnboardingAssistant;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.domain.Tour;
import org.vito.mycodetour.tours.service.Utils;
import org.vito.mycodetour.tours.ui.CodeTourNotifier;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author vito
 * Created on 2025/01/01
 */
public class Validator {

    private static final Logger LOG = Logger.getInstance(Validator.class);

    public static boolean isDemo(Tour tour) {
        return tour.getId().equals(OnboardingAssistant.DEMO_ID);
    }

    public static void validateTours(@NotNull Project project, List<Tour> tours) {
        LOG.info("CodeTours Validation started at: " + LocalDateTime.now());
        final List<String> errors = new ArrayList<>();

        for (Tour tour : tours) {
            if (isDemo(tour)) continue;

            for (Step step : tour.getSteps()) {
                String identifier = step.reference();
                if (identifier == null || identifier.isEmpty()) continue;

                // 验证 PSI 元素引用
                if (step.getLine() != null) {
//                    validatePsiElementReference(project, step, errors, tour);
                } else {
                    // 验证文件行号引用
                    validateFileLineReference(project, step, errors, tour);
                }
            }
        }

        LOG.info(String.format("CodeTours Validation completed at: %s. Found %s errors",
                LocalDateTime.now(), errors.size()));

        if (!errors.isEmpty()) {
            final String title = String.format("%s Invalid Steps Found!", errors.size());
            errors.add("You might want to fix them for better Code Navigation.");
            final String content = String.join("\n", errors);
            MessageDialogBuilder.okCancel(title, content).ask(project);
            CodeTourNotifier.warn(project, title);
        }
    }

    private static void validateFileLineReference(@NotNull Project project, Step step, List<String> errors, Tour tour) {
        if (step.getFile() == null) return;

        final String stepFileName = Paths.get(step.getFile()).getFileName().toString();
        final List<VirtualFile> validVirtualFiles = FilenameIndex
                .getVirtualFilesByName(stepFileName, GlobalSearchScope.projectScope(project)).stream()
                .filter(file -> Utils.isFileMatchesStep(file, step))
                .toList();

        if (validVirtualFiles.isEmpty()) {
            errors.add(String.format("Step '%s' of Tour '%s' points to a non valid file: '%s'!\n",
                    step.getTitle(), tour.getTitle(), step.getFile()));
        }
    }
}