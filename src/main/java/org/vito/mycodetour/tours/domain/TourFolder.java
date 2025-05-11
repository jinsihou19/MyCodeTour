package org.vito.mycodetour.tours.domain;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * 表示tour文件夹的类
 */
public class TourFolder {
    private final VirtualFile virtualFile;
    private final Project project;

    public TourFolder(@NotNull VirtualFile virtualFile, @NotNull Project project) {
        this.virtualFile = virtualFile;
        this.project = project;
    }

    public VirtualFile getVirtualFile() {
        return virtualFile;
    }

    public String getName() {
        return virtualFile.getName();
    }

    /**
     * 获取文件夹的显示名称
     * 如果是.tours文件夹，显示为"Tours"加上简化的路径信息
     * 否则显示原始名称
     */
    public String getDisplayName() {

        if (getName().equals(Props.TOURS_DIR)) {

            // 如果是.tours文件夹，返回相对路径
            String relativePath = virtualFile.getPath();
            String projectPath = project.getBasePath();
            if (relativePath.startsWith(projectPath)) {
                relativePath = relativePath.substring(projectPath.length() + 1);
            }
            // 如果路径超过两级，简化显示
            String[] parts = relativePath.split("/");
            if (parts.length > 2) {
                relativePath = parts[0] + "/.../" + parts[parts.length - 1];
            }
            return "Tours (" + relativePath + ")";
        }
        return getName();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
} 