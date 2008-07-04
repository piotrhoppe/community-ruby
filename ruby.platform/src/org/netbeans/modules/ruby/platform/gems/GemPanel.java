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

import java.awt.Component;
import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import org.netbeans.api.options.OptionsDisplayer;
import org.netbeans.api.ruby.platform.RubyPlatform;
import org.netbeans.modules.ruby.platform.PlatformComponentFactory;
import org.netbeans.modules.ruby.platform.RubyPlatformCustomizer;
import org.netbeans.modules.ruby.platform.RubyPreferences;
import org.netbeans.modules.ruby.platform.Util;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileUtil;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * XXX needs a rewrite, little messy.
 * 
 * @todo Use a table instead of a list for the gem lists, use checkboxes to choose
 *   items to be uninstalled, and show the installation date (based
 *   on file timestamps)
 */
public final class GemPanel extends JPanel implements Runnable {
    
    private static final Logger LOGGER = Logger.getLogger(GemPanel.class.getName());
    
    private static final String LAST_GEM_DIRECTORY = "lastLocalGemDirectory"; // NOI18N

    private static final String LAST_PLATFORM_ID = "gemPanelLastPlatformID"; // NOI18N

    static enum TabIndex { UPDATED, INSTALLED, NEW; }
    
    private GemManager gemManager;
    
    private ExecutorService updateTasksQueue;
    private boolean closed;
    
    private List<Gem> installedGems;
    private List<Gem> availableGems;
    private List<Gem> newGems;
    private List<Gem> updatedGems;
    private boolean gemsModified;
    private boolean fetchingLocal;
    private boolean fetchingRemote;

    public GemPanel(String availableFilter) {
        this(availableFilter, null);
    }

    /**
     * Creates a new GemPanel.
     * 
     * @param availableFilter the filter to use for displaying gems, e.g. 
     * <code>"generators$"</code> for displaying only generator gems.
     * @param preselected the platform that should be preselected in the panel; 
     * may be <code>null</code> in which case the last selected platform is preselected.
     */
    public GemPanel(String availableFilter, RubyPlatform preselected) {
        updateTasksQueue = Executors.newSingleThreadExecutor();
        initComponents();
        allVersionsCheckbox.setSelected(RubyPreferences.shallFetchAllVersions());
        descriptionCheckbox.setSelected(RubyPreferences.shallFetchGemDescriptions());
        if (preselected == null) {
            Util.preselectPlatform(platforms, LAST_PLATFORM_ID);
        } else {
            platforms.setSelectedItem(preselected);
        }
        
        RubyPlatform selPlatform = getSelectedPlatform();
        if (selPlatform != null) {
            this.gemManager = selPlatform.getGemManager();
        }

        installedList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        installedList.getSelectionModel().addListSelectionListener(new MyListSelectionListener(installedList, installedDesc, uninstallButton));

        newList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        newList.getSelectionModel().addListSelectionListener(new MyListSelectionListener(newList, newDesc, installButton));

        updatedList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        updatedList.getSelectionModel().addListSelectionListener(new MyListSelectionListener(updatedList, updatedDesc, updateButton));

        if (availableFilter != null) {
            searchNewText.setText(availableFilter);
            gemsTab.setSelectedIndex(TabIndex.NEW.ordinal());
        }

        PlatformComponentFactory.addPlatformChangeListener(platforms, new PlatformComponentFactory.PlatformChangeListener() {
            public void platformChanged() {
                GemPanel.this.gemManager = getSelectedPlatform().getGemManager();
                updateAsynchronously();
            }
        });
        updateAsynchronously();
        
    }
    
    private void updateAsynchronously() {
        RequestProcessor.getDefault().post(this, 300);
    }

    public @Override void removeNotify() {
        closed = true;
        cancelRunningTasks();
        RubyPreferences.getPreferences().put(LAST_PLATFORM_ID, getSelectedPlatform().getID());
        super.removeNotify();
    }
    
    private void cancelRunningTasks() {
        LOGGER.finest("Cancelling all running GemPanel tasks");
        updateTasksQueue.shutdown();
        updateTasksQueue = Executors.newSingleThreadExecutor();
    }
    
