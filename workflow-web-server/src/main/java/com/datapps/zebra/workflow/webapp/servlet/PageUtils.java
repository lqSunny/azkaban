package com.datapps.zebra.workflow.webapp.servlet;

import com.datapps.zebra.workflow.server.session.Session;
import com.datapps.zebra.workflow.user.Permission;
import com.datapps.zebra.workflow.user.User;
import com.datapps.zebra.workflow.user.UserManager;
import com.datapps.zebra.workflow.user.UserUtils;


public final class PageUtils {

  private PageUtils() {

  }

  /**
   * Method hides the upload button for regular users from relevant pages when the property
   * "lockdown.upload.projects" is set. The button is displayed for admin users and users with
   * upload permissions.
   */
  public static void hideUploadButtonWhenNeeded(final Page page, final Session session, final UserManager userManager,
      final Boolean lockdownUploadProjects) {
    final User user = session.getUser();

    if (lockdownUploadProjects && !UserUtils.hasPermissionforAction(userManager, user,
        Permission.Type.UPLOADPROJECTS)) {
      page.add("hideUploadProject", true);
    }
  }
}
