/*
 * Copyright 2015 Azkaban Authors
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

package com.datapps.zebra.workflow.test.executions;

import org.junit.Assert;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

public class TestExecutions {

  public static File getFlowDir(final String path) throws URISyntaxException {
    final URL url = TestExecutions.class.getResource(path);
    Assert.assertNotNull(url);
    return new File(url.toURI());
  }
}
