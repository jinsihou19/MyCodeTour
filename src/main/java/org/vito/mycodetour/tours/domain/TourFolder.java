package org.vito.mycodetour.tours.domain;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * 表示tour文件夹的类
 */
public class TourFolder {
    private String name;
    private VirtualFile virtualFile;

    public TourFolder(@NotNull String name, @NotNull VirtualFile virtualFile) {
        this.name = name;
        this.virtualFile = virtualFile;
    }

    public String getName() {
        return name;
    }

    public VirtualFile getVirtualFile() {
        return virtualFile;
    }

    @Override
    public String toString() {
        return name;
    }
} 