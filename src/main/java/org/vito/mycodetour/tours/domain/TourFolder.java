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
            if (projectPath != null && relativePath.startsWith(projectPath)) {
                relativePath = relativePath.substring(projectPath.length() + 1);
                if(relativePath.equals(Props.TOURS_DIR)) {
                    relativePath = "";
                }
            }
            
            // 使用 ~ 替换用户主目录路径
            String userHome = System.getProperty("user.home");
            if (relativePath.startsWith(userHome)) {
                relativePath = "~" + relativePath.substring(userHome.length());
            }
            
            return "Tours " + relativePath;
        }
        return getName();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
} 