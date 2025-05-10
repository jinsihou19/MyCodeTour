package org.vito.mycodetour.tours.domain;

/**
 * 步骤
 *
 * @author vito
 * @since 11.0
 * Created on 2025/01/01
 */
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

    public String getTitle() {
        return title;
    }

    public Step setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Step setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getFile() {
        return file;
    }

    public Step setFile(String file) {
        this.file = file;
        return this;
    }

    public Integer getLine() {
        return line;
    }

    public Step setLine(Integer line) {
        this.line = line;
        return this;
    }

    public String getDirectory() {
        return directory;
    }

    public Step setDirectory(String directory) {
        this.directory = directory;
        return this;
    }

    public String getUri() {
        return uri;
    }

    public Step setUri(String uri) {
        this.uri = uri;
        return this;
    }

    public String getPattern() {
        return pattern;
    }

    public Step setPattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    @Override
    public String toString() {
        return title;
    }

    public static StepBuilder builder() {
        return new StepBuilder();
    }

    public static final class StepBuilder {
        private String title;
        private String description;
        private String file;
        private Integer line;
        private String directory;
        private String uri;
        private String pattern;

        private StepBuilder() {
        }

        public StepBuilder title(String title) {
            this.title = title;
            return this;
        }

        public StepBuilder description(String description) {
            this.description = description;
            return this;
        }

        public StepBuilder file(String file) {
            this.file = file;
            return this;
        }

        public StepBuilder line(Integer line) {
            this.line = line;
            return this;
        }

        public StepBuilder directory(String directory) {
            this.directory = directory;
            return this;
        }

        public StepBuilder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public StepBuilder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Step build() {
            return new Step(description, file, directory, uri, line, pattern, title);
        }
    }
}
