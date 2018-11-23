/*
 * Copyright 2014 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.datapps.zebra.workflow.restli;

import com.datapps.zebra.workflow.project.Project;
import com.datapps.zebra.workflow.server.session.Session;
import com.datapps.zebra.workflow.user.Permission;
import com.datapps.zebra.workflow.user.Role;
import com.datapps.zebra.workflow.user.User;
import com.datapps.zebra.workflow.user.UserManager;
import com.datapps.zebra.workflow.user.UserManagerException;
import com.datapps.zebra.workflow.utils.WebUtils;
import com.datapps.zebra.workflow.webapp.AzkabanWebServer;
import com.linkedin.restli.server.ResourceContext;
import java.util.Map;

public class ResourceUtils {

  public static boolean hasPermission(final Project project, final User user,
      final Permission.Type type) {
    final UserManager userManager = AzkabanWebServer.getInstance().getUserManager();
    if (project.hasPermission(user, type)) {
      return true;
    }

    for (final String roleName : user.getRoles()) {
      final Role role = userManager.getRole(roleName);
      if (role.getPermission().isPermissionSet(type)
          || role.getPermission().isPermissionSet(Permission.Type.ADMIN)) {
        return true;
      }
    }

    return false;
  }

  public static User getUserFromSessionId(final String sessionId, final String ip)
      throws UserManagerException {
    final Session session =
        AzkabanWebServer.getInstance().getSessionCache().getSession(sessionId);
    if (session == null) {
      throw new UserManagerException("Invalid session. Login required");
    } else if (!session.getIp().equals(ip)) {
      throw new UserManagerException("Invalid session. Session expired.");
    }

    return session.getUser();
  }

  public static String getRealClientIpAddr(final ResourceContext context) {

    // If some upstream device added an X-Forwarded-For header
    // use it for the client ip
    // This will support scenarios where load balancers or gateways
    // front the Azkaban web server and a changing Ip address invalidates
    // the session
    final Map<String, String> headers = context.getRequestHeaders();

    final WebUtils utils = new WebUtils();

    return utils.getRealClientIpAddr(headers,
        (String) context.getRawRequestContext().getLocalAttr("REMOTE_ADDR"));
  }
}
