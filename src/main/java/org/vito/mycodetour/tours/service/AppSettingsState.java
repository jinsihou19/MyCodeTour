package org.vito.mycodetour.tours.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supports storing the application settings in a persistent way.
 * The {@link State} and {@link Storage} annotations define the name of the data and the file name where
 * these persistent application settings are stored.
 *
* @author vito
* Created on 2025/01/01
 */
@State(
      name = "org.vito.mycodetour.tours.service.AppSettingsState",
      storages = @Storage("CodeTourSettings.xml")
)
public class AppSettingsState implements PersistentStateComponent<AppSettingsState> {

   private boolean onboardingAssistant = true;
   private SortOptionE sortOption = SortOptionE.TITLE;
   private SortDirectionE sortDirection = SortDirectionE.ASC;
   private String sourcePath = "";

   public static AppSettingsState getInstance() {
      return ApplicationManager.getApplication().getService(AppSettingsState.class);
   }

   @Nullable
   @Override
   public AppSettingsState getState() {
      return this;
   }

   @Override
   public void loadState(@NotNull AppSettingsState state) {
      XmlSerializerUtil.copyBean(state, this);
   }

   public boolean isOnboardingAssistantOn() {return onboardingAssistant;}

   public void toggleOnboardingAssistant() {
      onboardingAssistant = !onboardingAssistant;
   }

   public enum SortOptionE {
      TITLE, FILENAME, CREATION_DATE;
   }

   public enum SortDirectionE {
      ASC, DESC;
   }

   public boolean isOnboardingAssistant() {
      return onboardingAssistant;
   }

   public AppSettingsState setOnboardingAssistant(boolean onboardingAssistant) {
      this.onboardingAssistant = onboardingAssistant;
      return this;
   }

   public SortOptionE getSortOption() {
      return sortOption;
   }

   public AppSettingsState setSortOption(SortOptionE sortOption) {
      this.sortOption = sortOption;
      return this;
   }

   public SortDirectionE getSortDirection() {
      return sortDirection;
   }

   public AppSettingsState setSortDirection(SortDirectionE sortDirection) {
      this.sortDirection = sortDirection;
      return this;
   }

   public String getSourcePath() {
      return sourcePath;
   }

   public AppSettingsState setSourcePath(String sourcePath) {
      this.sourcePath = sourcePath;
      return this;
   }
}
