/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2007 Sun
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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JButton;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.ruby.platform.RubyInstallation;
import org.netbeans.api.ruby.platform.RubyPlatform;
import org.netbeans.modules.gsfret.source.usages.ClassIndexManager;
import org.netbeans.modules.ruby.platform.RubyExecution;
import org.netbeans.modules.ruby.platform.Util;
import org.netbeans.modules.ruby.platform.execution.ExecutionDescriptor;
import org.netbeans.modules.ruby.platform.execution.ExecutionService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.ErrorManager;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;

/**
 * Class which handles gem interactions - executing gem, installing, uninstalling, etc.
 *
 * @todo Use the new ExecutionService to do process management.
 *
 * @author Tor Norbye
 */
public final class GemManager {

    private static final Logger LOGGER = Logger.getLogger(GemManager.class.getName());
    
    /** Directory inside the GEM_HOME directory. */
    private static final String SPECIFICATIONS = "specifications"; // NOI18N
    
    /**
     * Regexp for matching version number in gem packages:  name-x.y.z (we need
     * to pull out x,y,z such that we can do numeric comparisons on them)
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(-\\S+)?"); // NOI18N
    
    private static final boolean PREINDEXING = Boolean.getBoolean("gsf.preindexing");

    private static boolean SKIP_INDEX_LIBS = System.getProperty("ruby.index.nolibs") != null; // NOI18N
    private static boolean SKIP_INDEX_GEMS = System.getProperty("ruby.index.nogems") != null; // NOI18N

    /**
     * Extension of files containing gems specification residing in {@link
     * #SPECIFICATIONS}.
     */
    private static final String DOT_GEM_SPEC = ".gemspec"; // NOI18N
    
    private Map<String, Map<String, File>> gemFiles;
    private Map<String, String> gemVersions;
    private Map<String, URL> gemUrls;
    private Set<URL> nonGemUrls;
   
    /**
     * Used by tests.
     * <p>
     * <em>FIXME</em>: get rid of this
     */
    public static String TEST_GEM_HOME;
    
    /** Share over invocations of the dialog since these are slow to compute */
    private static List<Gem> installed;
    
    /** Share over invocations of the dialog since these are ESPECIALLY slow to compute */
    private static List<Gem> available;

    private String gem;
    private FileObject gemHomeFo;
    private String gemHomeUrl;
    private String rake;
    private String rails;
    private String rdoc;

    private final RubyPlatform platform;
    
    public GemManager(final RubyPlatform platform) {
        this.platform = platform;
    }
    
    private String getGemMissingMessage() {
        if (Utilities.isMac() && "/usr/bin/ruby".equals(platform.getInterpreter())) { // NOI18N
            String version = System.getProperty("os.version"); // NOI18N
            if (version == null || version.startsWith("10.4")) { // Only a problem on Tiger // NOI18N
                return NbBundle.getMessage(GemAction.class, "GemMissingMac");
            }
        }
        return NbBundle.getMessage(GemAction.class, "GemMissing");
    }
    
    /**
     * Return null if there are no problems running gem. Otherwise return
     * an error message which describes the problem.
     */
    public String getGemProblem() {
        String gem = getGem();
        
        if (gem == null) {
            return getGemMissingMessage();
        }
        
        String gemDirPath = getGemDir();
        if (gemDirPath == null) {
            // edge case, misconfiguration? gem tool is installed but repository is not found
            return NbBundle.getMessage(GemAction.class, "CannotFindGemRepository");
        }

        File gemDir = new File(gemDirPath);
        
        if (!gemDir.exists()) {
            // Is this possible? (Installing gems, but no gems installed yet
            return null;
        }
        
        if (!gemDir.canWrite()) {
            return NbBundle.getMessage(GemAction.class, "GemNotWritable");
        }
        
        return null;
    }
    
    public String getGemDir() {
        return getGemDir(true);
    }
    
    /**
     * Return the gem directory for the current ruby installation.
     * Returns the gem root, not the gem subdirectory.
     * Not cached.
     */
    public String getGemDir(boolean canonical) {
        String gemdir = null;
        
        String gemHome = TEST_GEM_HOME; // test hook
        // XXX: do not use GEM_HOME for bundle JRuby for now
        if (!platform.isDefault() && gemHome == null) {
            gemHome = System.getenv().get("GEM_HOME"); // NOI18N
        }
        if (gemHome != null) {
            File lib = new File(gemHome); // NOI18N
            if (!lib.isDirectory()) {
                LOGGER.finest("Cannot find Gems repository. \"" + lib + "\" does not exist or is not a directory."); // NOI18N
                // Fall through and try the Ruby interpreter's area
            } else {
                gemHomeFo = FileUtil.toFileObject(lib);
                return lib.getAbsolutePath();
            }
        }
        
        File rubyHome = platform.getHome(canonical);
        assert rubyHome != null : "rubyHome not null for " + platform;

        File libGems = new File(platform.getLib() + File.separator + "ruby" +
                File.separator + "gems");
        File defaultGemDir = new File(libGems, RubyPlatform.DEFAULT_RUBY_RELEASE);

        if (defaultGemDir.isDirectory()) {
            return defaultGemDir.getAbsolutePath();
        }

        // Special case for Debian: /usr/share/doc/rubygems/README.Debian documents
        // a special location for gems
        if ("/usr".equals(rubyHome.getPath())) { // NOI18N
            File varGem = new File("/var/lib/gems/1.8"); // NOI18N
            if (varGem.exists()) {
                gemHomeFo = FileUtil.toFileObject(varGem);
                return varGem.getPath();
            }
        }

        // Search for a numbered directory
        File[] children = libGems.listFiles();
        if (children != null) {
            for (File c : children) {
                if (!c.isDirectory()) {
                    continue;
                }

                if (c.getName().matches("\\d+\\.\\d+")) { // NOI18N
                    gemHomeFo = FileUtil.toFileObject(c);
                    gemdir = c.getAbsolutePath();
                    break;
                }
            }
        }

        if ((gemdir == null) && (children != null) && (children.length > 0)) {
            gemHomeFo = FileUtil.toFileObject(children[0]);
            gemdir = children[0].getAbsolutePath();
        }

        return gemdir;
    }

