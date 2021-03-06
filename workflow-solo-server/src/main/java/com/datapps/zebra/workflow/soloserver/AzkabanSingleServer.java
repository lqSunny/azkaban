/*
 * Copyright 2012 LinkedIn Corp.
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

package com.datapps.zebra.workflow.soloserver;

import static com.datapps.zebra.workflow.ServiceProvider.SERVICE_PROVIDER;

import com.datapps.zebra.workflow.AzkabanCommonModule;
import com.datapps.zebra.workflow.database.AzkabanDatabaseSetup;
import com.datapps.zebra.workflow.database.AzkabanDatabaseUpdater;
import com.datapps.zebra.workflow.execapp.AzkabanExecServerModule;
import com.datapps.zebra.workflow.execapp.AzkabanExecutorServer;
import com.datapps.zebra.workflow.server.AzkabanServer;
import com.datapps.zebra.workflow.utils.Props;
import com.datapps.zebra.workflow.webapp.AzkabanWebServer;
import com.datapps.zebra.workflow.webapp.AzkabanWebServerModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.apache.log4j.Logger;


public class AzkabanSingleServer {

  private static final Logger log = Logger.getLogger(AzkabanWebServer.class);

  private final AzkabanWebServer webServer;
  private final AzkabanExecutorServer executor;

  @Inject
  public AzkabanSingleServer(final AzkabanWebServer webServer,
      final AzkabanExecutorServer executor) {
    this.webServer = webServer;
    this.executor = executor;
  }

  public static void main(final String[] args) throws Exception {
    log.info("Starting Azkaban Server");

    final Props props = AzkabanServer.loadProps(args);
    if (props == null) {
      log.error("Properties not found. Need it to connect to the db.");
      log.error("Exiting...");
      return;
    }

    if (props.getBoolean(AzkabanDatabaseSetup.DATABASE_CHECK_VERSION, true)) {
      final boolean updateDB = props
          .getBoolean(AzkabanDatabaseSetup.DATABASE_AUTO_UPDATE_TABLES, true);
      final String scriptDir = props.getString(AzkabanDatabaseSetup.DATABASE_SQL_SCRIPT_DIR, "sql");
      AzkabanDatabaseUpdater.runDatabaseUpdater(props, scriptDir, updateDB);
    }

    /* Initialize Guice Injector */
    final Injector injector = Guice.createInjector(
        new AzkabanCommonModule(props),
        new AzkabanWebServerModule(),
        new AzkabanExecServerModule()
    );
    SERVICE_PROVIDER.setInjector(injector);

    /* Launch server */
    injector.getInstance(AzkabanSingleServer.class).launch();
  }

  private void launch() throws Exception {
    AzkabanWebServer.launch(this.webServer);
    log.info("Azkaban Web Server started...");

    AzkabanExecutorServer.launch(this.executor);
    log.info("Azkaban Exec Server started...");
  }
}
