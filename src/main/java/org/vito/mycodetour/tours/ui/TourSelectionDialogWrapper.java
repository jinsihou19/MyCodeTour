package org.vito.mycodetour.tours.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;
import org.vito.mycodetour.tours.domain.Tour;
import org.vito.mycodetour.tours.state.StateManager;
import org.vito.mycodetour.tours.state.Validator;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

/**
 * @author vito
 * Created on 2025/1/1
 */
public class TourSelectionDialogWrapper extends DialogWrapper {

    private Optional<Tour> selected = Optional.empty();
    private final Project project;

    public TourSelectionDialogWrapper(Project project, String title) {
        super(true);
        this.project = project;
        setTitle(title);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel dialogPanel = new JPanel(new BorderLayout());

        var tours = StateManager.getInstance().getState(project).getTours();

        // Demo should not be present in this selection
        tours.removeIf(Validator::isDemo);

        final int toursSize = tours.size();
        final Tour[] toursOptions = new Tour[toursSize];
        for (int i = 0; i < toursSize; i++)
            toursOptions[i] = tours.get(i);

        final ComboBox<Tour> comboBox = new ComboBox<>(toursOptions);
        comboBox.addActionListener(e -> {
            selected = Optional.of(comboBox.getItem());
        });
        JLabel label = new JLabel("Select the Tour");
        // label.setPreferredSize(new Dimension(100, 100));
        dialogPanel.add(label, BorderLayout.NORTH);
        dialogPanel.add(comboBox, BorderLayout.CENTER);

        return dialogPanel;
    }

    public Optional<Tour> getSelected() {
        return selected;
    }
}