    /** Return > 0 if version1 is greater than version 2, 0 if equal and -1 otherwise */
    public static int compareGemVersions(String version1, String version2) {
        if (version1.equals(version2)) {
            return 0;
        }

        Matcher matcher1 = VERSION_PATTERN.matcher(version1);

        if (matcher1.matches()) {
            int major1 = Integer.parseInt(matcher1.group(1));
            int minor1 = Integer.parseInt(matcher1.group(2));
            int micro1 = Integer.parseInt(matcher1.group(3));

            Matcher matcher2 = VERSION_PATTERN.matcher(version2);

            if (matcher2.matches()) {
                int major2 = Integer.parseInt(matcher2.group(1));
                int minor2 = Integer.parseInt(matcher2.group(2));
                int micro2 = Integer.parseInt(matcher2.group(3));

                if (major1 != major2) {
                    return major1 - major2;
                }

                if (minor1 != minor2) {
                    return minor1 - minor2;
                }

                if (micro1 != micro2) {
                    return micro1 - micro2;
                }
            } else {
                // TODO uh oh
                //assert false : "no version match on " + version2;
            }
        } else {
            // TODO assert false : "no version match on " + version1;
        }

        // Just do silly alphabetical comparison
        return version1.compareTo(version2);
    }

    /**
     * Checks whether a gem with the given name is installed in the gem
     * repository used by the currently set Ruby interpreter.
     *
     * @param gemName name of a gem to be checked
     * @return <tt>true</tt> if installed; <tt>false</tt> otherwise
     */
    public boolean isGemInstalled(final String gemName) {
        return getVersion(gemName) != null;
    }
    
    /**
     * Checks whether a gem with the given name and the given version is
     * installed in the gem repository used by the currently set Ruby
     * interpreter.
     *
     * @param gemName name of a gem to be checked
     * @param version version of the gem to be checked
     * @return <tt>true</tt> if installed; <tt>false</tt> otherwise
     */
    public boolean isGemInstalled(final String gemName, final String version) {
        String currVersion = getVersion(gemName);
        return currVersion != null && GemManager.compareGemVersions(version, currVersion) <= 0;
    }

    public String getVersion(String gemName) {
        // TODO - use gemVersions map instead!
        initGemList();

        if (gemFiles == null) {
            return null;
        }

        Map<String, File> highestVersion = gemFiles.get(gemName);

        if ((highestVersion == null) || (highestVersion.size() == 0)) {
            return null;
        }

        return highestVersion.keySet().iterator().next();
    }

    private void initGemList() {
        if (gemFiles == null) {
            // Initialize lazily
            String gemDir = getGemDir();
            if (gemDir == null) {
                return;
            }
            File specDir = new File(gemDir, SPECIFICATIONS);

            if (specDir.exists()) {
                LOGGER.finest("Initializing \"" + gemDir + "\" repository");
                // Add each of */lib/
                File[] gems = specDir.listFiles();
                gems = chooseGems(gems);
            } else {
                LOGGER.finest("Cannot find Gems repository. \"" + gemDir + "\" does not exist or is not a directory."); // NOI18N
            }
        }
    }

    /** 
     * Given a list of files that may represent gems, choose the most recent
     * version of each.
     */
    private File[] chooseGems(File[] gems) {
        gemFiles = new HashMap<String, Map<String, File>>();

        for (File f : gems) {
            // See if it looks like a gem
            String n = f.getName();
            if (!n.endsWith(DOT_GEM_SPEC)) {
                continue;
            }

            n = n.substring(0, n.length()-DOT_GEM_SPEC.length());
            
            int dashIndex = n.lastIndexOf('-');
            
            if (dashIndex == -1) {
                // Probably not a gem
                continue;
            }
            
            String name;
            String version;

            if (dashIndex < n.length()-1 && Character.isDigit(n.charAt(dashIndex+1))) {
                // It's a gem without a platform suffix such as -mswin or -ruby
                name = n.substring(0, dashIndex);
                version = n.substring(dashIndex + 1);
            } else {
                String nosuffix = n.substring(0, dashIndex);
                int versionIndex = nosuffix.lastIndexOf('-');
                if (versionIndex != -1) {
                    name = n.substring(0, versionIndex);
                    version = n.substring(versionIndex+1, dashIndex);
                } else {
                    name = n.substring(0, dashIndex);
                    version = n.substring(dashIndex + 1);
                }
            }

            Map<String, File> nameMap = gemFiles.get(name);

            if (nameMap == null) {
                nameMap = new HashMap<String, File>();
                gemFiles.put(name, nameMap);
                nameMap.put(version, f);
            } else {
                // Decide whether this version is more recent than the one already there
                String oldVersion = nameMap.keySet().iterator().next();

                if (GemManager.compareGemVersions(version, oldVersion) > 0) {
                    // New version is higher
                    nameMap.clear();
                    nameMap.put(version, f);
                }
            }
        }

        List<File> result = new ArrayList<File>();

        for (Map<String, File> map : gemFiles.values()) {
            for (File f : map.values()) {
                result.add(f);
            }
        }

        return result.toArray(new File[result.size()]);
    }

