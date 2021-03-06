package com.intellij.lang.javascript.flex.projectStructure.conversion;

import com.intellij.conversion.*;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * User: ksafonov
 */
class FlexProjectConverter extends ProjectConverter {
  private ConversionParams myParams;
  private final ConversionContext myContext;

  FlexProjectConverter(ConversionContext context) {
    myContext = context;
  }

  private boolean _isConversionNeeded() {
    for (File file : myContext.getModuleFiles()) {
      try {
        ModuleSettings moduleSettings = myContext.getModuleSettings(file);
        if (FlexModuleConverter.isConversionNeededStatic(moduleSettings)) return true;
      }
      catch (CannotConvertException ignored) {
      }
    }
    return false;
  }

  @Override
  public ConversionProcessor<ModuleSettings> createModuleFileConverter() {
    return new FlexModuleConverter(getParams());
  }

  @Nullable
  public ConversionProcessor<WorkspaceSettings> createWorkspaceFileConverter() {
    return _isConversionNeeded() ? new FlexWorkspaceConverter(getParams()) : null;
  }

  public ConversionParams getParams() {
    if (myParams == null) {
      myParams = new ConversionParams(myContext);
    }
    return myParams;
  }

  @Override
  public void preProcessingFinished() throws CannotConvertException {
    ComponentManagerSettings projectRootManagerSettings = myContext.getProjectRootManagerSettings();
    if (projectRootManagerSettings == null) return;
    Element projectRootManager = projectRootManagerSettings.getComponentElement(ProjectRootManager.class.getSimpleName());
    if (projectRootManager == null) return;

    getParams().projectSdkName = projectRootManager.getAttributeValue(ProjectRootManagerImpl.PROJECT_JDK_NAME_ATTR);

    ConversionParams.convertFlexSdks();
  }

  @Override
  public void postProcessingFinished() throws CannotConvertException {
    getParams().apply();
  }


  public ConversionProcessor<ProjectLibrariesSettings> createProjectLibrariesConverter() {
    return new FlexLibrariesConverter(myContext, myParams);
  }
}
