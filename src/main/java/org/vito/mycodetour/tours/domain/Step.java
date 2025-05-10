package org.vito.mycodetour.tours.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 步骤
 *
 * @author vito
 * @since 11.0
 * Created on 2025/01/01
 */
@Data
public class Step {
    private String title;
    private String description;
    private String file;
    private Integer line;
    private String directory;
    private String uri;
    private String pattern;

    public Step() {
    }

    @Builder
    public Step(String description, String file, String directory, String uri, Integer line, String pattern,
                String title) {
        this.description = description;
        this.file = file;
        this.directory = directory;
        this.uri = uri;
        this.line = line;
        this.pattern = pattern;
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }

}