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

package com.datapps.zebra.workflow.jobExecutor;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;


public class Utils {

  private Utils() {
  }

  static void dumpFile(final String filename, final String fileContent) throws IOException {
    try (PrintWriter writer = new PrintWriter(filename, StandardCharsets.UTF_8.toString())) {
      writer.print(fileContent);
    }
  }

  static void removeFile(final String filename) throws IOException {
    Files.delete(Paths.get(filename));
  }
}
