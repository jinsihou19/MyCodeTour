package org.vito.mycodetour.tours.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.SlowOperations;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vito.mycodetour.tours.domain.Props;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.domain.Tour;
import org.vito.mycodetour.tours.domain.TourFolder;
import org.vito.mycodetour.tours.service.AppSettingsState;
import org.vito.mycodetour.tours.service.Navigator;
import org.vito.mycodetour.tours.service.StepRendererPane;
import org.vito.mycodetour.tours.service.TourValidator;
import org.vito.mycodetour.tours.service.Utils;
import org.vito.mycodetour.tours.state.StateManager;
import org.vito.mycodetour.tours.state.StepSelectionNotifier;
import org.vito.mycodetour.tours.state.TourUpdateNotifier;
import org.vito.mycodetour.tours.state.ToursState;
import org.vito.mycodetour.tours.state.Validator;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Code Tour - Tool Window (Tours Navigation and Management).
 * Contains the editable tree representation of all the available Tours and provides
 * easy single-click navigation on Steps and Prev/Next buttons
 *
 * @author vito
 * Created on 2025/1/1
 */
public class ToolPaneWindow {

    private static final String ID = "Tours Navigation";
    private static final Logger LOG = Logger.getInstance(ToolPaneWindow.class);
    private static final String TREE_TITLE = "Code Tours";

    private final JPanel content;
    private final OnePixelSplitter splitter;
    private Tree toursTree;
    private DefaultTreeModel treeModel;

    private final ToolWindow toolWindow;
    private final Project project;

    public ToolPaneWindow(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        this.toolWindow = toolWindow;
        this.project = project;

        content = new JPanel(new BorderLayout());
        splitter = new OnePixelSplitter(true, 0.3f);
        content.add(splitter, BorderLayout.CENTER);

        createToursTree();

        registerMessageBusListener();

        updateToursTree();
    }

    public JPanel getContent() {
        return content;
    }

    /**
     * Handle plugin messaging
     */
    public void registerMessageBusListener() {

        project.getMessageBus().connect().subscribe(
                TourUpdateNotifier.TOPIC,
                (TourUpdateNotifier) (tour) ->
                        ApplicationManager.getApplication().invokeLater(() -> {
                            updateToursTree();
                            renderActiveTourStepContent(tour);
                        }));

        project.getMessageBus().connect().subscribe(
                StepSelectionNotifier.TOPIC,
                (StepSelectionNotifier) (step) -> {
                    if (!toolWindow.isVisible()) {
                        toolWindow.show();
                    }
                    selectTourStep(step);
                });
    }

    private void renderActiveTourStepContent(Tour tour) {
        ToursState state = StateManager.getInstance().getState(project);
        if (tour != null && state.getActiveStepIndex() != -1) {
            Step step = tour.getStep(state.getActiveStepIndex());
            if (step != null) {
                createOrUpdateContent(step, project);
            }
        }
    }

