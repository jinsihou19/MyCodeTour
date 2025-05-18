package org.vito.mycodetour.tours.domain;

import com.intellij.psi.PsiNameHelper;

import java.util.Objects;

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
    private transient Tour owner;

    public Step() {
    }

    public Step(String description, String file, Integer line, String title) {
        this.description = description;
        this.file = file;
        this.line = line;
        this.title = title;
    }

    public Step(String description, String file, Integer line, String title, Tour owner) {
        this.description = description;
        this.file = file;
        this.line = line;
        this.title = title;
        this.owner = owner;
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

    public Tour getOwner() {
        return owner;
    }

    public Step setOwner(Tour owner) {
        this.owner = owner;
        return this;
    }

    /**
     * 获取step的索引
     *
     * @return 索引
     */
    public int getStepIndex() {
        return owner.getSteps().indexOf(this);
    }

    public String reference() {
        return getFile() == null
                ? ""
                : getLine() != null
                ? String.format("%s:%s", getFile(), getLine())
                : getFile();
    }

    @Override
    public String toString() {
        return title;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Step step)) return false;
        return Objects.equals(title, step.title)
                && Objects.equals(description, step.description)
                && Objects.equals(file, step.file)
                && Objects.equals(line, step.line);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, description, file, line);
    }

    /**
     * 创建step
     *
     * @param reference 引用，可能是文件也可能是方法类引用
     * @return step
     */
    public static Step with(String reference) {

        return Step.builder()
                .title(PsiNameHelper.getShortClassName(reference))
                .description("Simple Navigation to " + reference)
                .file(reference)
                .build();
    }

    /**
     * 创建step
     *
     * @param reference 引用，可能是文件也可能是方法类引用
     * @param line      行数
     * @return step
     */
    public static Step with(String reference, int line) {
        final String title = String.format("%s:%s",
                PsiNameHelper.getShortClassName(reference.replace(".java", "")),
                line);
        return Step.builder()
                .title(title)
                .description("Simple Navigation to " + title)
                .file(reference)
                .line(line)
                .build();
    }

    /**
     * 建造器
     *
     * @return StepBuilder
     */
    public static StepBuilder builder() {
        return new StepBuilder();
    }

    public static final class StepBuilder {
        private String title;
        private String description;
        private String file;
        private Integer line;
        private Tour owner;

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

        public StepBuilder tour(Tour owner) {
            this.owner = owner;
            return this;
        }

        public Step build() {
            return new Step(description, file, line, title, owner);
        }
    }
}
