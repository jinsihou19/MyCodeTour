package org.vito.mycodetour.tours.domain;

import com.google.common.collect.ArrayListMultimap;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 指南
 *
 * @author vito
 * @since 11.0
 * Created on 2025/01/01
 */
public class Tour {
    private String id;
    private String tourFile;
    private String title;
    private String description;
    private String nextTour;
    private LocalDateTime createdAt;
    private List<Step> steps;
    private transient VirtualFile virtualFile;

    public Tour() {
    }

    public Tour(String id, String tourFile, String title, String description,
                String nextTour, LocalDateTime createdAt, List<Step> steps) {
        this.id = id;
        this.tourFile = tourFile;
        this.title = title;
        this.description = description;
        this.nextTour = nextTour;
        this.createdAt = createdAt;
        this.steps = steps;
    }

    public String getId() {
        return id;
    }

    public Tour setId(String id) {
        this.id = id;
        return this;
    }

    public String getTourFile() {
        return tourFile;
    }

    public Tour setTourFile(String tourFile) {
        this.tourFile = tourFile;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public Tour setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Tour setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getNextTour() {
        return nextTour;
    }

    public Tour setNextTour(String nextTour) {
        this.nextTour = nextTour;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Tour setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    /**
     * 获取step的不可变的列表
     *
     * @return unmodifiableList
     */
    public List<Step> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public Tour setSteps(List<Step> steps) {
        this.steps = steps;
        linkStep();
        return this;
    }

    public VirtualFile getVirtualFile() {
        return virtualFile;
    }

    public Tour setVirtualFile(VirtualFile virtualFile) {
        this.virtualFile = virtualFile;
        return this;
    }

    public Tour linkStep() {
        this.steps = steps.stream().map(step -> step.setOwner(this)).collect(Collectors.toList());
        return this;
    }

    /**
     * 删除步骤
     *
     * @param index 索引
     * @return
     */
    public Tour removeStep(int index) {
        this.steps.remove(index);
        return this;
    }

    /**
     * 删除步骤
     *
     * @param step
     * @return
     */
    public Tour removeStep(Step step) {
        this.steps.remove(step.setOwner(null));
        return this;
    }

    /**
     * 添加步骤
     *
     * @param step
     * @return
     */
    public Tour addStep(Step step) {
        this.steps.add(step.setOwner(this));
        return this;
    }

    /**
     * 在指定位置插入一个step
     *
     * @param index 索引
     * @param step  新step
     * @return tour
     */
    public Tour addStep(int index, Step step) {
        this.steps.add(index, step.setOwner(this));
        return this;
    }

    /**
     * 更新为指定step
     *
     * @param index 索引
     * @param step  新step
     * @return tour
     */
    public Tour updateStep(int index, Step step) {
        this.steps.set(index, step.setOwner(this));
        return this;
    }

    /**
     * 获取指定index的步骤
     *
     * @param index 索引
     * @return 步骤
     */
    @Nullable
    public Step getStep(int index) {
        if (steps == null || index < 0 || index >= steps.size()) {
            return null;
        }
        return steps.get(index);
    }

    /**
     * 获取step的索引
     *
     * @return step的索引
     */
    public ArrayListMultimap<String, Integer> getStepIndexes() {
        ArrayListMultimap<String, Integer> multimap = ArrayListMultimap.create();
        steps.forEach(step -> multimap.put(step.getFile(), step.getLine()));
        return multimap;
    }

    /**
     * 获取当前步骤总数
     *
     * @return 当前步骤总数
     */
    public int getStepCount() {
        return this.steps.size();
    }


    @Override
    public String toString() {
        return title;
    }

    public static TourBuilder builder() {
        return new TourBuilder();
    }

    public static final class TourBuilder {
        private String id;
        private String tourFile;
        private String title;
        private String description;
        private String nextTour;
        private LocalDateTime createdAt;
        private List<Step> steps;
        private VirtualFile virtualFile;

        private TourBuilder() {
        }

        public TourBuilder id(String id) {
            this.id = id;
            return this;
        }

        public TourBuilder tourFile(String tourFile) {
            this.tourFile = tourFile;
            return this;
        }

        public TourBuilder title(String title) {
            this.title = title;
            return this;
        }

        public TourBuilder description(String description) {
            this.description = description;
            return this;
        }

        public TourBuilder nextTour(String nextTour) {
            this.nextTour = nextTour;
            return this;
        }

        public TourBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public TourBuilder steps(List<Step> steps) {
            this.steps = steps;
            return this;
        }

        public TourBuilder virtualFile(VirtualFile virtualFile) {
            this.virtualFile = virtualFile;
            return this;
        }

        public Tour build() {
            Tour tour = new Tour();
            tour.setId(id);
            tour.setTourFile(tourFile);
            tour.setTitle(title);
            tour.setDescription(description);
            tour.setNextTour(nextTour);
            tour.setCreatedAt(createdAt);
            if (steps != null) {
                tour.setSteps(steps);
            }
            tour.setVirtualFile(virtualFile);
            return tour;
        }
    }
}