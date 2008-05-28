/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2008 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */
package org.netbeans.modules.ruby.platform.gems;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.netbeans.api.ruby.platform.RubyPlatform;
import org.netbeans.api.ruby.platform.RubyPlatformManager;
import org.netbeans.api.ruby.platform.RubyPlatformManagerTest;
import org.netbeans.api.ruby.platform.RubyTestBase;
import org.netbeans.api.ruby.platform.TestUtil;
import org.netbeans.modules.ruby.platform.Util;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class GemManagerTest extends RubyTestBase {

    public GemManagerTest(final String testName) {
        super(testName);
        TestUtil.getXTestJRubyHome();
    }

    public void testGetGemProblem() {
        RubyPlatform jruby = RubyPlatformManager.getDefaultPlatform();
        GemManager gm = jruby.getGemManager();
        assertNotNull(gm);
    }

    public void testGetRubyLibGemDir() throws Exception {
        RubyPlatform platform = RubyPlatformManager.addPlatform(setUpRubyWithGems());
        GemManager gemManager = platform.getGemManager();
        assertEquals("righ gem dir", new File(platform.getLibDir(), "ruby/gems/1.8"), new File(gemManager.getGemHome()));
    }
    
    public void testGetGem() throws Exception {
        RubyPlatform platform = RubyPlatformManager.addPlatform(setUpRubyWithGems());
        GemManager gemManager = platform.getGemManager();
        assertEquals("righ gem dir", new File(new File(getTestRubyHome(), "bin"), "gem").getAbsolutePath(), gemManager.getGemTool());
    }
    
    // XXX: (Try to) reenable with JRuby 1.1.2+
//    public void testGemFetching() {
//        RubyPlatform jruby = RubyPlatformManager.getDefaultPlatform();
//        GemManager gm = jruby.getGemManager();
//        
//        List<String> errors = new ArrayList<String>();
//        List<Gem> available = gm.getRemoteGems(errors);
//        assertNotNull("gem not null", available);
//        System.out.println("available: " + available.size());
//        assertTrue("no errros: " + errors, errors.isEmpty());
//        
//        List<Gem> installed = gm.getInstalledGems(errors);
//        assertNotNull("gem not null", installed);
//        System.out.println("installed: " + installed.size());
//        assertTrue("no errros", errors.isEmpty());
//        
//        gm.reloadIfNeeded(errors);
//        assertTrue("no errros", errors.isEmpty());
//    }

    public void testIsValidGemHome() throws Exception {
        assertFalse("not valid", GemManager.isValidGemHome(getWorkDir()));
        assertTrue("valid", GemManager.isValidGemHome(
                new File(RubyPlatformManager.getDefaultPlatform().getInfo().getGemHome())));
        RubyPlatform platform = RubyPlatformManager.addPlatform(setUpRubyWithGems());
        assertTrue("valid", GemManager.isValidGemHome(
                new File(platform.getInfo().getGemHome())));
    }
    
    public void testGetRepositories() throws Exception {
        RubyPlatform platform = RubyPlatformManager.getDefaultPlatform();
        GemManager gemManager = platform.getGemManager();
        Set<? extends File> paths = gemManager.getRepositories();
        assertEquals("one path element", 1, paths.size());
        assertEquals("same as Gem Home", gemManager.getGemHomeF(), paths.iterator().next());
        assertEquals("same as Gem Home", gemManager.getGemHome(), platform.getInfo().getGemPath());
    }
    
    public void testAddRemoveRepository() throws Exception {
        RubyPlatform platform = RubyPlatformManager.getDefaultPlatform();
        GemManager gemManager = platform.getGemManager();
        File dummyRepo = new File(getWorkDirPath(), "/a");
        gemManager.addGemPath(dummyRepo);
        assertEquals("two repositories", 2, gemManager.getRepositories().size());
        assertTrue("two repositories in info's gempath", platform.getInfo().getGemPath().indexOf(File.pathSeparatorChar) != -1);
        gemManager.removeGemPath(dummyRepo);
        assertEquals("one repositories", 1, gemManager.getRepositories().size());
        assertTrue("one repositories in info's gempath", platform.getInfo().getGemPath().indexOf(File.pathSeparatorChar) == -1);
    }
    
    public void testSetGemHome() throws Exception {
        RubyPlatform platform = RubyPlatformManager.getDefaultPlatform();
        GemManager gemManager = platform.getGemManager();
        String origGemHome = gemManager.getGemHome();
        File dummyRepo = new File(getWorkDirPath(), "/a");
        platform.setGemHome(dummyRepo);
        RubyPlatformManagerTest.resetPlatforms();
        String newGemHome = RubyPlatformManager.getDefaultPlatform().getGemManager().getGemHome();
        assertFalse("Gem Home changed", origGemHome.equals(newGemHome));
    }
    
    public void testAddTheSameRepositoryTwice() {
        RubyPlatform platform = RubyPlatformManager.getDefaultPlatform();
        GemManager gemManager = platform.getGemManager();
        File dummyRepo = new File(getWorkDirPath(), "/a");
        assertTrue("successfuly added", gemManager.addGemPath(dummyRepo));
        assertFalse("failed to add second time", gemManager.addGemPath(dummyRepo));
    }
    
    public void testInitializeRepository() throws Exception {
        FileObject gemRepo = FileUtil.toFileObject(getWorkDir()).createFolder("gem-repo");
        GemManager.initializeRepository(gemRepo);
        GemManager.isValidGemHome(FileUtil.toFile(gemRepo));
    }
    
    public void testInitializeRepositoryFile() throws Exception {
        File gemRepo = new File(getWorkDir(), "gem-repo");
        GemManager.initializeRepository(gemRepo);
        GemManager.isValidGemHome(gemRepo);
    }
    
    public void testGetVersionForPlatform() throws IOException {
        RubyPlatform platform = RubyPlatformManager.getDefaultPlatform();
        GemManager gemManager = platform.getGemManager();
        RubyPlatform jruby = RubyPlatformManager.getDefaultPlatform();
        FileObject gemRepo = FileUtil.toFileObject(getWorkDir()).createFolder("gem-repo");
        GemManager.initializeRepository(gemRepo);
        jruby.setGemHome(FileUtil.toFile(gemRepo));
        String version = "0.10.0";
        installFakeGem("ruby-debug-base", version, platform);
        assertEquals("native fast debugger available", version, gemManager.getLatestVersion("ruby-debug-base"));
        assertFalse("no jruby fast debugger available", gemManager.isGemInstalledForPlatform("ruby-debug-base", "0.10.0"));
        uninstallFakeGem("ruby-debug-base", version, platform);
        installFakeGem("ruby-debug-base", version, "java", platform);
        assertEquals("no jruby fast debugger available", true, gemManager.isGemInstalledForPlatform("ruby-debug-base", "0.10.0"));
    }
    
    public void testIsGemInstalledForPlatform() throws IOException {
        RubyPlatform platform = RubyPlatformManager.addPlatform(setUpRubyWithGems());
        for (String version : new String[]{"0.10.0", "0.10.1"}) {
            installFakeGem("ruby-debug-base", version, platform);
        }
        GemManager gemManager = platform.getGemManager();
        assertTrue(gemManager.isGemInstalledForPlatform("ruby-debug-base", "0.10.0", false));
        assertTrue(gemManager.isGemInstalledForPlatform("ruby-debug-base", "0.10.0", true));
        assertTrue(gemManager.isGemInstalledForPlatform("ruby-debug-base", "0.10.1", false));
        assertTrue(gemManager.isGemInstalledForPlatform("ruby-debug-base", "0.10.1", true));
    }
    
    public void testChooseGems() throws Exception {
        RubyPlatform platform = RubyPlatformManager.addPlatform(setUpRubyWithGems());
        GemManager gemManager = platform.getGemManager();
        
        String gemLibs = gemManager.getGemHome();
        File specs = new File(new File(gemManager.getGemHome()), "specifications");

        // Put gems into the gemLibs dir
        String[] gemDirs = new String[]{"foo-1.0.0",
                "notagem",
                "pdf-writer-0.1.1",
                "mongrel-1.0.0-mswin",
                "bar-baz-0.3.3-ruby",
                "activerecord-1.15.1.6752",
                "activerecord-1.15.3.6752"};
        for (String gemDir : gemDirs) {
            new File(gemLibs, gemDir).mkdir();
            new File(specs, gemDir + ".gemspec").createNewFile();
        }

        // Test for 106862
        new File(gemLibs, "sqlite-2.0.1").mkdirs();
        new File(gemLibs, "sqlite3-ruby-1.2.0").mkdirs();

        // Now introspect on the structure
        Set<String> installedGems = gemManager.getInstalledGemsFiles();
        assertTrue(installedGems.contains("foo"));
        assertTrue(installedGems.contains("pdf-writer"));
        assertTrue(installedGems.contains("mongrel"));
        assertTrue(installedGems.contains("bar-baz"));
        assertTrue(installedGems.contains("activerecord"));
        assertFalse(installedGems.contains("notagem"));
        assertFalse(installedGems.contains("whatever"));
        assertFalse(installedGems.contains("sqlite"));
        assertFalse(installedGems.contains("sqlite3-ruby"));

        assertEquals("1.0.0", gemManager.getLatestVersion("foo"));
        assertEquals(null, gemManager.getLatestVersion("notagem"));
        assertEquals(null, gemManager.getLatestVersion("nosuchgem"));
        assertEquals(null, gemManager.getLatestVersion("sqlite"));
        assertEquals(null, gemManager.getLatestVersion("sqlite3-ruby"));
        assertEquals("1.0.0", gemManager.getLatestVersion("mongrel"));
        assertEquals("0.3.3", gemManager.getLatestVersion("bar-baz"));
        assertEquals("0.1.1", gemManager.getLatestVersion("pdf-writer"));
        assertEquals("1.15.3.6752", gemManager.getLatestVersion("activerecord"));
    }
    
    public void testInstallLocal() throws IOException {
        RubyPlatform platform = RubyPlatformManager.getDefaultPlatform();
        GemManager gemManager = platform.getGemManager();
        RubyPlatform jruby = RubyPlatformManager.getDefaultPlatform();
        FileObject gemRepo = FileUtil.toFileObject(getWorkDir()).createFolder("gem-repo");
        GemManager.initializeRepository(gemRepo);
        jruby.setGemHome(FileUtil.toFile(gemRepo));
        jruby.getInfo().setGemPath("");
        File rakeGem = getRakeGem();
        assertNull("rake is not installed", gemManager.getLatestVersion("rake"));
        gemManager.installLocal(rakeGem, null, false, false, false, null);
        assertNotNull("rake is installed", gemManager.getLatestVersion("rake"));
    }

    private File getRakeGem() throws IOException {
        File rakeGem = new File(TestUtil.getXTestJRubyHome(), "lib/ruby/gems/1.8/cache/rake-0.8.1.gem");
        assertNotNull("rake gem found", rakeGem);
        assertTrue("rake gem found", rakeGem.isFile());
        return rakeGem;
    }

    // XXX
//    public void testFindGemExecutableWith_GEM_HOME() throws Exception {
//        File gemRepo = new File(getWorkDir(), "gemrepo");
//        File gemRepoBinF = new File(gemRepo, "bin");
//        gemRepoBinF.mkdirs();
//        RubyPlatform platform = RubyPlatformManager.addPlatform(setUpRuby(), "ruby");
//        GemManager.TEST_GEM_HOME = gemRepo.getAbsolutePath();
//        touch("rdebug-ide", gemRepoBinF.getAbsolutePath());
//        assertNotNull(platform.getGemManager().findGemExecutable("rdebug-ide"));
//    }

}
