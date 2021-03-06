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

import com.datapps.zebra.workflow.executor.ExecutorLoader;
import com.datapps.zebra.workflow.executor.JdbcExecutorLoader;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;


/**
 * This Guice module is currently a one place container for all bindings in the current module. This
 * is intended to help during the migration process to Guice. Once this class starts growing we can
 * move towards more modular structuring of Guice components.
 */
public class AzkabanExecServerModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(ExecutorLoader.class).to(JdbcExecutorLoader.class);
    bind(AzkabanExecutorServer.class).in(Scopes.SINGLETON);
    bind(TriggerManager.class).in(Scopes.SINGLETON);
    bind(FlowRunnerManager.class).in(Scopes.SINGLETON);
  }
}
