package com.datapps.zebra.workflow.project.validator;

import com.datapps.zebra.workflow.project.Project;
import com.datapps.zebra.workflow.utils.Props;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * ValidatorManager is responsible for loading the list of validators specified in the
 * Azkaban validator configuration file. Once these validators are loaded, the ValidatorManager
 * will use the registered validators to verify each uploaded project before persisting it.
 */
public interface ValidatorManager {

  /**
   * Load the validators using the given properties. Each validator is also given the specified
   * logger to record any necessary message in the Azkaban log file.
   */
  void loadValidators(Props props, Logger logger);

  /**
   * Validate the given project using the registered list of validators. This method returns a
   * map of {@link ValidationReport} with the key being the validator's name and the value being
   * the {@link ValidationReport} generated by that validator.
   */
  Map<String, ValidationReport> validate(Project project, File projectDir);

  /**
   * The ValidatorManager should have a default validator which checks for the most essential
   * components of a project. The ValidatorManager should always load the default validator.
   * This method returns the default validator of this ValidatorManager.
   */
  ProjectValidator getDefaultValidator();

  /**
   * Returns a list of String containing the name of each registered validators.
   */
  List<String> getValidatorsInfo();
}
