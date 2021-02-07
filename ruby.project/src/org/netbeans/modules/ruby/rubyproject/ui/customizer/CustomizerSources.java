/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
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
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Deve1loper of the Original
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

package org.netbeans.modules.ruby.rubyproject.ui.customizer;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.charset.Charset;
import javax.swing.JPanel;
import javax.swing.UIManager;
import org.netbeans.modules.ruby.rubyproject.RubyProject;
import org.netbeans.spi.project.ui.support.ProjectCustomizer;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

/**
 *
 * @author  Tomas Zezula
 */
public class CustomizerSources extends JPanel implements HelpCtx.Provider {
    
    private String originalEncoding;
    private boolean notified;
    private final RubyProjectProperties uiProperties;
    
    public CustomizerSources( RubyProjectProperties uiProperties ) {
        this.uiProperties = uiProperties;
        initComponents();
        sourceSP.getViewport().setBackground( sourceRoots.getBackground() );
        testSP.getViewport().setBackground( testRoots.getBackground() );
        
        sourceRoots.setModel( uiProperties.sourceRootsModel  );
        testRoots.setModel( uiProperties.testRootsModel  );
        sourceRoots.getTableHeader().setReorderingAllowed(false);
        testRoots.getTableHeader().setReorderingAllowed(false);

        RubyProject project = uiProperties.getRubyProject();
        FileObject projectFolder = project.getProjectDirectory();
        File pf = FileUtil.toFile(projectFolder);
        this.projectLocation.setText(pf == null ? "" : pf.getPath());
        
        
        RubySourceRootsUi.EditMediator emSR = RubySourceRootsUi.registerEditMediator(
            project,
            project.getSourceRoots(),
            sourceRoots,
            addSourceRoot,
            removeSourceRoot, 
            upSourceRoot, 
            downSourceRoot);
        
        RubySourceRootsUi.EditMediator emTSR = RubySourceRootsUi.registerEditMediator(
            project,
            project.getTestSourceRoots(),
            testRoots,
            addTestRoot,
            removeTestRoot, 
            upTestRoot, 
            downTestRoot);
        
        emSR.setRelatedEditMediator(emTSR);
        emTSR.setRelatedEditMediator(emSR);

        this.originalEncoding = project.evaluator().getProperty(RubyProjectProperties.SOURCE_ENCODING);
        if (this.originalEncoding == null) {
            this.originalEncoding = Charset.defaultCharset().name();
        }
        
        this.encoding.setModel(ProjectCustomizer.encodingModel(originalEncoding));
        this.encoding.setRenderer(ProjectCustomizer.encodingRenderer());
        
        final String lafid = UIManager.getLookAndFeel().getID();
        if (!"Aqua".equals(lafid)) { //NOI18N
            this.encoding.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);    //NOI18N
            this.encoding.addItemListener(new java.awt.event.ItemListener() {

                public void itemStateChanged(java.awt.event.ItemEvent e) {
                    javax.swing.JComboBox combo = (javax.swing.JComboBox) e.getSource();
                    combo.setPopupVisible(false);
                }
            });
        }