    public Set<String> getInstalledGemsFiles() {
        initGemList();

        if (gemFiles == null) {
            return Collections.emptySet();
        }

        return gemFiles.keySet();
    }

    public List<String> reload() {
        List<String> errors = new ArrayList<String>(500);
        installed = new ArrayList<Gem>();
        available = new ArrayList<Gem>();
        refreshList(installed, available, errors);
        return errors;
    }
    
    public List<Gem> getInstalledGems() {
        return installed;
    }
    
    /**
     * <em>WARNING:</em> Slow! Synchronous gem execution.
     * 
     * @param errors list to which the errors, which happen during gems
     *        reload, will be accumulated 
     */
    public List<Gem> reloadInstalledGems(List<String> errors) {
        installed = new ArrayList<Gem>(40);
        refreshList(installed, null, errors);
        return installed;
    }
    
    public boolean haveGem() {
        return getGem() != null;
    }
    
    public List<Gem> getAvailableGems() {
        return available;
    }
    
    /** WARNING: Slow! Synchronous gem execution. */
    public List<Gem> reloadAvailableGems(List<String> lines) {
        available = new ArrayList<Gem>(300);
        refreshList(null, available, lines);
        return available;
    }
    
    public boolean hasUptodateAvailableList() {
        return available != null;
    }

    /**
     * @param errors list to which the errors, which happen during gems
     *        reload, will be accumulated 
     */
    private void refreshList(final List<Gem> localList, final List<Gem> remoteList, final List<String> errors) {
        if (localList != null) {
            localList.clear();
        }
        if (remoteList != null) {
            remoteList.clear();
        }
        
        // Install the given gem
        List<String> argList = new ArrayList<String>();
        
        if (localList != null && remoteList != null) {
            argList.add("--both"); // NOI18N
        } else if (localList != null) {
            argList.add("--local"); // NOI18N
        } else {
            assert remoteList != null;
            argList.add("--remote"); // NOI18N
        }
        
        String[] args = argList.toArray(new String[argList.size()]);
        List<String> lines = new ArrayList<String>(3000);
        boolean ok = gemRunner("list", null, null, lines, args); // NOI18N
        
        if (ok) {
            parseGemList(lines, localList, remoteList);
            
            // Sort the list
            if (localList != null) {
                Collections.sort(localList);
            }
            if (remoteList != null) {
                Collections.sort(remoteList);
            }
        } else {
            // Produce the error list
            boolean inErrors = false;
            for (String line : lines) {
                if (inErrors) {
                    errors.add(line);
                } else if (line.startsWith("***") || line.startsWith(" ") || line.trim().length() == 0) { // NOI18N
                    continue;
                } else if (!line.matches("[a-zA-Z\\-]+ \\(([0-9., ])+\\)\\s?")) { // NOI18N
                    errors.add(line);
                    inErrors = true;
                }
            }
        }
    }
    
    private void parseGemList(List<String> lines, List<Gem> localList, List<Gem> remoteList) { 
        Gem gem = null;
        boolean listStarted = false;
        boolean inLocal = false;
        boolean inRemote = false;
        
        for (String line : lines) {
            if (line.length() == 0) {
                gem = null;
                
                continue;
            }
            
            if (line.startsWith("*** REMOTE GEMS")) { // NOI18N
                inRemote = true;
                inLocal = false;
                listStarted = true;
                gem = null;
                continue;
            } else if (line.startsWith("*** LOCAL GEMS")) { // NOI18N
                inRemote = false;
                inLocal = true;
                listStarted = true;
                gem = null;
                continue;
            }
            
            if (!listStarted) {
                // Skip status messages etc.
                continue;
            }
            
            if (Character.isWhitespace(line.charAt(0))) {
                if (gem != null) {
                    String description = line.trim();
                    
                    if (gem.getDescription() == null) {
                        gem.setDescription(description);
                    } else {
                        gem.setDescription(gem.getDescription() + " " + description); // NOI18N
                    }
                }
            } else {
                if (line.charAt(0) == '.') {
                    continue;
                }
                
                // Should be a gem - but could be an error message!
                int versionIndex = line.indexOf('(');
                
                if (versionIndex != -1) {
                    String name = line.substring(0, versionIndex).trim();
                    int endIndex = line.indexOf(')');
                    String versions;
                    
                    if (endIndex != -1) {
                        versions = line.substring(versionIndex + 1, endIndex);
                    } else {
                        versions = line.substring(versionIndex);
                    }
                    
                    gem = new Gem(name, inLocal ? versions : null, inLocal ? null : versions);
                    if (inLocal) {
                        localList.add(gem);
                    } else {
                        assert inRemote;
                        remoteList.add(gem);
                    }
                } else {
                    gem = null;
                }
            }
        }
    }
    
