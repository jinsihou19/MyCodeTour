package org.vito.mycodetour.tours.state;


import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.search.FilenameIndex;
import org.jetbrains.annotations.NotNull;
import org.vito.mycodetour.tours.domain.OnboardingAssistant;
import org.vito.mycodetour.tours.domain.Props;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.domain.Tour;
import org.vito.mycodetour.tours.service.AppSettingsState;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * tour状态，包含读写
 *
 * @author vito
 * @since 1.0
 * Created on 2025/1/22
 */
public class ToursState {

    private static final Logger LOG = Logger.getInstance(StateManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    private List<Tour> tours = new ArrayList<>();
    private Optional<Tour> activeTour = Optional.empty();
    private int activeStepIndex = -1;
    private Project project;
    private static LocalDateTime lastValidationTime = LocalDateTime.now().minusHours(2);
    // Caching
    private final Multimap<String, Integer> stepFileLinesIndex = ArrayListMultimap.create();

    public ToursState(Project project) {
        this.project = project;
        tours = loadTours(project);
    }

    public List<Tour> getTours() {
        return tours;
    }

    public Optional<Tour> getActiveTour() {
        return activeTour;
    }

    public ToursState setActiveTour(Tour activeTour) {
        this.activeTour = Optional.ofNullable(activeTour);
        return this;
    }

    public int getActiveStepIndex() {
        return activeStepIndex;
    }

    public ToursState setActiveStepIndex(int activeStepIndex) {
        this.activeStepIndex = activeStepIndex;
        return this;
    }

    public void resetActiveStepIndex() {
        activeStepIndex = -1;
    }

    public Project getProject() {
        return project;
    }

    public ToursState setProject(Project project) {
        this.project = project;
        return this;
    }

    public static LocalDateTime getLastValidationTime() {
        return lastValidationTime;
    }

    public void setLastValidationTime(LocalDateTime lastValidationTime) {
        ToursState.lastValidationTime = lastValidationTime;
    }

    public boolean isFileIncludedInAnyStep(String fileName) {
        return stepFileLinesIndex.containsKey(fileName);
    }

    public boolean isValidStep(String fileName, Integer line) {
        return stepFileLinesIndex.get(fileName).contains(line);
    }

    public String getStepMetaLabel(String stepTitle) {
        if (activeTour.isPresent()) {
            Tour tour = activeTour.get();
            return String.format("<strong>CodeTour</strong> <em>Step #%s of %s (%s)</em>",
                    activeStepIndex + 1, tour.getSteps().size(), stepTitle);
        } else {
            return String.format("<strong>CodeTour</strong> <em>Step #%s (%s)</em>",
                    activeStepIndex + 1, stepTitle);
        }

    }

    private List<Tour> loadTours(@NotNull Project project) {
        final List<Tour> tours = new ArrayList<>();
        AppSettingsState settings = AppSettingsState.getInstance();
        if (settings.isOnboardingAssistantOn()) {
            final Tour onboardingTour = OnboardingAssistant.getInstance().getTour();
            if (onboardingTour != null)
                tours.add(onboardingTour);
        }

        // 只通过索引找所有的指南文件
        var userTours = loadFromIndex(project);
        if (userTours.isEmpty()) {
            // 通过猜测工程再找一次
            userTours = new ArrayList<>(getSpeciseTourList());
        }

        // Sort User Tours. By default, they are sorted base on Title. Otherwise, it follows User Settings
        Comparator<Tour> comparator = Comparator.comparing(Tour::getTitle);
        switch (settings.getSortOption()) {
            case FILENAME -> comparator = Comparator.comparing(Tour::getTourFile);
            case CREATION_DATE ->
                    comparator = Comparator.comparing(Tour::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        if (AppSettingsState.SortDirectionE.DESC.equals(settings.getSortDirection()))
            comparator = comparator.reversed(); // ASC,DESC
        LOG.info("Sorting Tours using: %s - %s".formatted(settings.getSortOption(), settings.getSortDirection()));
        userTours.sort(comparator);

        tours.addAll(userTours);
        // Cache some info
        updateLinesCache(tours);

        // Validate them at most once in an hour
        final LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(lastValidationTime.plusHours(1))) {
            Validator.validateTours(project, tours);
            lastValidationTime = now;
        }

        return tours;
    }

    private void updateLinesCache(List<Tour> tours) {
        stepFileLinesIndex.clear();
        tours.forEach(tour -> stepFileLinesIndex.putAll(tour.getStepIndexes()));
    }

    /**
     * Persists the provided Tour to filesystem
     */
    public Tour createTour(Project project, Tour tour) {
        if (project.getBasePath() == null) return null;
        final String fileName = tour.getTourFile();

        tours.add(tour);

        LOG.info(String.format("Saving Tour '%s' (%s steps) into file '%s'%n",
                tour.getTitle(), tour.getSteps().size(), fileName));

        WriteAction.runAndWait(() -> {
            Optional<VirtualFile> toursDir = getToursDir();
            if (toursDir.isEmpty()) {
                toursDir = createToursDir();
                if (toursDir.isEmpty()) {
                    throw new PluginException("Could not find or creat '.tours' directory. Tour creation failed",
                            PluginId.findId("org.uom.lefterisxris.codetour"));
                }
            }
            // Persist the file
            try {
                final VirtualFile newTourVfile = toursDir.get().createChildData(this, fileName);
                tour.setVirtualFile(newTourVfile);
                newTourVfile.setBinaryContent(GSON.toJson(tour).getBytes(StandardCharsets.UTF_8));
                setActiveTour(tour);
            } catch (IOException e) {
                LOG.error("Failed to create tour file: " + e.getMessage(), e);
            }
        });
        return tour;
    }

    /**
     * Updates the given tour by deleting and recreating it
     *
     * @param tour The tour to persist
     * @return the updated tour
     */
    public Tour updateTour(Tour tour) {

        WriteAction.runAndWait(() -> {
            try {
                final VirtualFile newTourVfile = tour.getVirtualFile();
                newTourVfile.setBinaryContent(GSON.toJson(tour).getBytes(StandardCharsets.UTF_8));
                updateLinesCache(tours);
            } catch (IOException e) {
                LOG.error("Failed to create tour file: " + e.getMessage(), e);
            }
        });
        setActiveTour(tour);

        return tour;
    }

    /**
     * 删除 tour
     *
     * @param tour 指南
     * @return 删除的tour
     */
    public Tour deleteTour(Tour tour) {
        findTourFile(tour).ifPresent(virtualFile -> WriteAction.runAndWait(() -> {
            try {
                virtualFile.delete(this);
                tours.remove(tour);
                updateLinesCache(tours);
            } catch (IOException e) {
                LOG.error(e);
            }
        }));
        return tour;
    }

    public void reloadState() {
        tours = loadTours(project);
    }

    public boolean shouldNotify(Project project) {
        return true;
    }


    @NotNull
    private List<Tour> getSpeciseTourList() {
        Optional<VirtualFile> userWorkSpace = getToursDir();
        if (userWorkSpace.isEmpty()) {
            return Collections.emptyList();
        }

        return userWorkSpace.map(virtualFile -> Arrays.stream(virtualFile.getChildren())
                .filter(f -> f.getName().endsWith(".tour"))
                .map(f -> {
                    Tour tour;
                    try {
                        tour = GSON.fromJson(new InputStreamReader(f.getInputStream(), StandardCharsets.UTF_8), Tour.class);
                        tour.setVirtualFile(f);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return tour;
                }).toList()).orElse(Collections.emptyList());
    }

    private List<Tour> loadFromIndex(@NotNull Project project) {
        return ReadAction.compute(() -> FilenameIndex.getAllFilesByExt(project, Props.TOUR_EXTENSION).stream()
                        .map(virtualFile -> {
                            Tour tour;
                            try {
                                LOG.info("Reading (from Index) Tour from file: " + virtualFile.getName());
                                tour = GSON.fromJson(new InputStreamReader(virtualFile.getInputStream(), StandardCharsets.UTF_8), Tour.class);
                            } catch (IOException e) {
                                LOG.error("Skipping file: " + virtualFile.getName(), e);
                                return null;
                            }
                            tour.setVirtualFile(virtualFile);
                            return tour;
                        })
                        .filter(Objects::nonNull))
                .collect(Collectors.toList());
    }

    private List<Tour> loadFromFS() {
        final List<Tour> tours = new ArrayList<>();
        final Optional<VirtualFile> toursDir = getToursDir();
        if (toursDir.isEmpty()) return new ArrayList<>();

        VfsUtilCore.iterateChildrenRecursively(toursDir.get(),
                null,
                fileOrDir -> {
                    if (!fileOrDir.isDirectory() && Props.TOUR_EXTENSION.equals(fileOrDir.getExtension()))
                        parse(fileOrDir).ifPresent(tours::add);
                    return true;
                });
        return tours;
    }

    private Optional<Tour> parse(VirtualFile file) {
        if (file.isDirectory())
            return Optional.empty();

        try {
            LOG.info("Reading (from FS) Tour from file: " + file.getName());
            return Optional.of(
                    GSON.fromJson(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8), Tour.class));
        } catch (Exception e) {
            LOG.error("Skipping file: " + file.getName(), e);
        }
        return Optional.empty();
    }

    private Optional<VirtualFile> findTourFile(Tour tour) {
        final Optional<VirtualFile> toursDir = getToursDir();
        if (toursDir.isEmpty()) return Optional.empty();

        final List<VirtualFile> virtualFiles = new ArrayList<>();
        VfsUtilCore.iterateChildrenRecursively(toursDir.get(),
                null,
                fileOrDir -> {
                    if (!fileOrDir.isDirectory() && tour.getTourFile().equals(fileOrDir.getName()))
                        virtualFiles.add(fileOrDir);
                    return true;
                });

        return virtualFiles.isEmpty() ? Optional.empty() : Optional.of(virtualFiles.get(0));
    }

    private Optional<VirtualFile> findTourFile(String tourId) {
        return getTours().stream()
                .filter(tour -> tourId.equals(tour.getId()))
                .findFirst()
                .flatMap(tour -> findTourFile(tour));
    }

    /**
     * 不要直接用，有场景获取不到
     */
    private Optional<VirtualFile> getProjectDir() {
        String projectDir = AppSettingsState.getInstance().getSourcePath();
        if (StringUtil.isNotEmpty(projectDir)) {
            return Optional.ofNullable(VirtualFileManager.getInstance().findFileByNioPath(new File(projectDir).toPath()));
        }

        return Optional.ofNullable(ProjectUtil.guessProjectDir(project));
    }

    private Optional<VirtualFile> getToursDir() {
        return Arrays.stream(getProjectDir().get().getChildren())
                .filter(file -> file.isDirectory() && file.getName().equals(Props.TOURS_DIR))
                .findFirst();
    }

    private Optional<VirtualFile> createToursDir() {
        Optional<VirtualFile> toursDir = getProjectDir();
        if (toursDir.isEmpty()) {
            return Optional.empty();
        }

        try {
            toursDir.get().createChildDirectory(this, Props.TOURS_DIR);
        } catch (IOException e) {
            LOG.error("Failed to create .tours Directory: " + e.getMessage(), e);
            return Optional.empty();
        }

        return getToursDir();
    }

    /**
     * Retrieves the Next Step of the currently active Tour. Also updates the activeStepIndex
     */
    public Optional<Step> getNextStep() {
        return getNextOrPrevStep(true);
    }

    /**
     * Retrieves the Previous Step of the currently active Tour. Also updates the activeStepIndex
     */
    public Optional<Step> getPrevStep() {
        return getNextOrPrevStep(false);
    }

    public boolean hasPrevStep() {
        return hasNextOrPrevStep(false);
    }

    public boolean hasNextStep() {
        return hasNextOrPrevStep(true);
    }

    private boolean hasNextOrPrevStep(boolean next) {
        final Optional<Tour> activeTour = getActiveTour();
        if (activeTour.isEmpty()) return false;

        int activeIndex = getActiveStepIndex();
        if (activeIndex == -1) return false;

        final int totalSteps = activeTour.get().getSteps().size();
        final int candidateStep = next ? activeIndex + 1 : activeIndex - 1;

        // if candidate is in range, then it exists!
        return candidateStep >= 0 && candidateStep < totalSteps;
    }

    private Optional<Step> getNextOrPrevStep(boolean next) {
        final Optional<Tour> activeTour = getActiveTour();
        if (activeTour.isEmpty()) return Optional.empty();

        int activeIndex = getActiveStepIndex();
        if (activeIndex == -1) return Optional.empty();

        final int candidate = next ? activeIndex + 1 : activeIndex - 1;
        if (candidate >= 0 && activeTour.get().getSteps().size() > candidate) {
            setActiveStepIndex(candidate);
            return Optional.of(activeTour.get().getSteps().get(candidate));
        }

        return Optional.empty();
    }

    /**
     * Tries to find the Step that is configured to navigate to the provided file and line, and if found,
     * it activates its Tour and sets the step as active.
     *
     * @param fileName The file to which the Step is configured to navigate
     * @param line     The line to which the Step is configured to navigate
     * @return The Step (optional)
     */
    public Optional<Step> findStepByFileLine(String fileName, int line) {
        final Optional<Tour> tourToActivate = getTours().stream()
                .filter(tour -> tour.getSteps().stream()
                        .filter(step -> step.getFile() != null)
                        .anyMatch(step -> step.getFile().equals(fileName) && step.getLine() == line))
                .findFirst();
        if (tourToActivate.isEmpty()) return Optional.empty();
        setActiveTour(tourToActivate.get());

        final List<Step> steps = tourToActivate.get().getSteps();
        for (int i = 0; i < steps.size(); i++) {
            final Step step = steps.get(i);
            if (step.getFile() != null && step.getFile().equals(fileName) && step.getLine() == line) {
                setActiveStepIndex(i);
                return Optional.of(step);
            }
        }

        return Optional.empty();
    }

}
