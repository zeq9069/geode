/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.geode.management.internal.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.test.compiler.ClassBuilder;
import org.apache.geode.test.dunit.rules.ClusterStartupRule;
import org.apache.geode.test.dunit.rules.MemberVM;
import org.apache.geode.test.junit.categories.DistributedTest;
import org.apache.geode.test.junit.rules.GfshCommandRule;
import org.apache.geode.test.junit.rules.VMProvider;

@Category(DistributedTest.class)
public class AlterRegionCommandDUnitTest {

  @ClassRule
  public static ClusterStartupRule cluster = new ClusterStartupRule();

  @ClassRule
  public static GfshCommandRule gfsh = new GfshCommandRule();

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static MemberVM locator, server1, server2, server3;

  @BeforeClass
  public static void beforeClass() throws Exception {
    locator = cluster.startLocatorVM(0);
    server1 = cluster.startServerVM(1, "group1", locator.getPort());
    server2 = cluster.startServerVM(2, locator.getPort());
    server3 = cluster.startServerVM(3, locator.getPort());

    gfsh.connectAndVerify(locator);
  }

  @Test
  public void testAlterRegionResetCacheListeners() throws IOException {
    gfsh.executeAndAssertThat("list regions").statusIsSuccess().containsOutput("No Regions Found");

    gfsh.executeAndAssertThat("create region --name=regionA --type=PARTITION").statusIsSuccess();

    deployJarFilesForRegionAlter();

    String listenerABC =
        "com.cadrdunit.RegionAlterCacheListenerA,com.cadrdunit.RegionAlterCacheListenerB,com.cadrdunit.RegionAlterCacheListenerC";
    gfsh.executeAndAssertThat("alter region --name=regionA --cache-listener=" + listenerABC)
        .statusIsSuccess().tableHasRowCount("Member", 3);

    VMProvider.invokeInEveryMember(() -> {
      RegionAttributes attributes =
          ClusterStartupRule.getCache().getRegion("regionA").getAttributes();
      assertEquals(3, attributes.getCacheListeners().length);

      assertThat(Arrays.stream(attributes.getCacheListeners()).map(c -> c.getClass().getName())
          .collect(Collectors.toSet())).containsExactlyInAnyOrder(
              "com.cadrdunit.RegionAlterCacheListenerA", "com.cadrdunit.RegionAlterCacheListenerB",
              "com.cadrdunit.RegionAlterCacheListenerC");
    }, server1, server2, server3);

    // remove listener on server1
    gfsh.executeAndAssertThat("alter region --group=group1 --name=regionA --cache-listener=''")
        .statusIsSuccess().tableHasRowCount("Member", 1).tableHasRowWithValues("Member", "Status",
            "server-1", "Region \"/regionA\" altered on \"server-1\"");

    server1.invoke(() -> {
      RegionAttributes attributes =
          ClusterStartupRule.getCache().getRegion("regionA").getAttributes();
      assertEquals(0, attributes.getCacheListeners().length);
    });
  }

  private void deployJarFilesForRegionAlter() throws IOException {
    ClassBuilder classBuilder = new ClassBuilder();
    final File jarFile1 = new File(temporaryFolder.getRoot(), "testAlterRegion1.jar");
    final File jarFile2 = new File(temporaryFolder.getRoot(), "testAlterRegion2.jar");
    final File jarFile3 = new File(temporaryFolder.getRoot(), "testAlterRegion3.jar");
    final File jarFile4 = new File(temporaryFolder.getRoot(), "testAlterRegion4.jar");
    final File jarFile5 = new File(temporaryFolder.getRoot(), "testAlterRegion5.jar");

    byte[] jarBytes =
        classBuilder.createJarFromClassContent("com/cadrdunit/RegionAlterCacheListenerA",
            "package com.cadrdunit;" + "import org.apache.geode.cache.util.CacheListenerAdapter;"
                + "public class RegionAlterCacheListenerA extends CacheListenerAdapter {}");
    writeJarBytesToFile(jarFile1, jarBytes);
    gfsh.executeAndAssertThat("deploy --jar=" + jarFile1.getAbsolutePath()).statusIsSuccess();

    jarBytes = classBuilder.createJarFromClassContent("com/cadrdunit/RegionAlterCacheListenerB",
        "package com.cadrdunit;" + "import org.apache.geode.cache.util.CacheListenerAdapter;"
            + "public class RegionAlterCacheListenerB extends CacheListenerAdapter {}");
    writeJarBytesToFile(jarFile2, jarBytes);
    gfsh.executeAndAssertThat("deploy --jar=" + jarFile2.getAbsolutePath()).statusIsSuccess();

    jarBytes = classBuilder.createJarFromClassContent("com/cadrdunit/RegionAlterCacheListenerC",
        "package com.cadrdunit;" + "import org.apache.geode.cache.util.CacheListenerAdapter;"
            + "public class RegionAlterCacheListenerC extends CacheListenerAdapter {}");
    writeJarBytesToFile(jarFile3, jarBytes);
    gfsh.executeAndAssertThat("deploy --jar=" + jarFile3.getAbsolutePath()).statusIsSuccess();

    jarBytes = classBuilder.createJarFromClassContent("com/cadrdunit/RegionAlterCacheLoader",
        "package com.cadrdunit;" + "import org.apache.geode.cache.CacheLoader;"
            + "import org.apache.geode.cache.CacheLoaderException;"
            + "import org.apache.geode.cache.LoaderHelper;"
            + "public class RegionAlterCacheLoader implements CacheLoader {"
            + "public void close() {}"
            + "public Object load(LoaderHelper helper) throws CacheLoaderException {return null;}}");
    writeJarBytesToFile(jarFile4, jarBytes);
    gfsh.executeAndAssertThat("deploy --jar=" + jarFile4.getAbsolutePath()).statusIsSuccess();

    jarBytes = classBuilder.createJarFromClassContent("com/cadrdunit/RegionAlterCacheWriter",
        "package com.cadrdunit;" + "import org.apache.geode.cache.util.CacheWriterAdapter;"
            + "public class RegionAlterCacheWriter extends CacheWriterAdapter {}");
    writeJarBytesToFile(jarFile5, jarBytes);
    gfsh.executeAndAssertThat("deploy --jar=" + jarFile5.getAbsolutePath()).statusIsSuccess();
  }

  private void writeJarBytesToFile(File jarFile, byte[] jarBytes) throws IOException {
    final OutputStream outStream = new FileOutputStream(jarFile);
    outStream.write(jarBytes);
    outStream.flush();
    outStream.close();
  }
}