    /** Non-blocking gem executor which also provides progress UI etc. */
    private void asynchGemRunner(final Component parent, final String description,
            final String successMessage, final String failureMessage, final List<String> lines,
            final Runnable successCompletionTask, final String command, final String... commandArgs) {
        final Cursor originalCursor;
        if (parent != null) {
            originalCursor = parent.getCursor();
            Cursor busy = Utilities.createProgressCursor(parent);
            parent.setCursor(busy);
        } else {
            originalCursor = null;
        }
        
        final ProgressHandle progressHandle = null;
        final boolean interactive = true;
        final JButton closeButton = new JButton(NbBundle.getMessage(GemManager.class, "CTL_Close"));
        final JButton cancelButton =
                new JButton(NbBundle.getMessage(GemManager.class, "CTL_Cancel"));
        closeButton.getAccessibleContext()
                .setAccessibleDescription(NbBundle.getMessage(GemManager.class, "AD_Close"));
        
        Object[] options = new Object[] { closeButton, cancelButton };
        closeButton.setEnabled(false);
        
        final GemProgressPanel progress =
                new GemProgressPanel(NbBundle.getMessage(GemManager.class, "GemPleaseWait"));
        progress.getAccessibleContext().setAccessibleDescription(
                NbBundle.getMessage(GemManager.class, "GemProgressPanel.AccessibleContext.accessibleDescription"));

        DialogDescriptor descriptor =
                new DialogDescriptor(progress, description, true, options, closeButton,
                DialogDescriptor.DEFAULT_ALIGN, new HelpCtx(GemManager.class), null); // NOI18N
        descriptor.setModal(true);
        
        final Process[] processHolder = new Process[1];
        final Dialog dlg = DialogDisplayer.getDefault().createDialog(descriptor);

        
        closeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                dlg.setVisible(false);
                dlg.dispose();
                if (parent != null) parent.setCursor(originalCursor);
            }
        });
        
        Runnable runner =
                new Runnable() {
            public void run() {
                try {
                    boolean succeeded =
                            gemRunner(command, progress, processHolder, lines, commandArgs);
                    
                    closeButton.setEnabled(true);
                    cancelButton.setEnabled(false);
                    
                    progress.done(succeeded ? successMessage : failureMessage);
                    
                    if (succeeded && (successCompletionTask != null)) {
                        successCompletionTask.run();
                    }
                } finally {
                    if (parent != null) parent.setCursor(originalCursor);
                }
            }
        };
        
        RequestProcessor.getDefault().post(runner, 50);
        
        dlg.setVisible(true);
        
        if ((descriptor.getValue() == DialogDescriptor.CANCEL_OPTION) ||
                (descriptor.getValue() == cancelButton)) {
            if (parent != null) parent.setCursor(originalCursor);
            cancelButton.setEnabled(false);
            
            Process process = processHolder[0];
            
            if (process != null) {
                process.destroy();
                dlg.setVisible(false);
                dlg.dispose();
            }
        }
    }
    
    private boolean gemRunner(String command, GemProgressPanel progressPanel,
            Process[] processHolder, List<String> lines, String... commandArgs) {
        String gemProblem = getGemProblem();
        if (gemProblem != null) {
            NotifyDescriptor nd = new NotifyDescriptor.Message(gemProblem, NotifyDescriptor.Message.ERROR_MESSAGE);
            DialogDisplayer.getDefault().notify(nd);
            return false;
        }
        
        // Install the given gem
        String gemCmd = getGem();
        List<String> argList = new ArrayList<String>();
        
        File cmd = new File(platform.getInterpreter());
        
        if (!cmd.getName().startsWith("jruby") || RubyExecution.LAUNCH_JRUBY_SCRIPT) { // NOI18N
            argList.add(cmd.getPath());
        }
        
        argList.addAll(RubyExecution.getRubyArgs(platform));
        
        argList.add(gemCmd);
        argList.add(command);
        
        for (String arg : commandArgs) {
            argList.add(arg);
        }
        
        String[] args = argList.toArray(new String[argList.size()]);
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.environment().put("GEM_HOME", platform.getGemManager().getGemDir());
        pb.directory(cmd.getParentFile());
        pb.redirectErrorStream(true);

        // TODO: Following unfortunately does not work -- gems blows up. Looks
        // like a RubyGems bug.
        // ERROR:  While executing gem ... (NoMethodError)
        //    undefined method `[]=' for #<Gem::ConfigFile:0xb6c763 @hash={} ,@args=["--remote", "-p", "http://foo.bar:8080"] ,@config_file_name=nil ,@verbose=true>
        //argList.add("--http-proxy"); // NOI18N
        //argList.add(proxy);
        // (If you uncomment the above, move it up above the args = argList.toArray line)
        Util.adjustProxy(pb);

        // PATH additions for JRuby etc.
        new RubyExecution(new ExecutionDescriptor(platform, "gem", pb.directory()).cmd(cmd)).setupProcessEnvironment(pb.environment()); // NOI18N
        
        if (lines == null) {
            lines = new ArrayList<String>(40);
        }
        
        int exitCode = -1;
        
        try {
            ExecutionService.logProcess(pb);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            if (processHolder != null) {
                processHolder[0] = process;
            }
            
            InputStream is = process.getInputStream();
            
            if (progressPanel != null) {
                progressPanel.setProcessInput(process.getOutputStream());
            }
            
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            
            try {
                while (true) {
                    line = br.readLine();
                    
                    if (line == null) {
                        break;
                    }
                    
                    if (progressPanel != null) {
                        // Add "\n" ?
                        progressPanel.appendOutput(line);
                    }
                    
                    lines.add(line);
                }
            } catch (IOException ioe) {
                // When we cancel we call Process.destroy which may quite possibly
                // raise an IO Exception in this thread reading text out of the
                // process. Silently ignore that.
                String message = "*** Gem Process Killed ***\n"; // NOI18N
                lines.add(message);
                
                if (progressPanel != null) {
                    progressPanel.appendOutput(message);
                }
            }
            
            exitCode = process.waitFor();
            
            if (exitCode != 0) {
                try {
                    // This might not be necessary now that I'm
                    // calling ProcessBuilder.redirectErrorStream(true)
                    // but better safe than sorry
                    is = process.getErrorStream();
                    isr = new InputStreamReader(is);
                    br = new BufferedReader(isr);
                    
                    while ((line = br.readLine()) != null) {
                        if (progressPanel != null) {
                            // Add "\n" ?
                            progressPanel.appendOutput(line);
                        }
                        
                        lines.add(line);
                    }
                } catch (IOException ioe) {
                    // When we cancel we call Process.destroy which may quite possibly
                    // raise an IO Exception in this thread reading text out of the
                    // process. Silently ignore that.
                    String message = "*** Gem Process Killed ***\n"; // NOI18N
                    lines.add(message);
                    
                    if (progressPanel != null) {
                        progressPanel.appendOutput(message);
                    }
                }
            }
        } catch (IOException ex) {
            ErrorManager.getDefault().notify(ex);
        } catch (InterruptedException ex) {
            ErrorManager.getDefault().notify(ex);
        }
        
        boolean succeeded = exitCode == 0;
        
        return succeeded;
    }
    
    /**
     * Install the latest version of the given gem with dependencies and refresh
     * IDE caches accordingly after the gem is installed.
     *
     * @param gem gem to install
     * @param rdoc if true, generate rdoc as part of the installation
     * @param ri if true, generate ri data as part of the installation
     */
    public void installGem(final String gem, final boolean rdoc, final boolean ri) {
        final Gem[] gems = new Gem[] {
            new Gem(gem, null, null)
        };
        Runnable installationComplete = new Runnable() {
            public void run() {
                platform.recomputeRoots();
            }
        };
        install(gems, null, rdoc, ri, null, true, true, installationComplete);
    }

    /**
     * Install the given gems.
     *
     * @param gem Gem description for the gem to be installed. Only the name is relevant.
     * @param parent For asynchronous tasks, provide a parent Component that will have progress dialogs added,
     *   a possible cursor change, etc.
     * @param progressHandle If the task is not asynchronous, use the given handle for progress notification.
     * @param asynchronous If true, run the gem task asynchronously - returning immediately and running the gem task
     *    in a background thread. A progress bar and message will be displayed (along with the option to view the
     *    gem output). If the exit code is normal, the completion task will be run at the end.
     * @param asyncCompletionTask If asynchronous is true and the gem task completes normally, this task will be run at the end.
     * @param rdoc If true, generate rdoc as part of the installation
     * @param ri If true, generate ri data as part of the installation
     * @param version If non null, install the specified version rather than the latest available version
     */
    public boolean install(Gem[] gems, Component parent, boolean rdoc, boolean ri,
            String version, boolean includeDeps, boolean asynchronous,
            Runnable asyncCompletionTask) {
        // Install the given gem
        List<String> argList = new ArrayList<String>();
        
        for (Gem gem : gems) {
            argList.add(gem.getName());
        }
        
        //argList.add("--verbose"); // NOI18N
        if (!rdoc) {
            argList.add("--no-rdoc"); // NOI18N
        }
        
        if (!ri) {
            argList.add("--no-ri"); // NOI18N
        }
        
        if (includeDeps) {
            argList.add("--include-dependencies"); // NOI18N
        } else {
            argList.add("--ignore-dependencies"); // NOI18N
        }
        
        argList.add("--version"); // NOI18N
        
        if ((version != null) && (version.length() > 0)) {
            argList.add(version);
        } else {
            argList.add("> 0"); // NOI18N
        }
        
        String[] args = argList.toArray(new String[argList.size()]);
        
        String title = NbBundle.getMessage(GemManager.class, "Installation");
        String success = NbBundle.getMessage(GemManager.class, "InstallationOk");
        String failure = NbBundle.getMessage(GemManager.class, "InstallationFailed");
        String gemCmd = "install"; // NOI18N
        
        if (asynchronous) {
            asynchGemRunner(parent, title, success, failure, null, asyncCompletionTask, gemCmd, args);
            
            return false;
        } else {
            boolean ok = gemRunner(gemCmd, null, null, null, args);
            
            return ok;
        }
    }
    
    /**
     * Uninstall the given gem.
     *
     * @param gem Gem description for the gem to be uninstalled. Only the name is relevant.
     * @param parent For asynchronous tasks, provide a parent Component that will have progress dialogs added,
     *   a possible cursor change, etc.
     * @param progressHandle If the task is not asynchronous, use the given handle for progress notification.
     * @param asynchronous If true, run the gem task asynchronously - returning immediately and running the gem task
     *    in a background thread. A progress bar and message will be displayed (along with the option to view the
     *    gem output). If the exit code is normal, the completion task will be run at the end.
     * @param asyncCompletionTask If asynchronous is true and the gem task completes normally, this task will be run at the end.
     */
    public boolean uninstall(Gem[] gems, Component parent, boolean asynchronous, Runnable asyncCompletionTask) {
        // Install the given gem
        List<String> argList = new ArrayList<String>();
        
        // This string is replaced in the loop below, one gem at a time as we iterate over the
        // deletion results
        int nameIndex = argList.size();
        argList.add("placeholder"); // NOI18N
        
        //argList.add("--verbose"); // NOI18N
        argList.add("--all"); // NOI18N
        argList.add("--executables"); // NOI18N
        argList.add("--ignore-dependencies"); // NOI18N
        
        String[] args = argList.toArray(new String[argList.size()]);
        String title = NbBundle.getMessage(GemManager.class, "Uninstallation");
        String success = NbBundle.getMessage(GemManager.class, "UninstallationOk");
        String failure = NbBundle.getMessage(GemManager.class, "UninstallationFailed");
        String gemCmd = "uninstall"; // NOI18N
        
        if (asynchronous) {
            for (Gem gem : gems) {
                args[nameIndex] = gem.getName();
                asynchGemRunner(parent, title, success, failure, null, asyncCompletionTask, gemCmd,
                        args);
            }
            
            return false;
        } else {
            boolean ok = true;
            
            for (Gem gem : gems) {
                args[nameIndex] = gem.getName();
                
                if (!gemRunner(gemCmd, null, null, null, args)) {
                    ok = false;
                }
            }
            
            return ok;
        }
    }
    
    /**
     * Update the given gem, or all gems if gem == null
     *
     * @param gem Gem description for the gem to be uninstalled. Only the name is relevant. If null, all installed gems
     *    will be updated.
     * @param parent For asynchronous tasks, provide a parent Component that will have progress dialogs added,
     *   a possible cursor change, etc.
     * @param progressHandle If the task is not asynchronous, use the given handle for progress notification.
     * @param asynchronous If true, run the gem task asynchronously - returning immediately and running the gem task
     *    in a background thread. A progress bar and message will be displayed (along with the option to view the
     *    gem output). If the exit code is normal, the completion task will be run at the end.
     * @param asyncCompletionTask If asynchronous is true and the gem task completes normally, this task will be run at the end.
     */
    public boolean update(Gem[] gems, Component parent, boolean rdoc,
            boolean ri, boolean asynchronous, Runnable asyncCompletionTask) {
        // Install the given gem
        List<String> argList = new ArrayList<String>();
        
        if (gems != null) {
            for (Gem gem : gems) {
                argList.add(gem.getName());
            }
        }
        
        argList.add("--verbose"); // NOI18N
        
        if (!rdoc) {
            argList.add("--no-rdoc"); // NOI18N
        }
        
        if (!ri) {
            argList.add("--no-ri"); // NOI18N
        }
        
        argList.add("--include-dependencies"); // NOI18N
        
        String[] args = argList.toArray(new String[argList.size()]);
        
        String title = NbBundle.getMessage(GemManager.class, "Update");
        String success = NbBundle.getMessage(GemManager.class, "UpdateOk");
        String failure = NbBundle.getMessage(GemManager.class, "UpdateFailed");
        String gemCmd = "update"; // NOI18N
        
        if (asynchronous) {
            asynchGemRunner(parent, title, success, failure, null, asyncCompletionTask, gemCmd, args);
            
            return false;
        } else {
            boolean ok = gemRunner(gemCmd, null, null, null, args);
            return ok;
        }
    }

    public String getGemHomeUrl() {
        if (gemHomeUrl == null) {
            String libGemDir = getGemDir();
            if (libGemDir != null) {
                try {
                    File r = new File(libGemDir);
                    if (r != null) {
                        gemHomeUrl = r.toURI().toURL().toExternalForm();
                    }
                } catch (MalformedURLException mue) {
                    Exceptions.printStackTrace(mue);
                }
            }
        }

        return gemHomeUrl;
    }

    /**
     * Try to find a path to the <tt>toFind</tt> executable in the "Ruby
     * specific" manner.
     *
     * @param toFind executable to be find, e.g. rails, rake, ...
     * @return path to the found executable; might be <tt>null</tt> if not
     *         found.
     */
    public String findGemExecutable(final String toFind) {
        String exec = null;
        boolean canonical = true; // default
        do {
            String binDir = platform.getBinDir();
            if (binDir != null) {
                LOGGER.finest("Looking for '" + toFind + "' gem executable; used intepreter: '" + platform.getInterpreter() + "'"); // NOI18N
                exec = GemManager.findExecutable(binDir, toFind);
            } else {
                LOGGER.warning("Could not find Ruby interpreter executable when searching for '" + toFind + "'"); // NOI18N
            }
            if (exec == null) {
                String libGemBinDir = getGemDir(canonical) + File.separator + "bin"; // NOI18N
                exec = GemManager.findExecutable(libGemBinDir, toFind);
            }
            canonical ^= true;
        } while (!canonical && exec == null);
        // try to find a gem on system path - see issue 116219
        if (exec == null) {
            exec = findOnPath(toFind);
        }
        // try *.bat commands on Windows
        if (exec == null && !toFind.endsWith(".bat") && Utilities.isWindows()) { // NOI18N
            exec = findGemExecutable(toFind + ".bat"); // NOI18N
        }
        return exec;
    }

    public String getAutoTest() {
        return findGemExecutable("autotest"); // NOI18N
    }

    public boolean isValidAutoTest(boolean warn) {
        String autoTest = getAutoTest();
        boolean valid = (autoTest != null) && new File(autoTest).exists();

        if (warn && !valid) {
            String msg = NbBundle.getMessage(RubyInstallation.class, "NotInstalledCmd", "autotest"); // NOI18N
            NotifyDescriptor nd =
                    new NotifyDescriptor.Message(msg, NotifyDescriptor.Message.ERROR_MESSAGE);
            DialogDisplayer.getDefault().notify(nd);
        }

        return valid;
    }

    private static String findExecutable(final String dir, final String toFind) {
        String exec = dir + File.separator + toFind;
        if (!new File(exec).isFile()) {
            LOGGER.finest("'" + exec + "' is not a file."); // NOI18N
            exec = null;
        }
        return exec;
    }
    
    /**
     * Return path to the <em>gem</em> tool if it does exist.
     *
     * @return path to the <em>gem</em> tool; might be <tt>null</tt> if not
     *         found.
     */
    public String getGem() {
        if (gem == null) {
            String bin = platform.getBinDir();
            if (bin != null) {
                gem = bin + File.separator + "gem"; // NOI18N
                if (!new File(gem).isFile()) {
                    gem = null;
                }
            }
        }
        if (gem == null) {
            gem = GemManager.findOnPath("gem"); // NOI18N
        }
        return gem;
    }
    
    private static String findOnPath(final String toFind) {
        String rubyLib = System.getenv("PATH"); // NOI18N
        if (rubyLib != null) {
            String[] paths = rubyLib.split("[:;]"); // NOI18N
            for (String path : paths) {
                String result = path + File.separator + toFind;
                if (new File(result).isFile()) {
                    return result;
                }
            }
        }
        return null;
    }

    public FileObject getRubyLibGemDirFo() {
        initGemList(); // Ensure getRubyLibGemDir has been called, which initialized gemHomeFo
        return gemHomeFo;
    }

    public String getRake() {
        if (rake == null) {
            rake = findGemExecutable("rake"); // NOI18N

            if (rake != null && !(new File(rake).exists()) && getVersion("rake") != null) { // NOI18N
                // On Windows, rake does funny things - you may only get a rake.bat
                InstalledFileLocator locator = InstalledFileLocator.getDefault();
                File f =
                        locator.locate("modules/org-netbeans-modules-ruby-project.jar", // NOI18N
                        null, false); // NOI18N

                if (f == null) {
                    throw new RuntimeException("Can't find cluster"); // NOI18N
                }

                f = new File(f.getParentFile().getParentFile().getAbsolutePath() + File.separator +
                        "rake"); // NOI18N

                try {
                    rake = f.getCanonicalPath();
                } catch (IOException ioe) {
                    Exceptions.printStackTrace(ioe);
                }
            }
        }

        return rake;
    }

    public boolean isValidRake(boolean warn) {
        String rakePath = getRake();
        boolean valid = (rakePath != null) && new File(rakePath).exists();

        if (warn && !valid) {
            String msg = NbBundle.getMessage(RubyInstallation.class, "NotInstalledCmd", "rake"); // NOI18N
            NotifyDescriptor nd =
                    new NotifyDescriptor.Message(msg, NotifyDescriptor.Message.ERROR_MESSAGE);
            DialogDisplayer.getDefault().notify(nd);
        }

        return valid;
    }

    public String getRDoc() {
        if (rdoc == null) {
            rdoc = findGemExecutable("rdoc"); // NOI18N
            if (rdoc == null && !platform.isJRuby()) {
                String name = new File(platform.getInterpreter(true)).getName();
                if (name.startsWith("ruby")) { // NOI18N
                    String suffix = name.substring(4);
                    // Try to find with suffix (#120441)
                    rdoc = findGemExecutable("rdoc" + suffix);
                }
            }
        }
        return rdoc;
    }

    public String getRails() {
        if (rails == null) {
            rails = findGemExecutable("rails"); // NOI18N
        }
        return rails;
    }

    public boolean isValidRails(boolean warn) {
        String railsPath = getRails();
        boolean valid = (railsPath != null) && new File(railsPath).exists();

        if (warn && !valid) {
            String msg = NbBundle.getMessage(RubyInstallation.class, "NotInstalledCmd", "rails"); // NOI18N
            NotifyDescriptor nd =
                    new NotifyDescriptor.Message(msg, NotifyDescriptor.Message.ERROR_MESSAGE);
            DialogDisplayer.getDefault().notify(nd);
        }

        return valid;
    }

    /** Return other load path URLs (than the gem ones returned by {@link #getGemUrls} to add for the platform
     * such as the basic ruby 1.8 libraries, the site_ruby libraries, and the stub libraries for 
     * the core/builtin classes.
     * 
     * @return a set of URLs
     */
    public Set<URL> getNonGemLoadPath() {
        if (nonGemUrls == null) {
            initializeUrlMaps();
        }
        
        return nonGemUrls;
    }
    
    /** 
     * Return a map from gem name to the version string, which is of the form
     * {@code <major>.<minor>.<tiny>[-<platform>]}, such as 1.2.3 and 1.13.5-ruby
     */
    public Map<String, String> getGemVersions() {
        if (gemVersions == null) {
            initializeUrlMaps();
        }

        return gemVersions;
    }

    /** 
     * Return a map from gem name to the URL for the lib root of the current gems
     */
    public Map<String, URL> getGemUrls() {
        if (gemUrls == null) {
            initializeUrlMaps();
        }

        return gemUrls;
    }

    private void initializeUrlMaps() {
        File rubyHome = platform.getHome();

        if (rubyHome == null || !rubyHome.exists()) {
            gemVersions = Collections.emptyMap();
            gemUrls = Collections.emptyMap();
            nonGemUrls = Collections.emptySet();
            return;
        }
        try {
            gemUrls = new HashMap<String, URL>(60);
            gemVersions = new HashMap<String, String>(60);
            nonGemUrls = new HashSet<URL>(12);

            FileObject rubyStubs = platform.getRubyStubs();

            if (rubyStubs != null) {
                try {
                    nonGemUrls.add(rubyStubs.getURL());
                } catch (FileStateInvalidException fsie) {
                    Exceptions.printStackTrace(fsie);
                }
            }

            // Install standard libraries
            // lib/ruby/1.8/ 
            if (!SKIP_INDEX_LIBS) {
                String rubyLibDir = platform.getLibDir();
                if (rubyLibDir != null) {
                    File libs = new File(rubyLibDir);
                    assert libs.exists() && libs.isDirectory();
                    nonGemUrls.add(libs.toURI().toURL());
                }
            }

            // Install gems.
            if (!SKIP_INDEX_GEMS) {
                initGemList();
                if (PREINDEXING) {
                    String gemDir = getGemDir();
                    File specDir = new File(gemDir, "gems"); // NOI18N

                    if (specDir.exists()) {
                        File[] gems = specDir.listFiles();
                        for (File f : gems) {
                            if (f.getName().indexOf('-') != -1) {
                                File lib = new File(f, "lib"); // NOI18N

                                if (lib.exists() && lib.isDirectory()) {
                                    URL url = lib.toURI().toURL();
                                    nonGemUrls.add(url);
                                }
                            }
                        }
                    }
                } else if (gemFiles != null) {
                    Set<String> gems = gemFiles.keySet();
                    for (String name : gems) {
                        Map<String, File> m = gemFiles.get(name);
                        assert m.keySet().size() == 1;
                        File f = m.values().iterator().next();
                        // Points to the specification file
                        assert f.getName().endsWith(DOT_GEM_SPEC);
                        String filename = f.getName().substring(0,
                                f.getName().length() - DOT_GEM_SPEC.length());
                        File lib = new File(f.getParentFile().getParentFile(), "gems" + // NOI18N
                                File.separator + filename + File.separator + "lib"); // NOI18N

                        if (lib.exists() && lib.isDirectory()) {
                            URL url = lib.toURI().toURL();
                            gemUrls.put(name, url);
                            String version = m.keySet().iterator().next();
                            gemVersions.put(name, version);
                        }
                    }
                }
            }

            // Install site ruby - this is where rubygems lives for example
            if (!SKIP_INDEX_LIBS) {
                String rubyLibSiteDir = platform.getRubyLibSiteDir();

                if (rubyLibSiteDir != null) {
                    File siteruby = new File(rubyLibSiteDir);

                    if (siteruby.exists() && siteruby.isDirectory()) {
                        nonGemUrls.add(siteruby.toURI().toURL());
                    }
                }
            }

            // During development only:
            gemUrls = Collections.unmodifiableMap(gemUrls);
            gemVersions = Collections.unmodifiableMap(gemVersions);
            nonGemUrls = Collections.unmodifiableSet(nonGemUrls);

            // Register boot roots. This is a bit of a hack.
            // I need to find a better way to distinguish source directories
            // from boot (library, gems, etc.) directories at the scanning and indexing end.
            ClassIndexManager mgr = ClassIndexManager.getDefault();
            List<URL> roots = new ArrayList<URL>(gemUrls.size() + nonGemUrls.size());
            roots.addAll(gemUrls.values());
            roots.addAll(nonGemUrls);
            mgr.setBootRoots(roots);
        } catch (MalformedURLException mue) {
            Exceptions.printStackTrace(mue);
        }
    }
}