    public void run() {
        // This will also update the New and Installed lists because Update depends on these
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                // cancel current update, the platform was changed
                cancelRunningTasks();
                
                boolean loading = PlatformComponentFactory.isLoadingPlatforms(platforms);
                if (loading || !getSelectedPlatform().hasRubyGemsInstalled()) {
                    if (!loading) {
                        gemHomeValue.setForeground(PlatformComponentFactory.INVALID_PLAF_COLOR);
                        gemHomeValue.setText(GemManager.getNotInstalledMessage());
                    }
                    availableGems = Collections.emptyList();
                    installedGems = Collections.emptyList();
                    newGems = Collections.emptyList();
                    fetchingLocal = false;
                    fetchingRemote = false;
                    notifyGemsUpdated();
                    updateList(TabIndex.INSTALLED, true);
                    setEnabledGUI(false);
                } else {
                    gemHomeValue.setText(gemManager.getGemHome());
                    gemHomeValue.setForeground(UIManager.getColor("Label.foreground")); // NOI18N
                    setEnabledGUI(false);
                    refreshUpdated();
                }
            }
        });
    }
    
    private static void updateGemDescription(JTextPane pane, Gem gem) {
        assert SwingUtilities.isEventDispatchThread();

        if (gem == null) {
            pane.setText("");
            return;
        }

        String htmlMimeType = "text/html"; // NOI18N
        pane.setContentType(htmlMimeType);

        StringBuilder sb = new StringBuilder();
        sb.append("<html>"); // NOI18N
        sb.append("<h2>"); // NOI18N
        sb.append(gem.getName());
        sb.append("</h2>\n"); // NOI18N

        if (gem.getInstalledVersions() != null && gem.getAvailableVersions() != null) {
            // It's an update gem
            sb.append("<h3>"); // NOI18N
            sb.append(getMessage("InstalledVersion"));
            sb.append("</h3>"); // NOI18N
            sb.append(gem.getInstalledVersions());

            sb.append("<h3>"); // NOI18N
            sb.append(getMessage("AvailableVersion"));
            sb.append("</h3>"); // NOI18N
            sb.append(gem.getAvailableVersions());
            sb.append("<br>"); // NOI18N
        } else {
            sb.append("<h3>"); // NOI18N
            String version = gem.getInstalledVersions();
            if (version == null) {
                version = gem.getAvailableVersions();
            }
            if (version.indexOf(',') == -1) {
            // TODO I18N
                sb.append(getMessage("Version"));
            } else {
                sb.append(getMessage("Versions"));
            }
            sb.append("</h3>"); // NOI18N
            sb.append(version);
        }

        if (gem.getDescription() != null) {
            sb.append("<h3>"); // NOI18N
            sb.append(getMessage("Description"));
            sb.append("</h3>"); // NOI18N
            sb.append(gem.getDescription());
        }

        sb.append("</html>"); // NOI18N

        pane.setText(sb.toString());
    }

    private void setEnabledGUI(boolean enabled) {
        setEnabled(TabIndex.INSTALLED, enabled);
        setEnabled(TabIndex.NEW, enabled);
        setEnabled(TabIndex.UPDATED, enabled);
    }

    private void setEnabled(TabIndex tab, boolean enabled) {
        gemsTab.setEnabledAt(tab.ordinal(), enabled);
        switch (tab) {
            case NEW:
                reloadNewButton.setEnabled(enabled);
                installButton.setEnabled(enabled);
                newPanel.setEnabled(enabled);
                newList.setEnabled(enabled);
                newSP.setEnabled(enabled);
                searchNewLbl.setEnabled(enabled);
                searchNewText.setEnabled(enabled);
                break;
            case UPDATED:
                updateButton.setEnabled(enabled);
                updateAllButton.setEnabled(enabled);
                reloadReposButton.setEnabled(enabled);
                updatedPanel.setEnabled(enabled);
                updatedList.setEnabled(enabled);
                updatedSP.setEnabled(enabled);
                searchUpdatedLbl.setEnabled(enabled);
                searchUpdatedText.setEnabled(enabled);
                break;
            case INSTALLED:
                reloadInstalledButton.setEnabled(enabled);
                uninstallButton.setEnabled(enabled);
                installedPanel.setEnabled(enabled);
                installedList.setEnabled(enabled);
                installedSP.setEnabled(enabled);
                searchInstLbl.setEnabled(enabled);
                searchInstText.setEnabled(enabled);
                break;
            default:
                throw new IllegalArgumentException();
        }
        boolean everythingDone = newPanel.isEnabled() && updatedPanel.isEnabled() && installedPanel.isEnabled();
        // allow certain actions only when all tabs are updated
        manageButton.setEnabled(everythingDone);
        browseGemHome.setEnabled(everythingDone);
    }

    /**
     * Called when installedGems or availableGems is refreshed; recompute the
     * updated list.
     * @return True iff we're done with the updates
     */
    private synchronized boolean notifyGemsUpdated() {
        assert SwingUtilities.isEventDispatchThread();

        if (!(fetchingRemote || fetchingLocal)) {
            updatedProgress.setVisible(false);
            updatedProgressLabel.setVisible(false);
        }
        if (!fetchingRemote) {
            newProgress.setVisible(false);
            newProgressLabel.setVisible(false);
        }
        if (!fetchingLocal) {
            installedProgress.setVisible(false);
            installedProgressLabel.setVisible(false);
        }

        if (installedGems != null && availableGems != null) {
            Map<String,Gem> nameMap = new HashMap<String,Gem>();
            for (Gem gem : installedGems) {
                nameMap.put(gem.getName(), gem);
            }
            Set<String> installedNames = nameMap.keySet();

            updatedGems = new ArrayList<Gem>();
            newGems = new ArrayList<Gem>();
            for (Gem gem : availableGems) {
                if (installedNames.contains(gem.getName())) {
                    String latestAvailable = gem.getLatestAvailable();
                    Gem installedGem = nameMap.get(gem.getName());
                    String latestInstalled = installedGem.getLatestInstalled();
                    if (Util.compareVersions(latestAvailable, latestInstalled) > 0) {
                        Gem update = new Gem(gem.getName(),
                                installedGem.getInstalledVersions(),
                                latestAvailable);
                        update.setDescription(installedGem.getDescription());
                        updatedGems.add(update);
                    }
                } else {
                    newGems.add(gem);
                }
            }

            updateList(TabIndex.NEW, true);
            updateList(TabIndex.UPDATED, true);
        }

        return !(fetchingRemote || fetchingLocal);
    }

    private void updateList(TabIndex tab, boolean updateCount) {
        assert SwingUtilities.isEventDispatchThread();

        Pattern pattern = null;
        String filter = getGemFilter(tab);
        String lcFilter = null;
        if ((filter != null) && (filter.indexOf('*') != -1 || filter.indexOf('^') != -1 || filter.indexOf('$') != -1)) {
            try {
                pattern = Pattern.compile(filter, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException pse) {
                // Don't treat the filter as a regexp
            }
        } else if (filter != null) {
            lcFilter = filter.toLowerCase(Locale.ENGLISH);
        }
        List<Gem> gems;
        JList list;

        switch (tab) {
            case NEW:
                gems = newGems;
                list = newList;
                break;
            case UPDATED:
                gems = updatedGems;
                list = updatedList;
                break;
            case INSTALLED:
                gems = installedGems;
                list = installedList;
                break;
            default:
                throw new IllegalArgumentException();
        }

        if (gems == null) {
            // attempting to filter before the list has been fetched - ignore
            return;
        }

        DefaultListModel model = new DefaultListModel();
        for (Gem gem : gems) {
            if (filter == null || filter.length() == 0) {
                model.addElement(gem);
            } else if (pattern == null) {
                if (lcFilter != null) {
                    String lcName = gem.getName().toLowerCase();
                    if (lcName.indexOf(lcFilter) != -1) {
                        model.addElement(gem);
                    } else if (gem.getDescription() != null) {
                        String lcDesc =gem.getDescription().toLowerCase();
                        if (lcDesc.indexOf(lcFilter) != -1) {
                            model.addElement(gem);
                        }
                    }
                } else {
                    model.addElement(gem);
                }
            } else if (pattern.matcher(gem.getName()).find() ||
                    (gem.getDescription() != null && pattern.matcher(gem.getDescription()).find())) {
                model.addElement(gem);
            }
        }
        list.clearSelection();
        list.setModel(model);
        list.invalidate();
        list.repaint();
        // This sometimes gives NPEs within setSelectedIndex...
        //        if (!gems.isEmpty()()) {
        //            list.setSelectedIndex(0);
        //        }

        if (updateCount) {
            String tabTitle = gemsTab.getTitleAt(tab.ordinal());
            String originalTabTitle = tabTitle;
            int index = tabTitle.lastIndexOf('(');
            if (index != -1) {
                tabTitle = tabTitle.substring(0, index);
            }
            String count;
            if (model.size() < gems.size()) {
                count = model.size() + "/" + gems.size(); // NOI18N
            } else {
                count = Integer.toString(gems.size());
            }
            tabTitle = tabTitle + "(" + count + ")"; // NOI18N
            if (!tabTitle.equals(originalTabTitle)) {
                gemsTab.setTitleAt(tab.ordinal(), tabTitle);
            }
            setEnabled(tab, true);
        }
    }
    
    /** Return whether any gems were modified - roots should be recomputed after panel is taken down */
    public boolean isModified() {
        return gemsModified;
    }

    private synchronized void refreshInstalled() {
        showProgressBar(installedList, installedDesc, installedProgress, installedProgressLabel);
        fetchingLocal = true;
        setEnabled(TabIndex.INSTALLED, false);
        refreshGemList(TabIndex.INSTALLED);
    }
    
    private synchronized void refreshNew() {
        showProgressBar(newList, newDesc, newProgress, newProgressLabel);
        fetchingRemote = true;
        setEnabled(TabIndex.NEW, false);
        refreshGemList(TabIndex.NEW);
    }

    private void refreshUpdated() {
        showProgressBar(updatedList, updatedDesc, updatedProgress, updatedProgressLabel);
        refreshNew();
        refreshInstalled();
        refreshGemLists();
    }

    private static void showProgressBar(JList list, JTextPane description, JProgressBar progress, JLabel progressLabel) {
        assert SwingUtilities.isEventDispatchThread();

        if (list.getSelectedIndex() != -1) {
            updateGemDescription(description, null);
        }
        progress.setVisible(true);
        progressLabel.setVisible(true);
    }
    
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        gemsTab = new javax.swing.JTabbedPane();
        updatedPanel = new javax.swing.JPanel();
        searchUpdatedText = new javax.swing.JTextField();
        searchUpdatedLbl = new javax.swing.JLabel();
        reloadReposButton = new javax.swing.JButton();
        updatedSP = new javax.swing.JScrollPane();
        updatedList = new javax.swing.JList();
        updateButton = new javax.swing.JButton();
        updateAllButton = new javax.swing.JButton();
        jScrollPane6 = new javax.swing.JScrollPane();
        updatedDesc = new javax.swing.JTextPane();
        updatedProgress = new javax.swing.JProgressBar();
        updatedProgressLabel = new javax.swing.JLabel();
        installedPanel = new javax.swing.JPanel();
        searchInstText = new javax.swing.JTextField();
        searchInstLbl = new javax.swing.JLabel();
        reloadInstalledButton = new javax.swing.JButton();
        uninstallButton = new javax.swing.JButton();
        installedSP = new javax.swing.JScrollPane();
        installedList = new javax.swing.JList();
        jScrollPane5 = new javax.swing.JScrollPane();
        installedDesc = new javax.swing.JTextPane();
        installedProgress = new javax.swing.JProgressBar();
        installedProgressLabel = new javax.swing.JLabel();
        newPanel = new javax.swing.JPanel();
        searchNewText = new javax.swing.JTextField();
        searchNewLbl = new javax.swing.JLabel();
        reloadNewButton = new javax.swing.JButton();
        installButton = new javax.swing.JButton();
        newSP = new javax.swing.JScrollPane();
        newList = new javax.swing.JList();
        jScrollPane4 = new javax.swing.JScrollPane();
        newDesc = new javax.swing.JTextPane();
        newProgress = new javax.swing.JProgressBar();
        newProgressLabel = new javax.swing.JLabel();
        installLocalButton = new javax.swing.JButton();
        settingsPanel = new javax.swing.JPanel();
        proxyButton = new javax.swing.JButton();
        allVersionsCheckbox = new javax.swing.JCheckBox();
        descriptionCheckbox = new javax.swing.JCheckBox();
        rubyPlatformLabel = new javax.swing.JLabel();
        platforms = org.netbeans.modules.ruby.platform.PlatformComponentFactory.getRubyPlatformsComboxBox();
        manageButton = new javax.swing.JButton();
        gemHome = new javax.swing.JLabel();
        gemHomeValue = new javax.swing.JTextField();
        browseGemHome = new javax.swing.JButton();

        FormListener formListener = new FormListener();

        searchUpdatedText.setColumns(14);
        searchUpdatedText.addActionListener(formListener);

        searchUpdatedLbl.setLabelFor(searchUpdatedText);
        org.openide.awt.Mnemonics.setLocalizedText(searchUpdatedLbl, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.searchUpdatedLbl.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(reloadReposButton, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.reloadReposButton.text")); // NOI18N
        reloadReposButton.addActionListener(formListener);

        updatedSP.setViewportView(updatedList);
        updatedList.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.updatedList.AccessibleContext.accessibleName")); // NOI18N
        updatedList.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.updatedList.AccessibleContext.accessibleDescription")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(updateButton, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.updateButton.text")); // NOI18N
        updateButton.setEnabled(false);
        updateButton.addActionListener(formListener);

        org.openide.awt.Mnemonics.setLocalizedText(updateAllButton, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.updateAllButton.text")); // NOI18N
        updateAllButton.addActionListener(formListener);

        updatedDesc.setEditable(false);
        jScrollPane6.setViewportView(updatedDesc);
        updatedDesc.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.updatedDesc.AccessibleContext.accessibleName")); // NOI18N
        updatedDesc.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.updatedDesc.AccessibleContext.accessibleDescription")); // NOI18N

        updatedProgress.setIndeterminate(true);

        org.openide.awt.Mnemonics.setLocalizedText(updatedProgressLabel, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.updatedProgressLabel.text")); // NOI18N

        org.jdesktop.layout.GroupLayout updatedPanelLayout = new org.jdesktop.layout.GroupLayout(updatedPanel);
        updatedPanel.setLayout(updatedPanelLayout);
        updatedPanelLayout.setHorizontalGroup(
            updatedPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(updatedPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(updatedPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, updatedPanelLayout.createSequentialGroup()
                        .add(reloadReposButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 436, Short.MAX_VALUE)
                        .add(searchUpdatedLbl)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(searchUpdatedText, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 156, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(updatedPanelLayout.createSequentialGroup()
                        .add(updateButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(updateAllButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 362, Short.MAX_VALUE)
                        .add(updatedProgressLabel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(updatedProgress, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, updatedPanelLayout.createSequentialGroup()
                        .add(updatedSP, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 461, Short.MAX_VALUE)
                        .add(18, 18, 18)
                        .add(jScrollPane6, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 283, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        updatedPanelLayout.setVerticalGroup(
            updatedPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(updatedPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(updatedPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(searchUpdatedLbl)
                    .add(searchUpdatedText, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(reloadReposButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(updatedPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(jScrollPane6, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 256, Short.MAX_VALUE)
                    .add(updatedSP, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 256, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(updatedPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(updatedPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(updateButton)
                        .add(updateAllButton))
                    .add(updatedProgress, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(updatedProgressLabel))
                .addContainerGap())
        );

        searchUpdatedText.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.searchUpdatedText.AccessibleContext.accessibleDescription")); // NOI18N
        searchUpdatedLbl.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.searchUpdatedLbl.AccessibleContext.accessibleDescription")); // NOI18N
        reloadReposButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.reloadReposButton.AccessibleContext.accessibleDescription")); // NOI18N
        updatedSP.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.jScrollPane3.AccessibleContext.accessibleDescription")); // NOI18N
        updateButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.updateButton.AccessibleContext.accessibleDescription")); // NOI18N
        updateAllButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.updateAllButton.AccessibleContext.accessibleDescription")); // NOI18N
        jScrollPane6.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.jScrollPane6.AccessibleContext.accessibleDescription")); // NOI18N
        updatedProgress.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.updatedProgress.AccessibleContext.accessibleDescription")); // NOI18N
        updatedProgressLabel.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.updatedProgressLabel.AccessibleContext.accessibleDescription")); // NOI18N

        gemsTab.addTab(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.updatedPanel.TabConstraints.tabTitle"), updatedPanel); // NOI18N

        searchInstText.setColumns(14);
        searchInstText.addActionListener(formListener);

        searchInstLbl.setLabelFor(searchInstText);
        org.openide.awt.Mnemonics.setLocalizedText(searchInstLbl, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.searchInstLbl.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(reloadInstalledButton, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.reloadInstalledButton.text")); // NOI18N
        reloadInstalledButton.addActionListener(formListener);

        org.openide.awt.Mnemonics.setLocalizedText(uninstallButton, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.uninstallButton.text")); // NOI18N
        uninstallButton.setEnabled(false);
        uninstallButton.addActionListener(formListener);

        installedSP.setViewportView(installedList);
        installedList.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.installedList.AccessibleContext.accessibleName")); // NOI18N
        installedList.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.installedList.AccessibleContext.accessibleDescription")); // NOI18N

        installedDesc.setEditable(false);
        jScrollPane5.setViewportView(installedDesc);
        installedDesc.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.installedDesc.AccessibleContext.accessibleName")); // NOI18N
        installedDesc.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.installedDesc.AccessibleContext.accessibleDescription")); // NOI18N

        installedProgress.setIndeterminate(true);

        org.openide.awt.Mnemonics.setLocalizedText(installedProgressLabel, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.installedProgressLabel.text")); // NOI18N

        org.jdesktop.layout.GroupLayout installedPanelLayout = new org.jdesktop.layout.GroupLayout(installedPanel);
        installedPanel.setLayout(installedPanelLayout);
        installedPanelLayout.setHorizontalGroup(
            installedPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(installedPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(installedPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, installedPanelLayout.createSequentialGroup()
                        .add(reloadInstalledButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 436, Short.MAX_VALUE)
                        .add(searchInstLbl)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(searchInstText, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 156, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(installedPanelLayout.createSequentialGroup()
                        .add(uninstallButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 455, Short.MAX_VALUE)
                        .add(installedProgressLabel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(installedProgress, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, installedPanelLayout.createSequentialGroup()
                        .add(installedSP, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 461, Short.MAX_VALUE)
                        .add(18, 18, 18)
                        .add(jScrollPane5, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 283, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        installedPanelLayout.setVerticalGroup(
            installedPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(installedPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(installedPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(searchInstLbl)
                    .add(reloadInstalledButton)
                    .add(searchInstText, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(installedPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(installedSP, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 256, Short.MAX_VALUE)
                    .add(jScrollPane5, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 256, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(installedPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(uninstallButton)
                    .add(installedProgress, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(installedProgressLabel))
                .addContainerGap())
        );

        searchInstText.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.instSearchText.AccessibleContext.accessibleDescription")); // NOI18N
        searchInstLbl.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.instSearchLbl.AccessibleContext.accessibleDescription")); // NOI18N
        reloadInstalledButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.reloadInstalledButton.AccessibleContext.accessibleDescription")); // NOI18N
        uninstallButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.uninstallButton.AccessibleContext.accessibleDescription")); // NOI18N
        installedSP.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.jScrollPane1.AccessibleContext.accessibleDescription")); // NOI18N
        jScrollPane5.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.jScrollPane5.AccessibleContext.accessibleDescription")); // NOI18N
        installedProgress.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.installedProgress.AccessibleContext.accessibleDescription")); // NOI18N
        installedProgressLabel.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.installedProgressLabel.AccessibleContext.accessibleDescription")); // NOI18N

        gemsTab.addTab(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.installedPanel.TabConstraints.tabTitle"), installedPanel); // NOI18N

        searchNewText.setColumns(14);
        searchNewText.addActionListener(formListener);

        searchNewLbl.setLabelFor(searchNewText);
        org.openide.awt.Mnemonics.setLocalizedText(searchNewLbl, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.searchNewLbl.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(reloadNewButton, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.reloadNewButton.text")); // NOI18N
        reloadNewButton.addActionListener(formListener);

        org.openide.awt.Mnemonics.setLocalizedText(installButton, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.installButton.text")); // NOI18N
        installButton.setEnabled(false);
        installButton.addActionListener(formListener);

        newSP.setViewportView(newList);
        newList.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.newList.AccessibleContext.accessibleName")); // NOI18N
        newList.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.newList.AccessibleContext.accessibleDescription")); // NOI18N

        newDesc.setEditable(false);
        jScrollPane4.setViewportView(newDesc);
        newDesc.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.newDesc.AccessibleContext.accessibleName")); // NOI18N
        newDesc.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.newDesc.AccessibleContext.accessibleDescription")); // NOI18N

        newProgress.setIndeterminate(true);

        org.openide.awt.Mnemonics.setLocalizedText(newProgressLabel, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.newProgressLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(installLocalButton, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.installLocalButton.text")); // NOI18N
        installLocalButton.addActionListener(formListener);

        org.jdesktop.layout.GroupLayout newPanelLayout = new org.jdesktop.layout.GroupLayout(newPanel);
        newPanel.setLayout(newPanelLayout);
        newPanelLayout.setHorizontalGroup(
            newPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(newPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(newPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, newPanelLayout.createSequentialGroup()
                        .add(reloadNewButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 436, Short.MAX_VALUE)
                        .add(searchNewLbl)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(searchNewText, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 156, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(newPanelLayout.createSequentialGroup()
                        .add(installButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(installLocalButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 348, Short.MAX_VALUE)
                        .add(newProgressLabel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(newProgress, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, newPanelLayout.createSequentialGroup()
                        .add(newSP, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 461, Short.MAX_VALUE)
                        .add(18, 18, 18)
                        .add(jScrollPane4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 283, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        newPanelLayout.setVerticalGroup(
            newPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(newPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(newPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(searchNewLbl)
                    .add(reloadNewButton)
                    .add(searchNewText, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(newPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(newSP, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 256, Short.MAX_VALUE)
                    .add(jScrollPane4, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 256, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(newPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(newPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(installButton)
                        .add(installLocalButton))
                    .add(newProgress, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(newProgressLabel))
                .addContainerGap())
        );

        searchNewText.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.searchNewText.AccessibleContext.accessibleDescription")); // NOI18N
        searchNewLbl.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.searchNewLbl.AccessibleContext.accessibleDescription")); // NOI18N
        reloadNewButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.reloadNewButton.AccessibleContext.accessibleDescription")); // NOI18N
        installButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.installButton.AccessibleContext.accessibleDescription")); // NOI18N
        newSP.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.jScrollPane2.AccessibleContext.accessibleDescription")); // NOI18N
        jScrollPane4.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.jScrollPane4.AccessibleContext.accessibleDescription")); // NOI18N
        newProgress.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.newProgress.AccessibleContext.accessibleDescription")); // NOI18N
        newProgressLabel.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.newProgressLabel.AccessibleContext.accessibleDescription")); // NOI18N

        gemsTab.addTab(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.newPanel.TabConstraints.tabTitle"), newPanel); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(proxyButton, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.proxyButton.text")); // NOI18N
        proxyButton.addActionListener(formListener);

        org.openide.awt.Mnemonics.setLocalizedText(allVersionsCheckbox, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.allVersionsCheckbox.text")); // NOI18N
        allVersionsCheckbox.addActionListener(formListener);

        org.openide.awt.Mnemonics.setLocalizedText(descriptionCheckbox, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.descriptionCheckbox.text")); // NOI18N
        descriptionCheckbox.addActionListener(formListener);

        org.jdesktop.layout.GroupLayout settingsPanelLayout = new org.jdesktop.layout.GroupLayout(settingsPanel);
        settingsPanel.setLayout(settingsPanelLayout);
        settingsPanelLayout.setHorizontalGroup(
            settingsPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(settingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(settingsPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(descriptionCheckbox)
                    .add(proxyButton)
                    .add(allVersionsCheckbox))
                .addContainerGap(463, Short.MAX_VALUE))
        );
        settingsPanelLayout.setVerticalGroup(
            settingsPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(settingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(proxyButton)
                .add(18, 18, 18)
                .add(allVersionsCheckbox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(descriptionCheckbox)
                .addContainerGap(237, Short.MAX_VALUE))
        );

        proxyButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.proxyButton.AccessibleContext.accessibleDescription")); // NOI18N

        gemsTab.addTab(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.settingsPanel.TabConstraints.tabTitle"), settingsPanel); // NOI18N

        rubyPlatformLabel.setLabelFor(platforms);
        org.openide.awt.Mnemonics.setLocalizedText(rubyPlatformLabel, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.rubyPlatformLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(manageButton, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.manageButton.text")); // NOI18N
        manageButton.addActionListener(formListener);

        gemHome.setLabelFor(gemHomeValue);
        org.openide.awt.Mnemonics.setLocalizedText(gemHome, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.gemHome.text")); // NOI18N

        gemHomeValue.setEditable(false);

        org.openide.awt.Mnemonics.setLocalizedText(browseGemHome, org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.browseGemHome.text")); // NOI18N
        browseGemHome.addActionListener(formListener);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(gemsTab, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 791, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(gemHome, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(rubyPlatformLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, gemHomeValue, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 591, Short.MAX_VALUE)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, platforms, 0, 591, Short.MAX_VALUE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, manageButton)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, browseGemHome, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 80, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {browseGemHome, manageButton}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.linkSize(new java.awt.Component[] {gemHome, rubyPlatformLabel}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(rubyPlatformLabel)
                    .add(manageButton)
                    .add(platforms, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(gemHome)
                    .add(browseGemHome)
                    .add(gemHomeValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(gemsTab, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 369, Short.MAX_VALUE)
                .addContainerGap())
        );

        gemsTab.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.gemsTab.AccessibleContext.accessibleName")); // NOI18N
        gemsTab.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.gemsTab.AccessibleContext.accessibleDescription")); // NOI18N
        platforms.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.platforms.AccessibleContext.accessibleName")); // NOI18N
        platforms.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.platforms.AccessibleContext.accessibleDescription")); // NOI18N
        manageButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.manageButton.AccessibleContext.accessibleDescription")); // NOI18N
        gemHomeValue.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.gemHomeValue.AccessibleContext.accessibleName")); // NOI18N
        gemHomeValue.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.gemHomeValue.AccessibleContext.accessibleDescription")); // NOI18N
        browseGemHome.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.browseGemHome.AccessibleContext.accessibleDescription")); // NOI18N

        getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.AccessibleContext.accessibleName")); // NOI18N
        getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(GemPanel.class, "GemPanel.AccessibleContext.accessibleDescription")); // NOI18N
    }

    // Code for dispatching events from components to event handlers.

    private class FormListener implements java.awt.event.ActionListener {
        FormListener() {}
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            if (evt.getSource() == searchUpdatedText) {
                GemPanel.this.searchUpdatedTextActionPerformed(evt);
            }
            else if (evt.getSource() == reloadReposButton) {
                GemPanel.this.reloadReposButtonActionPerformed(evt);
            }
            else if (evt.getSource() == updateButton) {
                GemPanel.this.updateButtonActionPerformed(evt);
            }
            else if (evt.getSource() == updateAllButton) {
                GemPanel.this.updateAllButtonActionPerformed(evt);
            }
            else if (evt.getSource() == searchInstText) {
                GemPanel.this.searchInstTextActionPerformed(evt);
            }
            else if (evt.getSource() == reloadInstalledButton) {
                GemPanel.this.reloadInstalledButtonActionPerformed(evt);
            }
            else if (evt.getSource() == uninstallButton) {
                GemPanel.this.uninstallButtonActionPerformed(evt);
            }
            else if (evt.getSource() == searchNewText) {
                GemPanel.this.searchNewTextActionPerformed(evt);
            }
            else if (evt.getSource() == reloadNewButton) {
                GemPanel.this.reloadNewButtonActionPerformed(evt);
            }
            else if (evt.getSource() == installButton) {
                GemPanel.this.installButtonActionPerformed(evt);
            }
            else if (evt.getSource() == installLocalButton) {
                GemPanel.this.installLocalButtonActionPerformed(evt);
            }
            else if (evt.getSource() == proxyButton) {
                GemPanel.this.proxyButtonActionPerformed(evt);
            }
            else if (evt.getSource() == allVersionsCheckbox) {
                GemPanel.this.allVersionsCheckboxActionPerformed(evt);
            }
            else if (evt.getSource() == descriptionCheckbox) {
                GemPanel.this.descriptionCheckboxActionPerformed(evt);
            }
            else if (evt.getSource() == manageButton) {
                GemPanel.this.manageButtonActionPerformed(evt);
            }
            else if (evt.getSource() == browseGemHome) {
                GemPanel.this.browseGemHomeActionPerformed(evt);
            }
        }
    }// </editor-fold>//GEN-END:initComponents

    private void reloadNewButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadNewButtonActionPerformed
        gemManager.resetRemote();//GEN-LAST:event_reloadNewButtonActionPerformed
        refreshNew();
    }                                               

    private void proxyButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_proxyButtonActionPerformed
        OptionsDisplayer.getDefault().open("General"); // NOI18N//GEN-LAST:event_proxyButtonActionPerformed
    }                                           

    private void searchNewTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchNewTextActionPerformed
        updateList(TabIndex.NEW, true);//GEN-LAST:event_searchNewTextActionPerformed
    }                                             

    private void searchUpdatedTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchUpdatedTextActionPerformed
        updateList(TabIndex.UPDATED, true);//GEN-LAST:event_searchUpdatedTextActionPerformed
    }                                                 

    private void reloadReposButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadReposButtonActionPerformed
        gemManager.reset();//GEN-LAST:event_reloadReposButtonActionPerformed
        setEnabledGUI(false);
        refreshUpdated();
    }                                                 

    private void installButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_installButtonActionPerformed
        assert SwingUtilities.isEventDispatchThread();

        int[] indices = newList.getSelectedIndices();
        List<Gem> gems = new ArrayList<Gem>();
        for (int index : indices) {
            Object o = newList.getModel().getElementAt(index);
            if (o instanceof Gem) { // Could be error or please wait string
                Gem gem = (Gem)o;
                gems.add(gem);
            }
        }

        if (!gems.isEmpty()) {
            for (Gem chosen : gems) {
                // Get some information about the chosen gem
                InstallationSettingsPanel panel = new InstallationSettingsPanel(chosen);
                panel.getAccessibleContext().setAccessibleDescription(
                        getMessage("InstallationSettingsPanel.AccessibleContext.accessibleDescription"));

                DialogDescriptor dd = new DialogDescriptor(panel, getMessage("ChooseGemSettings"));
                dd.setOptionType(NotifyDescriptor.OK_CANCEL_OPTION);
                dd.setModal(true);
                dd.setHelpCtx(new HelpCtx(GemPanel.class));
                Object result = DialogDisplayer.getDefault().notify(dd);
                if (result.equals(NotifyDescriptor.OK_OPTION)) {
                    Gem gem = new Gem(panel.getGemName(), null, null);
                    // XXX Do I really need to refresh it right way?
                    GemListRefresher completionTask = new GemListRefresher(newList, TabIndex.INSTALLED);
                    gemManager.install(new Gem[] { gem }, this, false, false, panel.getVersion(),//GEN-LAST:event_installButtonActionPerformed
                            panel.getIncludeDepencies(), true, completionTask);                                             
                }
            }
        }

    }                                             

    private void searchInstTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchInstTextActionPerformed
        updateList(TabIndex.INSTALLED, true);//GEN-LAST:event_searchInstTextActionPerformed
}                                              

    private void updateAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateAllButtonActionPerformed
        Runnable completionTask = new GemListRefresher(installedList, TabIndex.INSTALLED);//GEN-LAST:event_updateAllButtonActionPerformed
        gemManager.update(null, this, false, false, false, true, completionTask);
    }                                               

    private void updateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateButtonActionPerformed
        assert SwingUtilities.isEventDispatchThread();

        int[] indices = updatedList.getSelectedIndices();
        List<Gem> gems = new ArrayList<Gem>();
        if (indices != null) {
            for (int index : indices) {
                assert index >= 0;
                Object o = updatedList.getModel().getElementAt(index);
                if (o instanceof Gem) { // Could be error or please wait string
                    Gem gem = (Gem)o;
                    gems.add(gem);
                }
            }
        }
        if (!gems.isEmpty()) {//GEN-LAST:event_updateButtonActionPerformed
            Runnable completionTask = new GemListRefresher(updatedList, TabIndex.INSTALLED);
            gemManager.update(gems.toArray(new Gem[gems.size()]), this, false, false, false, true, completionTask);                                            
        }
    }                                            

    private void uninstallButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_uninstallButtonActionPerformed
        assert SwingUtilities.isEventDispatchThread();

        int[] indices = installedList.getSelectedIndices();
        List<Gem> gems = new ArrayList<Gem>();
        if (indices != null) {
            for (int index : indices) {
                assert index >= 0;
                Object o = installedList.getModel().getElementAt(index);
                if (o instanceof Gem) { // Could be error or please wait string
                    Gem gem = (Gem)o;
                    gems.add(gem);
                }
            }
        }
        if (!gems.isEmpty()) {
            Runnable completionTask = new GemListRefresher(installedList, TabIndex.INSTALLED);//GEN-LAST:event_uninstallButtonActionPerformed
            gemManager.uninstall(gems.toArray(new Gem[gems.size()]), this, true, completionTask);                                               
        }
    }                                               

    private void reloadInstalledButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadInstalledButtonActionPerformed
        gemManager.resetLocal();//GEN-LAST:event_reloadInstalledButtonActionPerformed
        refreshInstalled();
    }                                                     

    private void manageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manageButtonActionPerformed
        RubyPlatformCustomizer.manage(platforms);//GEN-LAST:event_manageButtonActionPerformed
    }                                            

    private void browseGemHomeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseGemHomeActionPerformed
        boolean changed = chooseAndSetGemHome(this, getSelectedPlatform());//GEN-LAST:event_browseGemHomeActionPerformed
        if (changed) {
            updateAsynchronously();
        }
    }                                             

    private void installLocalButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_installLocalButtonActionPerformed
        JFileChooser chooser = new JFileChooser(RubyPreferences.getPreferences().get(LAST_GEM_DIRECTORY, ""));
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || (f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".gem")); // NOI18N
            }
            public String getDescription() {
                return getMessage("GemPanel.rubygems.files.filter");
            }
        });
        int ret = chooser.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File gem = FileUtil.normalizeFile(chooser.getSelectedFile());
            RubyPreferences.getPreferences().put(LAST_GEM_DIRECTORY, gem.getParentFile().getAbsolutePath());//GEN-LAST:event_installLocalButtonActionPerformed
            GemListRefresher completionTask = new GemListRefresher(newList, TabIndex.INSTALLED);                                                  
            gemManager.installLocal(gem, this, false, false, true, completionTask);
        }
    }                                                  

    private void allVersionsCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allVersionsCheckboxActionPerformed
        RubyPreferences.setFetchAllVersions(allVersionsCheckbox.isSelected());//GEN-LAST:event_allVersionsCheckboxActionPerformed
    }                                                   

    private void descriptionCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_descriptionCheckboxActionPerformed
        RubyPreferences.setFetchGemDescriptions(descriptionCheckbox.isSelected());//GEN-LAST:event_descriptionCheckboxActionPerformed
    }                                                   

    public static File chooseGemRepository(final Component parent) {
        JFileChooser chooser = new JFileChooser();
        //        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int ret = chooser.showOpenDialog(parent);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File gemHomeF = FileUtil.normalizeFile(chooser.getSelectedFile());
            if (GemManager.isValidGemHome(gemHomeF)) {
                return gemHomeF;
            }
            if (!gemHomeF.exists() || (gemHomeF.isDirectory() && gemHomeF.list().length == 0)) {
                if (Util.confirmLocalized(GemPanel.class, "GemPanel.empty.create.gemrepo", gemHomeF.getAbsolutePath())) { // NOI18N
                    try {
                        GemManager.initializeRepository(gemHomeF);
                        return gemHomeF;
                    } catch (IOException ioe) {
                        LOGGER.log(Level.SEVERE, ioe.getLocalizedMessage(), ioe);
                    }
                }
            } else {
                Util.notifyLocalized(GemPanel.class, "GemPanel.invalid.gemHome", gemHomeF.getAbsolutePath()); // NOI18N
            }
        }
        return null;
    }
    
    public static boolean chooseAndSetGemHome(final Component parent, final RubyPlatform platform) {
        if (platform == null) {
            return false;
        }
        assert platform.hasRubyGemsInstalled() : "has RubyGems installed";
        File gemHomeF = chooseGemRepository(parent);
        if (gemHomeF != null) {
            platform.setGemHome(gemHomeF);
            return true;
        }
        return false;
    }
    
    /**
     * Refresh the list of displayed gems. If refresh is true, refresh the list
     * from the gem manager, otherwise just refilter list.
    */
    private void refreshGemList(final TabIndex tab) {
        Runnable updateTask = new Runnable() {
            public void run() { // runs one at time
                final List<String> errors = new ArrayList<String>(500);
                switch (tab) {
                    case INSTALLED:
                        installedGems = gemManager.getInstalledGems(errors);
                        fetchingLocal = false;
                        break;
                    case NEW:
                        availableGems = newGems = gemManager.getRemoteGems(errors);
                        fetchingRemote = false;
                        break;
                    default:
                        throw new AssertionError("Unknown tab: " + tab);
                }
                // Update UI
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        if (closed) {
                            return;
                        }
                        if (!errors.isEmpty()) {
                            showGemErrors(errors);
                        }
                        boolean done = notifyGemsUpdated();
                        if (!done) {
                            // Just filter
                            updateList(tab, false);
                        } else if (tab == TabIndex.INSTALLED) {
                            updateList(tab, true);
                        }
                    }
                });
            }
        };
        LOGGER.finest("Submitting update of " + tab);
        updateTasksQueue.submit(updateTask);
    }

    private void showGemErrors(final List<String> errors) {
        assert EventQueue.isDispatchThread();
        gemManager.reset();
        StringBuilder sb = new StringBuilder();
        for (String error : errors) {
            sb.append(error);
        }
        Util.notifyLocalized(GemPanel.class, "GemPanel.NoNetwork", NotifyDescriptor.ERROR_MESSAGE, sb.toString()); // NOI18N
    }

    private void refreshGemLists() {
        Runnable updateTask = new Runnable() {
            public void run() {
                assert !SwingUtilities.isEventDispatchThread();

                final List<String> errors = new ArrayList<String>();
                gemManager.reloadIfNeeded(errors);
                installedGems = gemManager.getInstalledGems(errors);
                availableGems = gemManager.getRemoteGems(errors);
                newGems = availableGems;
                fetchingLocal = false;
                fetchingRemote = false;

                // Update UI
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        if (closed) {
                            return;
                        }
                        if (!errors.isEmpty()) {
                            showGemErrors(errors);
                        }
                        notifyGemsUpdated();
                        updateList(TabIndex.INSTALLED, true);
                    }
                });
            }
        };
        LOGGER.finest("Submitting refreshing of gems");
        updateTasksQueue.submit(updateTask);
    }

    private String getGemFilter(TabIndex tab) {
        assert SwingUtilities.isEventDispatchThread();

        String filter = null;
        JTextField tf;
        if (tab == TabIndex.INSTALLED) {
            tf = searchInstText;
        } else if (tab == TabIndex.UPDATED) {
            tf = searchUpdatedText;
        } else {
            assert tab == TabIndex.NEW;
            tf = searchNewText;
        }
        filter = tf.getText().trim();
        if (filter.length() == 0) {
            filter = null;
        }

        return filter;
    }

    private RubyPlatform getSelectedPlatform() {
        return PlatformComponentFactory.getPlatform(platforms);
    }

    private static class MyListSelectionListener implements ListSelectionListener {
        
        private final JButton button;
        private final JTextPane pane;
        private final JList list;

        private MyListSelectionListener(JList list, JTextPane pane, JButton button) {
            this.list = list;
            this.pane = pane;
            this.button = button;
        }
        
        public void valueChanged(ListSelectionEvent ev) {
            if (ev.getValueIsAdjusting()) {
                return;
            }
            int index = list.getSelectedIndex();
            if (index != -1) {
                Object o = list.getModel().getElementAt(index);
                if (o instanceof Gem) { // Could be "Please Wait..." String
                    button.setEnabled(true);
                    if (pane != null) {
                        updateGemDescription(pane, (Gem)o);
                    }
                    return;
                }
            } else if (pane != null) {
                pane.setText("");
            }
            button.setEnabled(index != -1);
        }
    }

    private class GemListRefresher implements Runnable {
        private final JList list;
        private final TabIndex tab;

        public GemListRefresher(JList list, TabIndex tab) {
            this.list = list;
            this.tab = tab;
        }

        public void run() {
            if (!gemsModified) {
                gemsModified = true;
            }
            refreshGemList(tab);
        }
    }

    private static String getMessage(String key) {
        return NbBundle.getMessage(GemPanel.class, key);
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox allVersionsCheckbox;
    private javax.swing.JButton browseGemHome;
    private javax.swing.JCheckBox descriptionCheckbox;
    private javax.swing.JLabel gemHome;
    private javax.swing.JTextField gemHomeValue;
    private javax.swing.JTabbedPane gemsTab;
    private javax.swing.JButton installButton;
    private javax.swing.JButton installLocalButton;
    private javax.swing.JTextPane installedDesc;
    private javax.swing.JList installedList;
    private javax.swing.JPanel installedPanel;
    private javax.swing.JProgressBar installedProgress;
    private javax.swing.JLabel installedProgressLabel;
    private javax.swing.JScrollPane installedSP;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JButton manageButton;
    private javax.swing.JTextPane newDesc;
    private javax.swing.JList newList;
    private javax.swing.JPanel newPanel;
    private javax.swing.JProgressBar newProgress;
    private javax.swing.JLabel newProgressLabel;
    private javax.swing.JScrollPane newSP;
    private javax.swing.JComboBox platforms;
    private javax.swing.JButton proxyButton;
    private javax.swing.JButton reloadInstalledButton;
    private javax.swing.JButton reloadNewButton;
    private javax.swing.JButton reloadReposButton;
    private javax.swing.JLabel rubyPlatformLabel;
    private javax.swing.JLabel searchInstLbl;
    private javax.swing.JTextField searchInstText;
    private javax.swing.JLabel searchNewLbl;
    private javax.swing.JTextField searchNewText;
    private javax.swing.JLabel searchUpdatedLbl;
    private javax.swing.JTextField searchUpdatedText;
    private javax.swing.JPanel settingsPanel;
    private javax.swing.JButton uninstallButton;
    private javax.swing.JButton updateAllButton;
    private javax.swing.JButton updateButton;
    private javax.swing.JTextPane updatedDesc;
    private javax.swing.JList updatedList;
    private javax.swing.JPanel updatedPanel;
    private javax.swing.JProgressBar updatedProgress;
    private javax.swing.JLabel updatedProgressLabel;
    private javax.swing.JScrollPane updatedSP;
    // End of variables declaration//GEN-END:variables
    
}
