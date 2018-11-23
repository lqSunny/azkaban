/*
 * Copyright 2017 LinkedIn Corp.
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
 *
 */

package com.datapps.zebra.workflow.webapp;

import static com.datapps.zebra.workflow.ServiceProvider.SERVICE_PROVIDER;
import static com.datapps.zebra.workflow.ServiceProviderTest.assertSingleton;
import static com.datapps.zebra.workflow.executor.ExecutorManager.AZKABAN_USE_MULTIPLE_EXECUTORS;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.junit.Assert.assertNotNull;

import com.datapps.zebra.workflow.AzkabanCommonModule;
import com.datapps.zebra.workflow.database.AzkabanDatabaseSetup;
import com.datapps.zebra.workflow.database.AzkabanDatabaseUpdater;
import com.datapps.zebra.workflow.db.DatabaseOperator;
import com.datapps.zebra.workflow.executor.AlerterHolder;
import com.datapps.zebra.workflow.executor.Executor;
import com.datapps.zebra.workflow.executor.ExecutorLoader;
import com.datapps.zebra.workflow.executor.ExecutorManager;
import com.datapps.zebra.workflow.project.ProjectLoader;
import com.datapps.zebra.workflow.project.ProjectManager;
import com.datapps.zebra.workflow.spi.Storage;
import com.datapps.zebra.workflow.trigger.TriggerLoader;
import com.datapps.zebra.workflow.trigger.TriggerManager;
import com.datapps.zebra.workflow.utils.Emailer;
import com.datapps.zebra.workflow.utils.Props;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


public class AzkabanWebServerTest {

  public static final String AZKABAN_DB_SQL_PATH = "azkaban-db/src/main/sql";

  private static final Props props = new Props();

  private static String getUserManagerXmlFile() {
    final URL resource = AzkabanWebServerTest.class.getClassLoader()
        .getResource("azkaban-users.xml");
    return requireNonNull(resource).getPath();
  }

  private static String getSqlScriptsDir() throws IOException {
    // Dummy because any resource file works.
    final String dummyResourcePath = getUserManagerXmlFile();
    final Path resources = Paths.get(dummyResourcePath).getParent();
    final Path azkabanRoot = resources.getParent().getParent().getParent().getParent();

    final File sqlScriptDir = Paths.get(azkabanRoot.toString(), AZKABAN_DB_SQL_PATH).toFile();
    return sqlScriptDir.getCanonicalPath();
  }

  @BeforeClass
  public static void setUp() throws Exception {
    tearDown();

    final String sqlScriptsDir = getSqlScriptsDir();
    props.put(AzkabanDatabaseSetup.DATABASE_SQL_SCRIPT_DIR, sqlScriptsDir);

    props.put("database.type", "h2");
    props.put("h2.path", "./h2");

    props.put(AZKABAN_USE_MULTIPLE_EXECUTORS, "true");
    props.put("server.port", "0");
    props.put("jetty.port", "0");
    props.put("server.useSSL", "true");
    props.put("jetty.use.ssl", "false");
    props.put("user.manager.xml.file", getUserManagerXmlFile());

    AzkabanDatabaseUpdater.runDatabaseUpdater(props, sqlScriptsDir, true);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    SERVICE_PROVIDER.unsetInjector();

    deleteQuietly(new File("h2.mv.db"));
    deleteQuietly(new File("h2.trace.db"));
    deleteQuietly(new File("executor.port"));
    deleteQuietly(new File("executions"));
    deleteQuietly(new File("projects"));
  }

  @Test
  public void testInjection() throws Exception {
    final Injector injector = Guice.createInjector(
        new AzkabanCommonModule(props),
        new AzkabanWebServerModule()
    );
    SERVICE_PROVIDER.unsetInjector();
    SERVICE_PROVIDER.setInjector(injector);

    final ExecutorLoader executorLoader = injector.getInstance(ExecutorLoader.class);
    assertNotNull(executorLoader);

    final Executor executor = executorLoader.addExecutor("localhost", 60000);
    executor.setActive(true);
    executorLoader.updateExecutor(executor);

    assertNotNull(injector.getInstance(AzkabanWebServer.class));

    //Test if triggermanager is singletonly guiced. If not, the below test will fail.
    assertSingleton(ExecutorLoader.class, injector);
    assertSingleton(ExecutorManager.class, injector);
    assertSingleton(ProjectLoader.class, injector);
    assertSingleton(ProjectManager.class, injector);
    assertSingleton(Storage.class, injector);
    assertSingleton(DatabaseOperator.class, injector);
    assertSingleton(TriggerLoader.class, injector);
    assertSingleton(TriggerManager.class, injector);
    assertSingleton(AlerterHolder.class, injector);
    assertSingleton(Emailer.class, injector);

    SERVICE_PROVIDER.unsetInjector();
  }
}
