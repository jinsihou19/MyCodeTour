package org.vito.mycodetour.tours.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import icons.Icons;
import org.jetbrains.annotations.NotNull;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.domain.Tour;
import org.vito.mycodetour.tours.domain.TourFolder;

import javax.swing.BorderFactory;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.Color;
import java.awt.Font;

/**
 * 自定义树节点渲染器
 */
public class TreeRenderer extends ColoredTreeCellRenderer {
    private String selectedTourId;
    private boolean isDragging = false;
    private Object draggedObject = null;
    private Object dropTarget = null;
    private boolean isDropAbove = false;  // 新增：表示是否拖放到目标上方

    public TreeRenderer(String selectedTourId) {
        this.selectedTourId = selectedTourId;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            final Object userObject = node.getUserObject();

            // 设置图标
            if (userObject instanceof Tour tour) {
                if (tour.getId() != null && tour.getId().equals(selectedTourId))
                    setIcon(Icons.TOUR_16);
                else
                    setIcon(Icons.TOUR_OPEN_16);
            } else if (userObject instanceof Step) {
                setIcon(Icons.STEP_12);
            } else if (userObject instanceof TourFolder) {
                setIcon(AllIcons.Nodes.Folder);
            }

            // 设置文本
            if (userObject instanceof TourFolder folder) {
                String displayName = folder.getDisplayName();
                if (displayName.startsWith("Tours (")) {
                    // 分离"Tours"和路径信息
                    String toursPart = "Tours";
                    String pathPart = displayName.substring(7, displayName.length() - 1);
                    // 添加"Tours"文本
                    append(toursPart, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    // 添加灰色路径信息
                    append(" (" + pathPart + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                } else {
                    append(displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }
            } else if (userObject instanceof Tour tour) {
                append(tour.getTitle(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            } else if (userObject instanceof Step step) {
                append(step.getTitle(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            } else if (userObject instanceof String) {
                append(userObject.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }

            // 拖动效果
            if (isDragging) {
                if (userObject.equals(draggedObject)) {
                    // 被拖动的节点显示半透明
                    setForeground(new Color(128, 128, 128, 128));
                    setOpaque(true);
                } else if (userObject.equals(dropTarget)) {
                    // 目标位置显示高亮
                    setBackground(new Color(215, 0, 0, 50));
                    setOpaque(true);
                    // 设置粗体
                    Font currentFont = getFont();
                    setFont(currentFont.deriveFont(Font.BOLD));

                    // 添加横线指示器
                    if (userObject instanceof Step) {
                        Color selectionColor = UIUtil.getTreeSelectionBackground(true);
                        setBorder(BorderFactory.createCompoundBorder(
                                isDropAbove
                                        ? BorderFactory.createMatteBorder(2, 0, 0, 0, selectionColor)
                                        : BorderFactory.createMatteBorder(0, 0, 2, 0, selectionColor),
                                BorderFactory.createEmptyBorder(2, 0, 2, 0)));
                    } else if (userObject instanceof Tour) {
                        setFont(currentFont.deriveFont(UIUtil.getFontSize(UIUtil.FontSize.NORMAL) + 1));
                    }
                } else {
                    resetStyle();
                }
            } else {
                resetStyle();
            }
        }
    }

    private void resetStyle() {
        // 非拖动状态下重置样式
        setOpaque(false);
        setBackground(null);
        setForeground(null);
        // 重置字体
        Font currentFont = getFont();
        setFont(currentFont.deriveFont(Font.PLAIN));
        setFont(currentFont.deriveFont(UIUtil.getFontSize(UIUtil.FontSize.NORMAL)));
        // 重置边框
        setBorder(null);
    }

    public void setSelectedTourId(String selectedTourId) {
        this.selectedTourId = selectedTourId;
    }

    public void setDragging(boolean dragging, Object draggedObject) {
        this.isDragging = dragging;
        this.draggedObject = draggedObject;
    }

    public void setDropTarget(Object dropTarget, boolean isAbove) {
        this.dropTarget = dropTarget;
        this.isDropAbove = isAbove;
    }
}