    private void createToursTree() {
        final String activeId = StateManager.getInstance().getActiveTour(project).map(Tour::getId).orElse("Null");

        treeModel = new DefaultTreeModel(new DefaultMutableTreeNode(TREE_TITLE), false);
        toursTree = new Tree(treeModel);
        toursTree.setCellRenderer(new TreeRenderer(activeId));

        toursTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                final int selectedRow = toursTree.getRowForLocation(e.getX(), e.getY());
                final TreePath pathSelected = toursTree.getPathForLocation(e.getX(), e.getY());

                if (selectedRow < 0 || pathSelected == null) {
                    // 在空白处点击时显示上下文菜单
                    if (e.getButton() == MouseEvent.BUTTON3) {
                        final JBPopupMenu menu = new JBPopupMenu("Tree Context Menu");
                        
                        // Reload Action
                        final JMenuItem reloadAction = new JMenuItem("Reload", AllIcons.Actions.Refresh);
                        reloadAction.addActionListener(d -> {
                            LOG.info("Re-creating the tree");
                            reloadToursState();
                        });
                        
                        menu.add(reloadAction);
                        menu.show(toursTree, e.getX(), e.getY());
                    }
                    return;
                }

                if (!(pathSelected.getLastPathComponent() instanceof DefaultMutableTreeNode)) return;
                final DefaultMutableTreeNode node = (DefaultMutableTreeNode) pathSelected.getLastPathComponent();
                if (node.getUserObject() instanceof String && TREE_TITLE.equals(node.getUserObject().toString())) {
                    rootClickListener(e);
                    return;
                }
                if (node.getUserObject() instanceof Tour) {
                    tourClickListener(e, node);
                    return;
                }
                if (node.getUserObject() instanceof Step) {
                    stepClickListener(e, node, project);
                    return;
                }
                if (node.getUserObject() instanceof TourFolder) {
                    folderClickListener(e, node);
                }
            }
        });


        // 注册拖放源
        DnDManager dndManager = DnDManager.getInstance();
        dndManager.registerSource(new DnDSource() {
            @Override
            public boolean canStartDragging(DnDAction action, Point point) {
                TreePath path = toursTree.getPathForLocation(point.x, point.y);
                if (path == null) return false;

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                // 只允许拖动Step节点
                if (node.getUserObject() instanceof Step) {
                    // 设置拖动状态
                    if (toursTree.getCellRenderer() instanceof TreeRenderer renderer) {
                        renderer.setDragging(true, node.getUserObject());
                        toursTree.repaint();
                    }
                    return true;
                }
                return false;
            }

            @Override
            public DnDDragStartBean startDragging(DnDAction action, Point point) {
                TreePath path = toursTree.getPathForLocation(point.x, point.y);
                if (path != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (node.getUserObject() instanceof Step) {
                        return new DnDDragStartBean(node);
                    }
                }
                return null;
            }

            @Override
            public void dragDropEnd() {
                // 清除拖动状态
                if (toursTree.getCellRenderer() instanceof TreeRenderer renderer) {
                    renderer.setDragging(false, null);
                    renderer.setDropTarget(null);
                    toursTree.repaint();
                }
                // 拖动结束后刷新树
                updateToursTree();
            }
        }, toursTree);

        // 注册拖放目标
        dndManager.registerTarget(new DnDTarget() {
            @Override
            public boolean update(DnDEvent event) {
                Object attachedObject = event.getAttachedObject();
                if (!(attachedObject instanceof DefaultMutableTreeNode)) {
                    return false;
                }

                DefaultMutableTreeNode sourceNode = (DefaultMutableTreeNode) attachedObject;
                if (!(sourceNode.getUserObject() instanceof Step)) {
                    return false;
                }

                TreePath targetPath = toursTree.getPathForLocation(event.getPoint().x, event.getPoint().y);
                if (targetPath == null) {
                    return false;
                }

                DefaultMutableTreeNode targetNode = (DefaultMutableTreeNode) targetPath.getLastPathComponent();

                // 只允许拖放到Tour节点或Step节点上，但不允许拖放到Demo
                if (targetNode.getUserObject() instanceof Tour targetTour) {
                    if (Validator.isDemo(targetTour)) {
                        event.setDropPossible(false, "Cannot drop into Demo");
                        return false;
                    }
                    // 更新目标位置的高亮
                    if (toursTree.getCellRenderer() instanceof TreeRenderer renderer) {
                        renderer.setDropTarget(targetNode.getUserObject(), false);
                        toursTree.repaint();
                    }
                    event.setDropPossible(true, "Drop here to move step");
                    return true;
                } else if (targetNode.getUserObject() instanceof Step) {
                    // 检查父节点是否是Demo
                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) targetNode.getParent();
                    if (parentNode.getUserObject() instanceof Tour parentTour) {
                        if (Validator.isDemo(parentTour)) {
                            event.setDropPossible(false, "Cannot drop into Demo");
                            return false;
                        }
                    }

                    // 计算是否拖放到目标上方
                    Point dropPoint = event.getPoint();
                    Rectangle bounds = toursTree.getPathBounds(targetPath);
                    boolean isAbove = dropPoint.y < (bounds.y + bounds.height / 2);

                    // 更新目标位置的高亮
                    if (toursTree.getCellRenderer() instanceof TreeRenderer renderer) {
                        renderer.setDropTarget(targetNode.getUserObject(), isAbove);
                        toursTree.repaint();
                    }
                    event.setDropPossible(true, "Drop here to move step");
                    return true;
                }

                return false;
            }

            @Override
            public void drop(DnDEvent event) {
                // 清除拖动状态
                if (toursTree.getCellRenderer() instanceof TreeRenderer renderer) {
                    renderer.setDragging(false, null);
                    renderer.setDropTarget(null);
                    toursTree.repaint();
                }

                Object attachedObject = event.getAttachedObject();
                if (!(attachedObject instanceof DefaultMutableTreeNode)) {
                    return;
                }

                DefaultMutableTreeNode draggedNode = (DefaultMutableTreeNode) attachedObject;
                if (!(draggedNode.getUserObject() instanceof Step)) {
                    return;
                }

                TreePath targetPath = toursTree.getPathForLocation(event.getPoint().x, event.getPoint().y);
                if (targetPath == null) {
                    return;
                }

                DefaultMutableTreeNode targetNode = (DefaultMutableTreeNode) targetPath.getLastPathComponent();
                Step draggedStep = (Step) draggedNode.getUserObject();

                // 获取源Tour
                DefaultMutableTreeNode sourceParent = (DefaultMutableTreeNode) draggedNode.getParent();
                Tour sourceTour = (Tour) sourceParent.getUserObject();

                // 获取目标位置
                if (targetNode.getUserObject() instanceof Tour) {
                    // 如果目标是Tour，添加到Tour的最后
                    Tour targetTour = (Tour) targetNode.getUserObject();
                    if (targetTour != sourceTour) {
                        // 如果是不同的Tour，需要移动Step
                        sourceTour.removeStep(draggedStep);
                        targetTour.addStep(draggedStep);
                        StateManager.getInstance().getState(project).updateTour(sourceTour);
                        StateManager.getInstance().getState(project).updateTour(targetTour);
                    }
                } else if (targetNode.getUserObject() instanceof Step) {
                    // 如果目标是Step，插入到该Step之前或之后
                    Step targetStep = (Step) targetNode.getUserObject();
                    DefaultMutableTreeNode targetParent = (DefaultMutableTreeNode) targetNode.getParent();
                    Tour targetTour = (Tour) targetParent.getUserObject();

                    // 计算是否拖放到目标上方
                    Point dropPoint = event.getPoint();
                    Rectangle bounds = toursTree.getPathBounds(targetPath);
                    boolean isAbove = dropPoint.y < (bounds.y + bounds.height / 2);

                    int targetIndex = targetTour.getSteps().indexOf(targetStep);
                    // 先移除原step
                    int oldIndex = sourceTour.getSteps().indexOf(draggedStep);
                    sourceTour.removeStep(draggedStep);
                    // 如果是同一个tour并且原index < 目标index，插入点要-1
                    if (sourceTour == targetTour && oldIndex < targetIndex) {
                        targetIndex--;
                    }
                    // 如果拖放到下方，则插入到目标Step之后
                    if (!isAbove) {
                        targetIndex++;
                    }
                    targetTour.addStep(targetIndex, draggedStep);

                    StateManager.getInstance().getState(project).updateTour(sourceTour);
                    StateManager.getInstance().getState(project).updateTour(targetTour);
                }

                // 通知UI更新
                project.getMessageBus().syncPublisher(TourUpdateNotifier.TOPIC).tourUpdated(sourceTour);
                if (targetNode.getUserObject() instanceof Tour) {
                    project.getMessageBus().syncPublisher(TourUpdateNotifier.TOPIC).tourUpdated((Tour) targetNode.getUserObject());
                }
            }
        }, toursTree);


        final JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.setName("treePanel");
        final JBScrollPane scrollPane = new JBScrollPane(toursTree);
        treePanel.add(scrollPane, BorderLayout.CENTER);
        for (int i = 0; i < splitter.getComponentCount(); i++) {
            if ("treePanel".equals(splitter.getComponent(i).getName())) {
                splitter.remove(i);
                break;
            }
        }
        splitter.setFirstComponent(treePanel);
    }

    /**
     * 更新指南树数据
     */
    public void updateToursTree() {
        // 保存当前展开的节点路径
        Set<String> expandedNodePaths = new HashSet<>();
        for (int i = 0; i < toursTree.getRowCount(); i++) {
            TreePath path = toursTree.getPathForRow(i);
            if (toursTree.isExpanded(path)) {
                StringBuilder nodePath = new StringBuilder();
                for (int j = 0; j < path.getPathCount(); j++) {
                    Object component = path.getPathComponent(j);
                    if (component instanceof DefaultMutableTreeNode) {
                        Object userObject = ((DefaultMutableTreeNode) component).getUserObject();
                        if (userObject instanceof Tour) {
                            nodePath.append(((Tour) userObject).getVirtualFile().getPath());
                        } else if (userObject instanceof TourFolder) {
                            nodePath.append(((TourFolder) userObject).getVirtualFile().getPath());
                        } else if (j == 0 && TREE_TITLE.equals(userObject)) {
                            nodePath.append(TREE_TITLE);
                        }
                        if (j < path.getPathCount() - 1) {
                            nodePath.append("/");
                        }
                    }
                }
                if (!nodePath.isEmpty()) {
                    expandedNodePaths.add(nodePath.toString());
                }
            }
        }

        final ToursState state = StateManager.getInstance().getState(project);
        final DefaultMutableTreeNode root = new DefaultMutableTreeNode(TREE_TITLE);

        // 1. 获取所有文件夹
        List<TourFolder> allFolders = state.getFolders();

        // 2. 统计.tours文件夹的数量和找到唯一的.tours文件夹
        List<TourFolder> toursDirs = allFolders.stream()
                .filter(folder -> folder.getVirtualFile().getName().equals(Props.TOURS_DIR))
                .toList();
        boolean hasSingleToursDir = toursDirs.size() == 1;
        TourFolder singleToursDir = hasSingleToursDir ? toursDirs.get(0) : null;

        // 3. 创建文件夹节点映射，用于快速查找父节点
        Map<String, DefaultMutableTreeNode> folderNodes = new HashMap<>();

        // 4. 按层级关系创建文件夹节点
        for (TourFolder folder : allFolders) {
            VirtualFile folderFile = folder.getVirtualFile();
            String folderPath = folderFile.getPath();
            VirtualFile parentDir = folderFile.getParent();

            // 如果是唯一的.tours文件夹，跳过它
            if (hasSingleToursDir && folder.equals(singleToursDir)) {
                continue;
            }

            // 创建当前文件夹节点
            DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(folder);

            // 找到父节点并添加当前节点
            if (parentDir != null) {
                String parentPath = parentDir.getPath();
                DefaultMutableTreeNode parentNode = folderNodes.get(parentPath);

                // 如果有父节点，添加到父节点下
                // 如果找不到父节点，说明是根目录下的文件夹
                if (hasSingleToursDir && parentDir.equals(singleToursDir.getVirtualFile())) {
                    // 如果父目录是唯一的.tours文件夹，直接添加到根节点
                    root.add(folderNode);
                } else Objects.requireNonNullElse(parentNode, root).add(folderNode);
            } else {
                // 如果没有父目录，说明是根目录
                root.add(folderNode);
            }

            // 将当前节点添加到映射中
            folderNodes.put(folderPath, folderNode);
        }

        // 5. 获取所有tour并按文件夹分组
        Map<TourFolder, List<Tour>> folderToursMap = new HashMap<>();
        for (Tour tour : state.getTours()) {
            // 如果是demo tour，直接添加到根节点
            if (Validator.isDemo(tour)) {
                DefaultMutableTreeNode tourNode = new DefaultMutableTreeNode(tour);
                tour.getSteps().forEach(step -> tourNode.add(new DefaultMutableTreeNode(step)));
                root.insert(tourNode, 0);
                continue;
            }

            VirtualFile tourFile = tour.getVirtualFile();
            if (tourFile != null) {
                VirtualFile parentDir = tourFile.getParent();
                if (parentDir != null) {
                    // 如果父目录是唯一的.tours文件夹，直接添加到根节点
                    if (hasSingleToursDir && parentDir.equals(singleToursDir.getVirtualFile())) {
                        DefaultMutableTreeNode tourNode = new DefaultMutableTreeNode(tour);
                        tour.getSteps().forEach(step -> tourNode.add(new DefaultMutableTreeNode(step)));
                        root.add(tourNode);
                        continue;
                    }

                    allFolders.stream()
                            .filter(f -> f.getVirtualFile().equals(parentDir))
                            .findFirst().ifPresent(parentFolder ->
                                    folderToursMap.computeIfAbsent(parentFolder, k -> new ArrayList<>()).add(tour));

                }
            }
        }

        // 6. 将tour添加到对应的文件夹节点下
        for (Map.Entry<TourFolder, List<Tour>> entry : folderToursMap.entrySet()) {
            TourFolder folder = entry.getKey();
            List<Tour> folderTours = entry.getValue();

            DefaultMutableTreeNode folderNode = folderNodes.get(folder.getVirtualFile().getPath());
            if (folderNode != null) {
                for (Tour tour : folderTours) {
                    DefaultMutableTreeNode tourNode = new DefaultMutableTreeNode(tour);
                    tour.getSteps().forEach(step -> tourNode.add(new DefaultMutableTreeNode(step)));
                    folderNode.add(tourNode);
                }
            }
        }

        toursTree.setRootVisible(toursDirs.size() <= 1);
        treeModel.setRoot(root);
        treeModel.reload();

        // 恢复展开状态
        restoreExpandedState(root, expandedNodePaths, "");
    }

    private void restoreExpandedState(DefaultMutableTreeNode node, Set<String> expandedPaths, String currentPath) {
        Object userObject = node.getUserObject();
        String nodePath;

        if (userObject instanceof Tour tour) {
            if (Validator.isDemo(tour)) {
                nodePath = currentPath + tour.getTitle();
            } else {
                nodePath = currentPath + ((Tour) userObject).getVirtualFile().getPath();
            }

        } else if (userObject instanceof TourFolder) {
            nodePath = currentPath + ((TourFolder) userObject).getVirtualFile().getPath();
        } else if (TREE_TITLE.equals(userObject)) {
            nodePath = TREE_TITLE;
        } else {
            return;
        }

        if (expandedPaths.contains(nodePath)) {
            toursTree.expandPath(new TreePath(node.getPath()));
        }

        // 递归处理子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            restoreExpandedState(child, expandedPaths, nodePath + "/");
        }
    }

    private void createOrUpdateContent(@NotNull Step step, @NotNull Project project) {
        StepRendererPane pane = new StepRendererPane(step, project);
        splitter.setSecondComponent(pane);
    }

    private void createNavigationButtons() {
        final JButton previousButton = new JButton("Previous Step");
        previousButton.setToolTipText("Navigate to the Previous Step of the active Tour (Ctrl+Alt+Q)");
        previousButton.addActionListener(e -> {
            LOG.info("Previous button pressed!");

            // Navigate to the previous Step if exist
            StateManager.getInstance().getActiveTour(project)
                    .flatMap(tour -> StateManager.getInstance().getState(project).getPrevStep())
                    .ifPresent(this::selectTourStep);

        });

        final JButton nextButton = new JButton("Next Step");
        nextButton.setToolTipText("Navigate to the Next Step of the active Tour (Ctrl+Alt+W)");
        nextButton.addActionListener(e -> {
            LOG.info("Next button pressed!");

            // Navigate to the next Step if exist
            StateManager.getInstance().getActiveTour(project)
                    .flatMap(tour -> StateManager.getInstance().getState(project).getNextStep())
                    .ifPresent(this::selectTourStep);
        });

        final JButton reloadButton = new JButton("Reload");
        reloadButton.setToolTipText("Reload the tours from the related files");
        reloadButton.addActionListener(e -> {
            LOG.info("Re-creating the tree");
            reloadToursState();
        });

        final JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(previousButton);
        buttonsPanel.add(nextButton);
        buttonsPanel.add(reloadButton);
        content.add(buttonsPanel, BorderLayout.SOUTH);
    }

    //region Tree Nodes listeners
    private void rootClickListener(MouseEvent e) {
        // Create new Tour option
        if (e.getButton() == MouseEvent.BUTTON3) {
            final JBPopupMenu menu = new JBPopupMenu("Tour Context Menu");

            // Create new Tour
            final JMenuItem newTourAction = new JMenuItem("Create New Tour", AllIcons.Actions.AddFile);
            newTourAction.addActionListener(d -> createNewTourListener());

            // Create new Folder
            final JMenuItem newFolderAction = new JMenuItem("Create New Folder", AllIcons.Actions.NewFolder);
            newFolderAction.addActionListener(d -> createNewFolderListener(null));

            // Enable/Disable Demo
            final String title = String.format("%s Demo",
                    AppSettingsState.getInstance().isOnboardingAssistantOn() ? "Disable" : "Enable");
            final Icon icon = AppSettingsState.getInstance().isOnboardingAssistantOn()
                    ? AllIcons.Actions.IntentionBulbGrey
                    : AllIcons.Actions.IntentionBulb;
            final JMenuItem toggleOnboardTourAction = new JMenuItem(title, icon);
            toggleOnboardTourAction.addActionListener(d -> {
                AppSettingsState.getInstance().toggleOnboardingAssistant();
                reloadToursState();
            });

            Arrays.asList(newTourAction, newFolderAction, toggleOnboardTourAction).forEach(menu::add);
            menu.show(toursTree, e.getX(), e.getY());
        }
    }

    private void tourClickListener(MouseEvent e, DefaultMutableTreeNode node) {
        final Tour tour = (Tour) node.getUserObject();
        // On Tour right click, show a context menu (Delete, Edit)
        if (e.getButton() == MouseEvent.BUTTON3) {
            final JBPopupMenu menu = new JBPopupMenu("Tour Context Menu");

            // Add new Step
            final JMenuItem newStepAction = new JMenuItem("Add new Step", AllIcons.Actions.AddFile);
            newStepAction.addActionListener(d -> addNewStepOnTourListener(tour));

            // Edit Action
            final JMenuItem editAction = new JMenuItem("Edit Tour", AllIcons.Actions.Edit);
            editAction.addActionListener(d -> editTourListener(tour));

            // Jump to Source Action
            final JMenuItem jumpToSourceAction = new JMenuItem("Jump to .tour Source", AllIcons.Actions.EditSource);
            jumpToSourceAction.addActionListener(d -> jumpToSourceTourListener(tour));

            // Delete Action
            final JMenuItem deleteAction = new JMenuItem("Delete Tour", AllIcons.Actions.DeleteTag);
            deleteAction.addActionListener(d -> deleteTourListener(tour));

            if (Validator.isDemo(tour)) {
                // Disable Demo Action
                final JMenuItem disableOnboardAssistantAction = new JMenuItem("Disable Demo",
                        AllIcons.Actions.IntentionBulbGrey);
                disableOnboardAssistantAction.addActionListener(d -> {
                    AppSettingsState.getInstance().setOnboardingAssistant(false);
                    reloadToursState();
                });
                menu.add(disableOnboardAssistantAction);

                Arrays.asList(newStepAction, editAction, jumpToSourceAction, deleteAction)
                        .forEach(item -> item.setEnabled(false));
            }

            Arrays.asList(newStepAction, editAction, jumpToSourceAction, deleteAction).forEach(menu::add);
            menu.show(toursTree, e.getX(), e.getY());
        } else {
            if (e.getClickCount() == 2) {
                updateActiveTour(tour);
                StateManager.getInstance().resetActiveStepIndex(project);
                toursTree.repaint();
            }
        }
    }

    private void stepClickListener(MouseEvent e, DefaultMutableTreeNode node, Project project) {
        final Step step = (Step) node.getUserObject();
        final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
        final Tour tour = (Tour) parentNode.getUserObject();
        updateActiveTour(tour);

        if (e.getButton() == MouseEvent.BUTTON3) {
            final JBPopupMenu menu = new JBPopupMenu("Tour Context Menu");

            // Edit Step Action
            final JMenuItem editDescriptionAction = new JMenuItem("Edit Step", AllIcons.Actions.EditScheme);
            editDescriptionAction.addActionListener(d -> editStepListener(step, tour));

            // Move up Step
            final JMenuItem moveUpAction = new JMenuItem("Move Up", AllIcons.Actions.MoveUp);
            moveUpAction.addActionListener(d -> moveListener(step, tour, true));
            moveUpAction.setEnabled(node.getPreviousSibling() != null);

            // Move down Step
            final JMenuItem moveDownAction = new JMenuItem("Move Down", AllIcons.Actions.MoveDown);
            moveDownAction.addActionListener(d -> moveListener(step, tour, false));
            moveDownAction.setEnabled(node.getNextSibling() != null);

            // Delete Action
            final JMenuItem deleteAction = new JMenuItem("Delete Step", AllIcons.Actions.DeleteTag);
            deleteAction.addActionListener(d -> deleteStepListener(step, tour));

            if (Validator.isDemo(tour)) {
                Arrays.asList(editDescriptionAction, moveUpAction, moveDownAction, deleteAction)
                        .forEach(item -> item.setEnabled(false));
            }

            Arrays.asList(editDescriptionAction, moveUpAction, moveDownAction, deleteAction)
                    .forEach(menu::add);
            menu.show(toursTree, e.getX(), e.getY());
            return;
        }

        final int index = parentNode.getIndex(node);
        if (index >= 0)
            StateManager.getInstance().getState(project).setActiveStepIndex(index);
        createOrUpdateContent(step, project);
        Navigator.navigateLine(step, project);
    }

    private void folderClickListener(MouseEvent e, DefaultMutableTreeNode node) {
        if (e.getButton() == MouseEvent.BUTTON3) {
            final JBPopupMenu menu = new JBPopupMenu("Folder Context Menu");
            TourFolder tourFolder = (TourFolder) node.getUserObject();

            // 在文件系统打开
            final JMenuItem openDirectoryAction = new JMenuItem("Open in File System", AllIcons.Actions.AddFile);
            openDirectoryAction.addActionListener(d ->
                    RevealFileAction.openDirectory(tourFolder.getVirtualFile().toNioPath()));

            // 创建新Tour的选项
            final JMenuItem newTourAction = new JMenuItem("Create New Tour", AllIcons.Actions.AddFile);
            newTourAction.addActionListener(d -> createNewTourInFolderListener(tourFolder));

            // 创建新文件夹的选项
            final JMenuItem newFolderAction = new JMenuItem("Create New Folder", AllIcons.Actions.NewFolder);
            newFolderAction.addActionListener(d -> createNewFolderListener(tourFolder));

            menu.add(openDirectoryAction);
            menu.add(newTourAction);
            menu.add(newFolderAction);
            menu.show(toursTree, e.getX(), e.getY());
        }
    }
    //endregion

    private void createNewTourListener() {
        final Tour newTour = createNewTour();
        if (newTour == null) {
            return; // i.e. hit cancel
        }

        StateManager.getInstance().getState(project).createTour(project, newTour);
        project.getMessageBus().syncPublisher(TourUpdateNotifier.TOPIC).tourUpdated(newTour);
        CodeTourNotifier.notifyTourAction(project, newTour, "Creation",
                String.format("Tour '%s' (file %s) has been created", newTour.getTitle(), newTour.getTourFile()));

    }

    private @Nullable Tour createNewTour() {
        final Tour newTour = Tour.builder()
                .id(UUID.randomUUID().toString())
                .tourFile("newTour" + Props.TOUR_EXTENSION_FULL)
                .title("A New Tour")
                .description("A New Tour")
                .createdAt(LocalDateTime.now())
                .steps(new ArrayList<>())
                .build();

        // Interactive creation (Title and filename) making sure that they are unique
        final Set<String> tourTitles = StateManager.getInstance().getState(project).getTours().stream()
                .map(Tour::getTitle)
                .collect(Collectors.toSet());
        final String updatedTitle = Messages.showInputDialog(project,
                "Input the title of the new Tour (should be unique)",
                "New Tour", AllIcons.Actions.NewFolder, newTour.getTitle(),
                new TourValidator(title -> StringUtils.isNotEmpty(title) && !tourTitles.contains(title)));
        if (updatedTitle == null) return null;
        newTour.setTitle(updatedTitle);


        // Just make sure that the new file is unique
        final Set<String> tourFiles = StateManager.getInstance().getState(project).getTours().stream()
                .map(Tour::getTourFile)
                .collect(Collectors.toSet());
        final String updatedFilename = Messages.showInputDialog(project,
                "Input the file name of the new Tour (should end with .tour and be unique)",
                "New Tour", AllIcons.Actions.NewFolder, Utils.fileNameFromTitle(newTour.getTitle()),
                new TourValidator(fileName -> StringUtils.isNotEmpty(fileName) &&
                        fileName.endsWith(Props.TOUR_EXTENSION_FULL) && !tourFiles.contains(fileName)));
        if (updatedFilename == null) return null;
        newTour.setTourFile(updatedFilename);
        return newTour;
    }

    private void createNewFolderListener(TourFolder parentFolder) {
        // 获取所有现有文件夹名称
        final Set<String> existingFolders = StateManager.getInstance().getState(project).getFolders().stream()
                .map(folder -> folder.getVirtualFile().getName())
                .collect(Collectors.toSet());

        // 交互式创建文件夹名称
        final String folderName = Messages.showInputDialog(project,
                "Input the name of the new folder",
                "New Folder", AllIcons.Actions.NewFolder, "NewFolder",
                new TourValidator(name -> StringUtils.isNotEmpty(name) && !existingFolders.contains(name)));
        if (folderName == null) return; // 即点击取消

        try {
            // 确定父目录
            VirtualFile parentDir;
            if (parentFolder != null) {
                parentDir = parentFolder.getVirtualFile();
            } else {
                // 如果是根节点，使用第一个.tours文件夹
                List<TourFolder> toursDirs = StateManager.getInstance().getState(project).getFolders().stream()
                        .filter(folder -> folder.getVirtualFile().getName().equals(Props.TOURS_DIR))
                        .collect(Collectors.toList());
                if (toursDirs.isEmpty()) {
                    CodeTourNotifier.error(project, "Cannot find .tours directory");
                    return;
                }
                parentDir = toursDirs.get(0).getVirtualFile();
            }

            // 创建文件夹
            WriteAction.runAndWait(() -> {
                try {
                    VirtualFile newFolder = parentDir.createChildDirectory(this, folderName);
                    // 刷新状态
                    StateManager.getInstance().getState(project).reloadState();
                    updateToursTree();
                    CodeTourNotifier.notifyTourAction(project, null, "Folder Creation",
                            String.format("Folder '%s' has been created", folderName));
                } catch (IOException ex) {
                    CodeTourNotifier.error(project, "Failed to create folder: " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            CodeTourNotifier.error(project, "Failed to create folder: " + ex.getMessage());
        }
    }

    //region Tour Context menu actions
    private void addNewStepOnTourListener(Tour tour) {
        MessageDialogBuilder.yesNo("", "");
        final boolean createDescriptionOnlyStep =
                MessageDialogBuilder.yesNo("Step Creation - Create a Description-Only Step?",
                                "To create a new Tour Step with navigation, go to the file you want to add a Step, " +
                                        "<kbd>Right Click on the Editor's Gutter</kbd> (i.e. next to line numbers) > <kbd>Add Tour Step</kbd>" +
                                        "\nHowever, you can create a Description-only Step (without navigation) from this option." +
                                        "\nDo you want to create a Description-Only Step?")
                        .ask(project);

        if (createDescriptionOnlyStep) {
            final Step step = Step.builder()
                    .title("A Description-Only Step")
                    .description("# Simple Description\nI won't navigate you anywhere")
                    .build();

            // Provide a dialog for Step editing
            final StepEditor stepEditor = new StepEditor(project, step);
            final boolean okSelected = stepEditor.showAndGet();
            if (!okSelected) return; // i.e. cancel the step creation

            final Step updatedStep = stepEditor.getUpdatedStep();
            tour.addStep(updatedStep);
            StateManager.getInstance().getState(project).updateTour(tour);

            // Notify UI to re-render
            project.getMessageBus().syncPublisher(TourUpdateNotifier.TOPIC).tourUpdated(tour);
        }
    }

    private void editTourListener(Tour tour) {

        final String updatedTitle = Messages.showInputDialog(project, "Edit Tour's title",
                "Edit Tour", AllIcons.Actions.Edit, tour.getTitle(), null);
        if (updatedTitle == null || updatedTitle.equals(tour.getTitle())) return;

        tour.setTitle(updatedTitle);
        StateManager.getInstance().getState(project).updateTour(tour);

        LOG.info("Active Tour: " + tour.getTitle());
        updateActiveTour(tour);
        updateToursTree();
        CodeTourNotifier.notifyTourAction(project, tour, "Tour Update",
                String.format("Tour's '%s' Title has been updated", tour.getTitle()));

        // Expand and select the first Step of the active Tour on the tree
        selectTourStep(tour.getSteps().get(tour.getSteps().isEmpty() ? -1 : 0), false);
    }

    private void jumpToSourceTourListener(Tour tour) {
        SlowOperations.allowSlowOperations(() -> {
            // 先从Tour中找
            VirtualFile tourFile = tour.getVirtualFile();
            if (tourFile != null) {
                new OpenFileDescriptor(project, tourFile, 0).navigate(true);
                return;
            }
            // 再从索引中捞一把
            final Collection<VirtualFile> virtualFiles = FilenameIndex.getVirtualFilesByName(tour.getTourFile(),
                    GlobalSearchScope.projectScope(project));
            final Optional<VirtualFile> virtualFile = virtualFiles.stream()
                    .filter(file -> !file.isDirectory() && file.getName().equals(tour.getTourFile()))
                    .findFirst();

            if (virtualFile.isEmpty()) {
                CodeTourNotifier.error(project, String.format("Could not locate navigation target '%s' for Tour '%s'",
                        tour.getTourFile(), tour.getTitle()));
                return;
            }

            // Navigate
            new OpenFileDescriptor(project, virtualFile.get(), 0).navigate(true);
        });
    }

    private void deleteTourListener(Tour tour) {
        StateManager.getInstance().getState(project).deleteTour(tour);
        CodeTourNotifier.notifyTourAction(project, tour, "Deletion", String.format("Tour " +
                "'%s' (file %s) has been deleted", tour.getTitle(), tour.getTourFile()));
    }
    //endregion

    //region Step Context menu actions
    private void editStepListener(Step step, Tour tour) {
        final int index = tour.getSteps().indexOf(step);

        // Prompt dialog for Step update
        final StepEditor stepEditor = new StepEditor(project, step);
        final boolean okSelected = stepEditor.showAndGet();
        if (!okSelected || !stepEditor.isDirty()) return;

        final Step updatedStep = stepEditor.getUpdatedStep();
        tour.updateStep(index, updatedStep);

        StateManager.getInstance().getState(project).updateTour(tour);
        CodeTourNotifier.notifyTourAction(project, tour, "Step Update",
                String.format("Step '%s' has been updated", step.getTitle()));
        project.getMessageBus().syncPublisher(StepSelectionNotifier.TOPIC).selectStep(updatedStep);
    }

    private void moveListener(Step step, Tour tour, boolean up) {
        final int index = tour.getSteps().indexOf(step);
        final int newIndex = up ? index - 1 : index + 1;
        tour.removeStep(index);
        if (tour.getSteps().size() <= newIndex || newIndex < 0)
            CodeTourNotifier.error(project, String.format("Cannot move Step '%s' %s!",
                    step.getTitle(), up ? "up" : "down"));

        tour.addStep(newIndex, step);

        StateManager.getInstance().getState(project).updateTour(tour);
        updateToursTree();
        CodeTourNotifier.notifyTourAction(project, tour, "Steps Order Update", "Steps have been re-arranged!");

        // Expand and select the last Step of the active Tour on the tree
        selectTourStep(step, false);
    }

    private void deleteStepListener(Step step, Tour tour) {
        final int index = tour.getSteps().indexOf(step);
        tour.removeStep(index);
        StateManager.getInstance().getState(project).updateTour(tour);
        updateToursTree();
        CodeTourNotifier.notifyTourAction(project, tour, "Step Deletion", String.format("Step " +
                "'%s' has been removed from Tour '%s'", step.getTitle(), tour.getTitle()));
        project.getMessageBus().syncPublisher(TourUpdateNotifier.TOPIC).tourUpdated(tour);
        // Expand and select a Step on the tree (on the same index)
        selectTourStep(tour.getSteps().get(Math.min(tour.getSteps().size() - 1, index)), false);
    }
    //endregion

    /**
     * Persist the selected tour and also notify the tree (for proper rendering)
     */
    private void updateActiveTour(Tour tour) {
        StateManager.getInstance().getState(project).setActiveTour(tour);
        if (toursTree != null && toursTree.getCellRenderer() instanceof TreeRenderer renderer) {
            renderer.setSelectedTourId(tour != null ? tour.getId() : "");
        }
    }

    private void selectTourStep(Step step) {
        selectTourStep(step, false);
    }

    private void selectTourStep(Step step, boolean navigate) {
        if (expandAndSelectStep(step)) {
            createOrUpdateContent(step, project);
            // Also navigate to that step (if set)
            if (navigate) {
                Navigator.navigateLine(step, project);
            }
        }
    }

    /**
     * 展开并选中对应step
     *
     * @param step step
     * @return 是否展开
     */
    private boolean expandAndSelectStep(Step step) {
        // Expand and select the given or the last Step of the active Tour on the tree
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        DefaultMutableTreeNode tourNode = findTourNode(root, step);
        if (tourNode == null) {
            return false;
        }
        toursTree.expandPath(new TreePath(tourNode.getPath()));
        // If activeIndex is provided, select it
        final DefaultMutableTreeNode stepNodeToSelect =
                (DefaultMutableTreeNode) tourNode.getChildAt(step.getStepIndex());
        TreePath stepTreePath = new TreePath(stepNodeToSelect.getPath());
        toursTree.getSelectionModel().setSelectionPath(stepTreePath);
        toursTree.scrollPathToVisible(stepTreePath);
        return true;
    }

    private DefaultMutableTreeNode findTourNode(DefaultMutableTreeNode node, Step step) {
        if (node.getUserObject() instanceof Tour nodeTour) {
            if (nodeTour.getTitle().equals(step.getOwner().getTitle())) {
                return node;
            }
        } else if (node.getUserObject() instanceof TourFolder || node.isRoot()) {
            // 递归处理子节点
            for (int i = 0; i < node.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                DefaultMutableTreeNode tourNode = findTourNode(child, step);
                if (tourNode != null) {
                    return tourNode;
                }
            }
        }
        return null;
    }

    private void reloadToursState() {
        StateManager.getInstance().getState(project).reloadState();
        updateActiveTour(null); // reset the activeTour
        updateToursTree();
    }

    private void createNewTourInFolderListener(TourFolder folder) {
        final Tour newTour = createNewTour();
        if (newTour == null) {
            return; // 即点击取消
        }

        StateManager.getInstance().getState(project).createTour(project, newTour, folder.getVirtualFile());
        project.getMessageBus().syncPublisher(TourUpdateNotifier.TOPIC).tourUpdated(newTour);
        CodeTourNotifier.notifyTourAction(project, newTour, "Creation",
                String.format("Tour '%s' (file %s) has been created", newTour.getTitle(), newTour.getTourFile()));
    }
}