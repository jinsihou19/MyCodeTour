package org.vito.mycodetour.tours.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import icons.Icons;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.domain.Tour;
import org.vito.mycodetour.tours.domain.TourFolder;

import javax.swing.BorderFactory;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义树节点渲染器
 */
public class TreeRenderer extends ColoredTreeCellRenderer {
    private String selectedTourId;
    private boolean isDragging = false;
    private Object draggedObject = null;
    private Object dropTarget = null;
    private boolean isDropAbove = false;  // 新增：表示是否拖放到目标上方
    private String searchText = "";
    private final Map<Object, String> highlightMap = new HashMap<>();

    public TreeRenderer(String selectedTourId) {
        this.selectedTourId = selectedTourId;
    }

    public void setSearchText(String searchText) {
        if (!StringUtils.equals(this.searchText, searchText)) {
            this.searchText = searchText;
            highlightMap.clear();
        }
    }

    public String getSearchText() {
        return searchText;
    }

    private String truncateText(String text, int matchStart, int matchLength, int contextLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 计算上下文范围
        int start = Math.max(0, matchStart - contextLength);
        int end = Math.min(text.length(), matchStart + matchLength + contextLength + 1);

        // 添加省略号
        StringBuilder result = new StringBuilder();
        if (start > 0) {
            result.append("...");
        }
        result.append(text, start, end);
        if (end < text.length()) {
            result.append("...");
        }

        return result.toString();
    }

    public boolean matchesSearch(Object userObject) {
        if (StringUtils.isEmpty(searchText)) {
            return true;
        }
        if (userObject instanceof Tour tour) {
            // 搜索文件名
            if (tour.getTourFile() != null &&
                    tour.getTourFile().toLowerCase().contains(searchText.toLowerCase())) {
                return true;
            }
            // 搜索标题
            if (tour.getTitle() != null &&
                    tour.getTitle().toLowerCase().contains(searchText.toLowerCase())) {
                return true;
            }
            // 搜索描述
            if (tour.getDescription() != null &&
                    tour.getDescription().toLowerCase().contains(searchText.toLowerCase())) {
                return true;
            }
            // 搜索步骤
            return tour.getSteps().stream().anyMatch(this::matchesSearch);
        } else if (userObject instanceof Step step) {
            // 搜索标题
            if (step.getTitle() != null &&
                    step.getTitle().toLowerCase().contains(searchText.toLowerCase())) {
                return true;
            }
            // 搜索描述
            if (step.getDescription() != null &&
                    step.getDescription().toLowerCase().contains(searchText.toLowerCase())) {
                return true;
            }
            // 搜索文件
            return step.getFile() != null &&
                    step.getFile().toLowerCase().contains(searchText.toLowerCase());
        }
        return false;
    }

    private void updateHighlightMap(Object userObject) {
        if (userObject instanceof Tour tour) {
            if (matchesSearch(tour)) {
                StringBuilder highlighted = new StringBuilder();
                String title = tour.getTitle();
                String tourFile = tour.getTourFile();
                String description = tour.getDescription();

                // 显示标题和文件名
                if (title != null) {
                    highlighted.append(title);
                }

                if (tourFile != null) {
                    highlighted.append(" (").append(tourFile).append(")");
                }

                // 处理描述内容
                if (description != null) {
                    String lowerDesc = description.toLowerCase();
                    String lowerSearch = searchText.toLowerCase();
                    int matchStart = lowerDesc.indexOf(lowerSearch);
                    if (matchStart >= 0) {
                        highlighted.append(" - ").append(truncateText(description, matchStart, searchText.length(), 10));
                    }
                }

                highlightMap.put(userObject, highlighted.toString());
            }
        } else if (userObject instanceof Step step) {
            if (matchesSearch(step)) {
                StringBuilder highlighted = new StringBuilder();
                String title = step.getTitle();
                String file = step.getFile();
                String description = step.getDescription();

                // 显示标题和文件名
                if (title != null) {
                    highlighted.append(title);
                }

                if (file != null) {
                    highlighted.append(" (").append(file).append(")");
                }

                // 处理描述内容
                if (description != null) {
                    String lowerDesc = description.toLowerCase();
                    String lowerSearch = searchText.toLowerCase();
                    int matchStart = lowerDesc.indexOf(lowerSearch);
                    if (matchStart >= 0) {
                        highlighted.append(" - ").append(truncateText(description, matchStart, searchText.length(), 10));
                    }
                }

                highlightMap.put(userObject, highlighted.toString());
            }
        }
    }

    private void appendWithHighlight(String text, String searchText, SimpleTextAttributes defaultAttr) {
        if (StringUtils.isEmpty(searchText)) {
            append(text, defaultAttr);
            return;
        }

        String lowerText = text.toLowerCase();
        String lowerSearch = searchText.toLowerCase();
        int lastEnd = 0;
        int start = lowerText.indexOf(lowerSearch);

        while (start >= 0) {
            // 添加匹配前的文本
            if (start > lastEnd) {
                append(text.substring(lastEnd, start), defaultAttr);
            }
            // 添加匹配的文本（IDEA风格高亮：黄色背景黑色文字）
            append(text.substring(start, start + searchText.length()),
                    new SimpleTextAttributes(SimpleTextAttributes.STYLE_SEARCH_MATCH, JBColor.BLACK));
            
            lastEnd = start + searchText.length();
            start = lowerText.indexOf(lowerSearch, lastEnd);
        }

        // 添加剩余的文本
        if (lastEnd < text.length()) {
            append(text.substring(lastEnd), defaultAttr);
        }
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            final Object userObject = node.getUserObject();

            // 更新高亮
            if (!StringUtils.isEmpty(searchText)) {
                updateHighlightMap(userObject);
            }

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
            String displayText = highlightMap.get(userObject);
            if (displayText != null && !StringUtils.isEmpty(searchText)) {
                // 检查是否包含描述部分（通过查找 " - " 分隔符）
                int descStart = displayText.indexOf(" - ");
                if (descStart >= 0) {
                    // 处理标题和文件名部分
                    String titleAndFile = displayText.substring(0, descStart);
                    appendWithHighlight(titleAndFile, searchText, SimpleTextAttributes.REGULAR_ATTRIBUTES);

                    // 处理描述部分
                    String description = displayText.substring(descStart);
                    appendWithHighlight(description, searchText, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                } else {
                    // 如果没有描述部分，直接处理整个文本
                    appendWithHighlight(displayText, searchText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }
            } else if (userObject instanceof TourFolder folder) {
                String displayName = folder.getDisplayName();
                if (displayName.startsWith("Tours ")) {
                    // 分离"Tours"和路径信息
                    String toursPart = "Tours";
                    String pathPart = displayName.substring(6);
                    // 添加"Tours"文本
                    append(toursPart, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    // 添加灰色路径信息
                    append(" " + pathPart, SimpleTextAttributes.GRAYED_ATTRIBUTES);
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