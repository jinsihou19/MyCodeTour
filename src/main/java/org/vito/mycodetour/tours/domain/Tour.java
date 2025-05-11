package org.vito.mycodetour.tours.domain;

import com.google.common.collect.ArrayListMultimap;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;

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

    public List<Step> getSteps() {
        return steps;
    }

    public Tour setSteps(List<Step> steps) {
        this.steps = steps;
        return this;
    }

    public VirtualFile getVirtualFile() {
        return virtualFile;
    }

    public Tour setVirtualFile(VirtualFile virtualFile) {
        this.virtualFile = virtualFile;
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
        if(steps == null || index < 0 || index >= steps.size()) {
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
            tour.setSteps(steps);
            tour.setVirtualFile(virtualFile);
            return tour;
        }
    }
}