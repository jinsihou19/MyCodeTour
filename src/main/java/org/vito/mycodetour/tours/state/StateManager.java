package org.vito.mycodetour.tours.state;

import com.intellij.openapi.project.Project;
import org.vito.mycodetour.tours.domain.Tour;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * tour状态管理，按工程区分
 *
 * @author vito
 * @since 1.0
 * Created on 2025/1/22
 */
public class StateManager {

    private final Map<Project, ToursState> stateMap = new HashMap<>();

    private static final StateManager STATE_MANAGER = new StateManager();

    public static StateManager getInstance() {
        return STATE_MANAGER;
    }

    private StateManager() {
    }

    /**
     * 注册项目
     *
     * @param project 项目
     */
    public void registerProject(Project project) {
        stateMap.putIfAbsent(project, new ToursState(project));
    }

    /**
     * 获取当前工程的ToursState
     *
     * @param project 工程
     * @return ToursState
     */
    public ToursState getState(Project project) {
        // 首次绑定
        if (stateMap.get(project) == null) {
            stateMap.putIfAbsent(project, new ToursState(project));
        }
        return stateMap.get(project);
    }

    public void resetActiveStepIndex(Project project) {
        getState(project).resetActiveStepIndex();
    }

    public int getActiveStepIndex(Project project) {
        return getState(project).getActiveStepIndex();
    }

    /**
     * 获取工程当前激活的tour
     *
     * @param project 当前工程
     * @return tour
     */
    public Optional<Tour> getActiveTour(Project project) {
        ToursState state = getState(project);
        return state.getActiveTour().flatMap(
                value -> state.getTours().stream()
                        .filter(tour -> tour.getId().equals(value.getId()))
                        .findFirst());
    }

}