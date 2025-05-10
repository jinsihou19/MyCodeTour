package org.vito.mycodetour.tours.domain;

import com.google.common.collect.ArrayListMultimap;
import com.intellij.openapi.vfs.VirtualFile;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 指南
 *
 * @author vito
 * @since 11.0
 * Created on 2025/01/01
 */
@Data
public class Tour {
    private String id;
    private String tourFile; // The file that this tour would be persisted
    private String title; // The title of the Tour (visible on the tree)
    private String description; // Description (visible on hover as tooltip)
    private String nextTour;
    private LocalDateTime createdAt;
    private List<Step> steps;
    private transient VirtualFile virtualFile;

    public Tour() {
    }

    @Builder
    public Tour(String id, String touFile, String title, String description,
                String nextTour, LocalDateTime createdAt, List<Step> steps) {
        this.id = id;
        this.tourFile = touFile;
        this.title = title;
        this.description = description;
        this.nextTour = nextTour;
        this.createdAt = createdAt;
        this.steps = steps;
    }

    /**
     * 获取指定index的步骤
     *
     * @param index 索引
     * @return 步骤
     */
    public Step getStep(int index) {
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
}