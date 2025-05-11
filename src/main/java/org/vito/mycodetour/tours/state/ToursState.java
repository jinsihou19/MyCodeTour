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
import org.vito.mycodetour.tours.domain.TourFolder;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Collection;

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

        // 使用索引查找所有.tour文件夹
        var tourFolders = loadFoldersFromIndex(project);
        if (tourFolders.isEmpty()) {
            // 如果索引中没有找到，尝试从文件系统加载
            tourFolders = loadFoldersFromFS();
        }

        // 从每个文件夹中加载tour文件
        for (TourFolder folder : tourFolders) {
            var folderTours = loadToursFromFolder(folder);
            tours.addAll(folderTours);
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
        tours.sort(comparator);

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

    /**
     * 从索引中加载所有.tour文件夹
     */
    private List<TourFolder> loadFoldersFromIndex(@NotNull Project project) {
        return ReadAction.compute(() -> {
            // 使用索引查找所有.tour文件夹
            Collection<VirtualFile> tourDirs = FilenameIndex.getAllFilesByExt(project, Props.TOURS_DIR);
            return tourDirs.stream()
                    .filter(VirtualFile::isDirectory)
                    .map(dir -> new TourFolder(dir.getName(), dir))
                    .collect(Collectors.toList());
        });
    }

    /**
     * 从文件系统加载所有.tour文件夹
     */
    private List<TourFolder> loadFoldersFromFS() {
        Optional<VirtualFile> toursDir = getToursDir();
        if (toursDir.isEmpty()) return Collections.emptyList();

        List<TourFolder> folders = new ArrayList<>();
        VfsUtilCore.iterateChildrenRecursively(toursDir.get(),
                null,
                fileOrDir -> {
                    if (fileOrDir.isDirectory()) {
                        folders.add(new TourFolder(fileOrDir.getName(), fileOrDir));
                    }
                    return true;
                });
        return folders;
    }

    /**
     * 从指定文件夹加载所有tour文件
     */
    private List<Tour> loadToursFromFolder(TourFolder folder) {
        List<Tour> folderTours = new ArrayList<>();
        VirtualFile folderFile = folder.getVirtualFile();

        // 遍历文件夹中的所有文件
        for (VirtualFile file : folderFile.getChildren()) {
            if (!file.isDirectory() && Props.TOUR_EXTENSION.equals(file.getExtension())) {
                parse(file).ifPresent(tour -> {
                    tour.setVirtualFile(file);
                    folderTours.add(tour);
                });
            }
        }

        return folderTours;
    }

    private void updateLinesCache(List<Tour> tours) {
        stepFileLinesIndex.clear();
        tours.forEach(tour -> stepFileLinesIndex.putAll(tour.getStepIndexes()));
    }

    /**
     * Persists the provided Tour to filesystem
     */
    public Tour createTour(Project project, Tour tour) {
        return createTour(project, tour, getToursDir().orElse(null));
    }

    /**
     * Persists the provided Tour to filesystem
     */
    public Tour createTour(Project project, Tour tour, VirtualFile directory) {
        if (project.getBasePath() == null) return null;
        final String fileName = tour.getTourFile();

        tours.add(tour);

        LOG.info(String.format("Saving Tour '%s' (%s steps) into file '%s'%n",
                tour.getTitle(), tour.getSteps().size(), fileName));

        WriteAction.runAndWait(() -> {
            VirtualFile parent = directory;
            if (parent == null) {
                Optional<VirtualFile> toursDir = createToursDir();
                if (toursDir.isEmpty()) {
                    throw new PluginException("Could not find or creat '.tours' directory. Tour creation failed",
                            PluginId.findId("org.vito.mycodetour"));
                } else {
                    parent = toursDir.get();
                }
            }
            // Persist the file
            try {
                final VirtualFile newTourVfile = parent.createChildData(this, fileName);
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

        // 使用Map来存储文件夹结构
        Map<String, List<Tour>> folderTours = new HashMap<>();
        Map<String, TourFolder> folders = new HashMap<>();

        VfsUtilCore.iterateChildrenRecursively(toursDir.get(),
                null,
                fileOrDir -> {
                    if (fileOrDir.isDirectory()) {
                        // 如果是文件夹，创建一个TourFolder对象
                        String folderPath = fileOrDir.getPath();
                        folders.put(folderPath, new TourFolder(fileOrDir.getName(), fileOrDir));
                        folderTours.put(folderPath, new ArrayList<>());
                    } else if (Props.TOUR_EXTENSION.equals(fileOrDir.getExtension())) {
                        // 如果是tour文件，解析它并添加到对应的文件夹中
                        parse(fileOrDir).ifPresent(tour -> {
                            String parentPath = fileOrDir.getParent().getPath();
                            if (parentPath.equals(toursDir.get().getPath())) {
                                // 如果父目录是.tours，直接添加到根目录
                                tours.add(tour);
                            } else {
                                // 否则添加到对应的文件夹中
                                folderTours.computeIfAbsent(parentPath, k -> new ArrayList<>()).add(tour);
                            }
                        });
                    }
                    return true;
                });

        // 将文件夹中的tour添加到结果列表中
        for (Map.Entry<String, List<Tour>> entry : folderTours.entrySet()) {
            if (!entry.getKey().equals(toursDir.get().getPath())) {
                tours.addAll(entry.getValue());
            }
        }

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

    /**
     * 获取指定文件夹下的所有tour
     */
    public List<Tour> getToursInFolder(TourFolder folder) {
        if (folder == null) return Collections.emptyList();

        return tours.stream()
                .filter(tour -> {
                    VirtualFile tourFile = tour.getVirtualFile();
                    return tourFile != null && tourFile.getParent().equals(folder.getVirtualFile());
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取所有文件夹
     */
    public List<TourFolder> getFolders() {
        // 优先使用索引查找
        List<TourFolder> folders = loadFoldersFromIndex(project);
        if (folders.isEmpty()) {
            // 如果索引中没有找到，从文件系统加载
            folders = loadFoldersFromFS();
        }
        return folders;
    }

}
