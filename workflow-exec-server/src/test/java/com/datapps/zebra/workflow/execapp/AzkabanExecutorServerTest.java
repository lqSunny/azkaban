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

package com.datapps.zebra.workflow.execapp;

import com.datapps.zebra.workflow.AzkabanCommonModule;
import com.datapps.zebra.workflow.Constants;
import com.datapps.zebra.workflow.database.AzkabanDatabaseSetup;
import com.datapps.zebra.workflow.database.AzkabanDatabaseUpdater;
import com.datapps.zebra.workflow.executor.AlerterHolder;
import com.datapps.zebra.workflow.utils.Emailer;
import com.datapps.zebra.workflow.utils.Props;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.junit.Assert.assertNotNull;


public class AzkabanExecutorServerTest {

  public static final String AZKABAN_LOCAL_TEST_STORAGE = "AZKABAN_LOCAL_TEST_STORAGE";
  public static final String AZKABAN_DB_SQL_PATH = "azkaban-db/src/main/sql";

  private static final Props props = new Props();

  private static String getSqlScriptsDir() throws IOException {
    // Dummy because any resource file works.
    final URL resource = AzkabanExecutorServerTest.class.getClassLoader().getResource("test.file");
    final String dummyResourcePath = requireNonNull(resource).getPath();
    final Path resources = Paths.get(dummyResourcePath).getParent();
    final Path azkabanRoot = resources.getParent().getParent().getParent().getParent();

    final File sqlScriptDir = Paths.get(azkabanRoot.toString(), AZKABAN_DB_SQL_PATH).toFile();
    return props
        .getString(AzkabanDatabaseSetup.DATABASE_SQL_SCRIPT_DIR, sqlScriptDir.getCanonicalPath());
  }

  @BeforeClass
  public static void setUp() throws Exception {
    final String sqlScriptsDir = getSqlScriptsDir();
    props.put(AzkabanDatabaseSetup.DATABASE_SQL_SCRIPT_DIR, sqlScriptsDir);

    props.put("database.type", "h2");
    props.put("h2.path", "./h2");

    AzkabanDatabaseUpdater.runDatabaseUpdater(props, sqlScriptsDir, true);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    deleteQuietly(new File("h2.mv.db"));
    deleteQuietly(new File("h2.trace.db"));
    deleteQuietly(new File("executor.port"));
    deleteQuietly(new File("executions"));
    deleteQuietly(new File("projects"));
  }

  @Test
  public void testInjection() throws Exception {
    props
        .put(Constants.ConfigurationKeys.AZKABAN_STORAGE_LOCAL_BASEDIR, AZKABAN_LOCAL_TEST_STORAGE);

    final Injector injector = Guice.createInjector(
        new AzkabanCommonModule(props),
        new AzkabanExecServerModule()
    );

    assertNotNull(injector.getInstance(AzkabanExecutorServer.class));
    assertNotNull(injector.getInstance(Emailer.class));
    assertNotNull(injector.getInstance(AlerterHolder.class));
  }
}