        this.encoding.addActionListener(new ActionListener () {
            public void actionPerformed(ActionEvent arg0) {
                handleEncodingChange();
            }            
        });
    }

    private void handleEncodingChange () {
        Charset enc = (Charset) encoding.getSelectedItem();
        String encName;
        if (enc != null) {
            encName = enc.name();
        } else {
            encName = originalEncoding;
        }
        if (!notified && encName != null && !encName.equals(originalEncoding)) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    NbBundle.getMessage(CustomizerSources.class, "MSG_EncodingWarning"), NotifyDescriptor.WARNING_MESSAGE));
            notified = true;
        }

        this.uiProperties.putAdditionalProperty(RubyProjectProperties.SOURCE_ENCODING, encName);
    }

    public HelpCtx getHelpCtx() {
        return new HelpCtx (CustomizerSources.class);
    }    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        prjFolderLabel = new javax.swing.JLabel();
        projectLocation = new javax.swing.JTextField();
        encodingPanel = new javax.swing.JPanel();
        encodingLabel = new javax.swing.JLabel();
        encoding = new javax.swing.JComboBox();
        sourceFolderLabel = new javax.swing.JLabel();
        sourceSP = new javax.swing.JScrollPane();
        sourceRoots = new javax.swing.JTable();
        addSourceRoot = new javax.swing.JButton();
        removeSourceRoot = new javax.swing.JButton();
        upSourceRoot = new javax.swing.JButton();
        downSourceRoot = new javax.swing.JButton();
        testFolderLabel = new javax.swing.JLabel();
        addTestRoot = new javax.swing.JButton();
        removeTestRoot = new javax.swing.JButton();
        upTestRoot = new javax.swing.JButton();
        downTestRoot = new javax.swing.JButton();
        testSP = new javax.swing.JScrollPane();
        testRoots = new javax.swing.JTable();

        prjFolderLabel.setLabelFor(projectLocation);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/netbeans/modules/ruby/rubyproject/ui/customizer/Bundle"); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(prjFolderLabel, bundle.getString("CTL_ProjectFolder")); // NOI18N

        projectLocation.setEditable(false);

        encodingLabel.setLabelFor(encoding);
        org.openide.awt.Mnemonics.setLocalizedText(encodingLabel, org.openide.util.NbBundle.getMessage(CustomizerSources.class, "TXT_Encoding")); // NOI18N

        javax.swing.GroupLayout encodingPanelLayout = new javax.swing.GroupLayout(encodingPanel);
        encodingPanel.setLayout(encodingPanelLayout);
        encodingPanelLayout.setHorizontalGroup(
            encodingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(encodingPanelLayout.createSequentialGroup()
                .addComponent(encodingLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(encoding, javax.swing.GroupLayout.PREFERRED_SIZE, 137, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(427, Short.MAX_VALUE))
        );
        encodingPanelLayout.setVerticalGroup(
            encodingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(encodingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(encodingLabel)
                .addComponent(encoding, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        encodingLabel.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(CustomizerSources.class, "CustomizerSources.encodingLabel.AccessibleContext.accessibleDescription")); // NOI18N

        sourceFolderLabel.setLabelFor(sourceRoots);
        org.openide.awt.Mnemonics.setLocalizedText(sourceFolderLabel, bundle.getString("CTL_SourceRoots")); // NOI18N

        sourceRoots.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null},
                {null, null},
                {null, null},
                {null, null}
            },
            new String [] {
                "Package Folder", "Label"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Object.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        sourceSP.setViewportView(sourceRoots);
        sourceRoots.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_CustomizerSources_sourceRoots")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(addSourceRoot, bundle.getString("CTL_AddSourceRoot")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(removeSourceRoot, bundle.getString("CTL_RemoveSourceRoot")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(upSourceRoot, bundle.getString("CTL_UpSourceRoot")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(downSourceRoot, bundle.getString("CTL_DownSourceRoot")); // NOI18N

        testFolderLabel.setLabelFor(testRoots);
        org.openide.awt.Mnemonics.setLocalizedText(testFolderLabel, bundle.getString("CTL_TestRoots")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(addTestRoot, bundle.getString("CTL_AddTestRoot")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(removeTestRoot, bundle.getString("CTL_RemoveTestRoot")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(upTestRoot, bundle.getString("CTL_UpTestRoot")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(downTestRoot, bundle.getString("CTL_DownTestRoot")); // NOI18N

        testRoots.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null},
                {null, null},
                {null, null},
                {null, null}
            },
            new String [] {
                "Package Folder", "Label"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Object.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        testSP.setViewportView(testRoots);
        testRoots.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_CustomizerSources_testRoots")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(encodingPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addComponent(prjFolderLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(projectLocation, javax.swing.GroupLayout.DEFAULT_SIZE, 536, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(testSP, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 516, Short.MAX_VALUE)
                    .addComponent(sourceSP, javax.swing.GroupLayout.DEFAULT_SIZE, 516, Short.MAX_VALUE)
                    .addComponent(sourceFolderLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 516, Short.MAX_VALUE)
                    .addComponent(testFolderLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 516, Short.MAX_VALUE))
                .addGap(9, 9, 9)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(addSourceRoot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(removeSourceRoot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(upSourceRoot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(downSourceRoot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(addTestRoot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(removeTestRoot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(upTestRoot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(downTestRoot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(prjFolderLabel)
                    .addComponent(projectLocation, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(2, 2, 2)
                .addComponent(sourceFolderLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(addSourceRoot)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(removeSourceRoot)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(upSourceRoot)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(downSourceRoot))
                    .addComponent(sourceSP, javax.swing.GroupLayout.PREFERRED_SIZE, 169, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(testFolderLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(addTestRoot)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(removeTestRoot)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(upTestRoot)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(downTestRoot))
                    .addComponent(testSP, javax.swing.GroupLayout.DEFAULT_SIZE, 182, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(encodingPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        projectLocation.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_CustomizerSources_projectLocation")); // NOI18N
        addSourceRoot.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_CustomizerSources_addSourceRoot")); // NOI18N
        removeSourceRoot.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_CustomizerSources_removeSourceRoot")); // NOI18N
        upSourceRoot.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_CustomizerSources_upSourceRoot")); // NOI18N
        downSourceRoot.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_CustomizerSources_downSourceRoot")); // NOI18N
        addTestRoot.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_CustomizerSources_addTestRoot")); // NOI18N
        removeTestRoot.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_CustomizerSources_removeTestRoot")); // NOI18N
        upTestRoot.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_CustomizerSources_upTestRoot")); // NOI18N
        downTestRoot.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_CustomizerSources_downTestRoot")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addSourceRoot;
    private javax.swing.JButton addTestRoot;
    private javax.swing.JButton downSourceRoot;
    private javax.swing.JButton downTestRoot;
    private javax.swing.JComboBox encoding;
    private javax.swing.JLabel encodingLabel;
    private javax.swing.JPanel encodingPanel;
    private javax.swing.JLabel prjFolderLabel;
    private javax.swing.JTextField projectLocation;
    private javax.swing.JButton removeSourceRoot;
    private javax.swing.JButton removeTestRoot;
    private javax.swing.JLabel sourceFolderLabel;
    private javax.swing.JTable sourceRoots;
    private javax.swing.JScrollPane sourceSP;
    private javax.swing.JLabel testFolderLabel;
    private javax.swing.JTable testRoots;
    private javax.swing.JScrollPane testSP;
    private javax.swing.JButton upSourceRoot;
    private javax.swing.JButton upTestRoot;
    // End of variables declaration//GEN-END:variables
    
}
