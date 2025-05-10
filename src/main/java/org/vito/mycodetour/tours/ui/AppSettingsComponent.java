package org.vito.mycodetour.tours.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.vito.mycodetour.tours.service.AppSettingsState;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * Supports creating and managing a {@link JPanel} for the Settings Dialog.
 *
* @author vito
* Created on 2025/01/01
 */
public class AppSettingsComponent {

    private final JPanel mainPanel;
    private final JBCheckBox onboardingAssistantCb = new JBCheckBox("Enable/disable virtual onboarding assistant");
    private final ComboBox<AppSettingsState.SortOptionE> sortOption =
            new ComboBox<>(AppSettingsState.SortOptionE.values());
    private final ComboBox<AppSettingsState.SortDirectionE> sortDirection =
            new ComboBox<>(AppSettingsState.SortDirectionE.values());

    private final TextFieldWithBrowseButton pathField =
            new TextFieldWithBrowseButton(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
                    fcd.setShowFileSystemRoots(true);
                    fcd.setTitle("Select Doc Dir");
                    fcd.setDescription(".tours");
                    fcd.setHideIgnored(false);
                    VirtualFile file = FileChooser.chooseFile(fcd, mainPanel, null, null);
                    if (file == null) {
                        return;
                    }
                    pathField.setText(file.getPath().replace('/', File.separatorChar));
                }
            });

    public AppSettingsComponent() {
        mainPanel = FormBuilder.createFormBuilder()
                .addComponent(onboardingAssistantCb, 1)
                // .addComponent(new TitledSeparator())
                .addLabeledComponent(new JBLabel("Tours sort option:"), sortOption, 2)
                .addLabeledComponent(new JBLabel("Sort direction: ascending / descending"), sortDirection, 3)
                .addLabeledComponent(new JBLabel(".tour Source path"), pathField, 4)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return onboardingAssistantCb;
    }

    public boolean isOnboardingAssistantOn() {
        return onboardingAssistantCb.isSelected();
    }

    public AppSettingsState.SortOptionE getSortOption() {
        return sortOption.getItem();
    }

    public AppSettingsState.SortDirectionE getSortDirection() {
        return sortDirection.getItem();
    }

    public void setOnboardingAssistant(boolean newStatus) {
        onboardingAssistantCb.setSelected(newStatus);
    }

    public void setSortOption(AppSettingsState.SortOptionE newSortOption) {
        sortOption.setItem(newSortOption);
    }

    public void setSortDirection(AppSettingsState.SortDirectionE newSortDirection) {
        sortDirection.setItem(newSortDirection);
    }

    public String getSourcePath() {
        return pathField.getText();
    }

    public void setSourcePath(String sourcePath) {
        pathField.setText(sourcePath);
    }
}
