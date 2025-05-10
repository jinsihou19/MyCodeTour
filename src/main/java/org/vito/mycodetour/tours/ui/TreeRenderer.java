package org.vito.mycodetour.tours.ui;

import com.intellij.ui.render.LabelBasedRenderer;
import com.intellij.util.ui.UIUtil;
import icons.Icons;
import org.jetbrains.annotations.NotNull;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.domain.Tour;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * Handles the rendering of each tree item. It is currently used for defining icons
 *
 * @author vito
 * Created on 2025/1/1
 */
public class TreeRenderer extends LabelBasedRenderer.Tree {

    private String selectedTourId;
    private boolean isDragging = false;
    private Object draggedNode = null;
    private Object dropTarget = null;
    private boolean isDropAbove = false;  // 新增：表示是否拖放到目标上方

    public TreeRenderer(String selectedTourId) {
        this.selectedTourId = selectedTourId;
    }

    public void setDragging(boolean dragging, Object draggedNode) {
        this.isDragging = dragging;
        this.draggedNode = draggedNode;
    }

    public void setDropTarget(Object target, boolean above) {
        this.dropTarget = target;
        this.isDropAbove = above;
    }

    @Override
    public @NotNull Component getTreeCellRendererComponent(@NotNull JTree tree, Object value, boolean sel,
                                                           boolean expanded, boolean leaf,
                                                           int row, boolean hasFocus) {

        final Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        if (value instanceof DefaultMutableTreeNode) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            final Object userObject = node.getUserObject();

            // 设置图标
            if (userObject instanceof Tour) {
                final Tour tour = (Tour) userObject;
                if (tour.getId() != null && tour.getId().equals(selectedTourId))
                    setIcon(Icons.LOGO_12);
            } else if (userObject instanceof Step) {
                setIcon(Icons.STEP_12);
            }

            // 拖动效果
            if (isDragging) {
                if (userObject == draggedNode) {
                    // 被拖动的节点显示半透明
                    setForeground(new Color(128, 128, 128, 128));
                    setOpaque(true);
                } else if (userObject == dropTarget) {
                    // 目标位置显示高亮
                    setBackground(new Color(215, 0, 0, 50));
                    setOpaque(true);
                    // 设置粗体
                    Font currentFont = getFont();
                    setFont(currentFont.deriveFont(Font.BOLD));

                    // 添加横线指示器
                    if (userObject instanceof Step) {
                        Color selectionColor = UIUtil.getTreeSelectionBackground();
                        setBorder(BorderFactory.createCompoundBorder(
                                isDropAbove ? BorderFactory.createMatteBorder(2, 0, 0, 0, selectionColor) : BorderFactory.createMatteBorder(0, 0, 2, 0, selectionColor),
                                BorderFactory.createEmptyBorder(2, 0, 2, 0)
                        ));
                    }
                }
            } else {
                // 非拖动状态下重置样式
                setOpaque(false);
                setBackground(null);
                setForeground(null);
                // 重置字体
                Font currentFont = getFont();
                setFont(currentFont.deriveFont(Font.PLAIN));
                // 重置边框
                setBorder(null);
            }
        }

        return component;
    }

    public void setSelectedTourId(String selectedTourId) {
        this.selectedTourId = selectedTourId;
    }
}