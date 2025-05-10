package org.vito.mycodetour.tours.service;

import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vito.mycodetour.tours.ui.AppSettingsComponent;
import org.vito.mycodetour.tours.ui.CodeTourNotifier;

import javax.swing.JComponent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Provides controller functionality for application settings.
 *
 * @author vito
 * Created on 2025/01/01
 */
public class AppSettingsConfigurable implements Configurable, Configurable.WithEpDependencies {

    private AppSettingsComponent settingsComponent;

    // A default constructor with no arguments is required because this implementation
    // is registered as an applicationConfigurable EP

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "CodeTour Plugin Settings";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return settingsComponent.getPreferredFocusedComponent();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        settingsComponent = new AppSettingsComponent();
        return settingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        AppSettingsState settings = AppSettingsState.getInstance();
        return settingsComponent.isOnboardingAssistantOn() != settings.isOnboardingAssistant()
                || (settingsComponent.getSortOption() != settings.getSortOption())
                || (settingsComponent.getSortDirection() != settings.getSortDirection())
                || !settingsComponent.getSourcePath().equals(settings.getSourcePath());
    }

    @Override
    public void apply() {
        AppSettingsState settings = AppSettingsState.getInstance();
        settings.setOnboardingAssistant(settingsComponent.isOnboardingAssistantOn());
        settings.setSortDirection(settingsComponent.getSortDirection());
        settings.setSortOption(Optional.ofNullable(settingsComponent.getSortOption())
                .orElse(AppSettingsState.SortOptionE.TITLE));
        settings.setSourcePath(settingsComponent.getSourcePath());
    }

    @Override
    public void reset() {
        AppSettingsState settings = AppSettingsState.getInstance();
        settingsComponent.setOnboardingAssistant(settings.isOnboardingAssistant());
        settingsComponent.setSortOption(settings.getSortOption());
        settingsComponent.setSortDirection(settings.getSortDirection());
        settingsComponent.setSourcePath(settings.getSourcePath());
        //TODO: This should be done automatically, instead of just prompting user

        // Notify user to reload Settings
        CodeTourNotifier.warn(null, "CodeTour User Settings Changed: " +
                "Reload Tours from the bottom right button in Tool Pane Window");
    }

    @Override
    public void disposeUIResources() {
        settingsComponent = null;
    }

    @Override
    public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
        return List.of();
    }
}