/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Oracle and/or its affiliates. All rights reserved.
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
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2009 Sun Microsystems, Inc.
 */

/*
 * RunFilePanel.java
 *
 * Created on Aug 18, 2009, 1:25:11 PM
 */

package org.netbeans.modules.ruby.rubyproject;

import org.netbeans.api.ruby.platform.RubyPlatform;
import org.netbeans.modules.ruby.platform.PlatformComponentFactory;
import org.netbeans.modules.ruby.platform.RubyPlatformCustomizer;

/**
 *
 * @author Erno Mononen
 */
final class RunFilePanel extends javax.swing.JPanel {

    /** Creates new form RunFilePanel */
    public RunFilePanel(RunFileActionProvider.RunFileArgs args, boolean allowPlatformChange) {
        initComponents();
        initJvmArgs();
        PlatformComponentFactory.addPlatformChangeListener(platformCombo, new PlatformComponentFactory.PlatformChangeListener() {
            public void platformChanged() {
                initJvmArgs();
            }
        });
        if (args != null) {
            runArgsField.setText(args.getRunArgs());
            jvmArgsField.setText(args.getJvmArgs());
            rubyOptionsField.setText(args.getRubyOpts());
            workDirField.setText(args.getWorkDir());
            if (args.getPlatform() != null) {
                platformCombo.setSelectedItem(args.getPlatform());
            }
        }
        platformCombo.setEnabled(allowPlatformChange);
        platformLabel.setEnabled(allowPlatformChange);
        managePlatformsButton.setEnabled(allowPlatformChange);
    }

    RunFileActionProvider.RunFileArgs getArgs() {
        return new RunFileActionProvider.RunFileArgs(getPlatform(), getRunArgs(),
                getJvmArgs(), getRubyOpts(), getWorkDir(), !displayDialog.isSelected());
    }
    
    private void initJvmArgs() {
        RubyPlatform selected = getPlatform();
        boolean enable = selected != null && selected.isJRuby();
        jvmArgsField.setEnabled(enable);
        jvmArgsLabel.setEnabled(enable);
    }

    private String getJvmArgs() {
        return jvmArgsField.getText();
    }

    private String getRunArgs() {
        return runArgsField.getText();
    }

    private String getRubyOpts() {
        return rubyOptionsField.getText();
    }

    private String getWorkDir() {
        return workDirField.getText();
    }

    private RubyPlatform getPlatform() {
        return PlatformComponentFactory.getPlatform(platformCombo);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        platformLabel = new javax.swing.JLabel();
        platformCombo = org.netbeans.modules.ruby.platform.PlatformComponentFactory.getRubyPlatformsComboxBox();
        managePlatformsButton = new javax.swing.JButton();
        runArgsLabel = new javax.swing.JLabel();
        runArgsField = new javax.swing.JTextField();
        jvmArgsLabel = new javax.swing.JLabel();
        jvmArgsField = new javax.swing.JTextField();
        displayDialog = new javax.swing.JCheckBox();
        rubyOptionsField = new javax.swing.JTextField();
        rubyOptionsLabel = new javax.swing.JLabel();
        workDirField = new javax.swing.JTextField();
        workDirLabel = new javax.swing.JLabel();

        platformLabel.setLabelFor(platformCombo);
        org.openide.awt.Mnemonics.setLocalizedText(platformLabel, org.openide.util.NbBundle.getMessage(RunFilePanel.class, "RunFilePanel.platformLabel.text")); // NOI18N

        platformCombo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                platformComboItemStateChanged(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(managePlatformsButton, org.openide.util.NbBundle.getMessage(RunFilePanel.class, "RunFilePanel.managePlatformsButton.text")); // NOI18N
        managePlatformsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                managePlatformsButtonActionPerformed(evt);
            }
        });

        runArgsLabel.setLabelFor(runArgsField);
        org.openide.awt.Mnemonics.setLocalizedText(runArgsLabel, org.openide.util.NbBundle.getMessage(RunFilePanel.class, "RunFilePanel.runArgsLabel.text")); // NOI18N

        runArgsField.setText(org.openide.util.NbBundle.getMessage(RunFilePanel.class, "RunFilePanel.runArgsField.text")); // NOI18N

        jvmArgsLabel.setLabelFor(jvmArgsField);
        org.openide.awt.Mnemonics.setLocalizedText(jvmArgsLabel, org.openide.util.NbBundle.getMessage(RunFilePanel.class, "RunFilePanel.jvmArgsLabel.text")); // NOI18N

        jvmArgsField.setText(org.openide.util.NbBundle.getMessage(RunFilePanel.class, "RunFilePanel.jvmArgsField.text")); // NOI18N
        jvmArgsField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jvmArgsFieldActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(displayDialog, org.openide.util.NbBundle.getMessage(RunFilePanel.class, "RunFilePanel.displayDialog.text")); // NOI18N
        displayDialog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                displayDialogActionPerformed(evt);
            }
        });

        rubyOptionsField.setText(org.openide.util.NbBundle.getMessage(RunFilePanel.class, "RunFilePanel.rubyOptionsField.text")); // NOI18N

        rubyOptionsLabel.setLabelFor(rubyOptionsField);
        org.openide.awt.Mnemonics.setLocalizedText(rubyOptionsLabel, org.openide.util.NbBundle.getMessage(RunFilePanel.class, "RunFilePanel.rubyOptionsLabel.text")); // NOI18N

        workDirField.setText(org.openide.util.NbBundle.getMessage(RunFilePanel.class, "RunFilePanel.workDirField.text")); // NOI18N

        workDirLabel.setLabelFor(workDirField);
        org.openide.awt.Mnemonics.setLocalizedText(workDirLabel, org.openide.util.NbBundle.getMessage(RunFilePanel.class, "RunFilePanel.workDirLabel.text")); // NOI18N

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(rubyOptionsLabel)
                            .add(workDirLabel)
                            .add(platformLabel)
                            .add(runArgsLabel)
                            .add(jvmArgsLabel))
                        .add(16, 16, 16)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jvmArgsField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(platformCombo, 0, 212, Short.MAX_VALUE)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(managePlatformsButton))
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, runArgsField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                            .add(workDirField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                            .add(rubyOptionsField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)))
                    .add(displayDialog))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(platformLabel)
                    .add(platformCombo, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(managePlatformsButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(runArgsLabel)
                    .add(runArgsField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(workDirLabel)
                    .add(workDirField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(rubyOptionsLabel)
                    .add(rubyOptionsField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(18, 18, 18)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jvmArgsLabel)
                    .add(jvmArgsField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(displayDialog)
                .add(18, 18, 18))
        );
        displayDialog.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(RunFilePanel.class, "RunFilePanel.displayDialog.AccessibleContext.accessibleDescription")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents

    private void jvmArgsFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jvmArgsFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jvmArgsFieldActionPerformed

    private void managePlatformsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_managePlatformsButtonActionPerformed
        RubyPlatformCustomizer.manage(platformCombo);
    }//GEN-LAST:event_managePlatformsButtonActionPerformed

    private void platformComboItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_platformComboItemStateChanged
        // TODO add your handling code here:
    }//GEN-LAST:event_platformComboItemStateChanged

    private void displayDialogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_displayDialogActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_displayDialogActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox displayDialog;
    private javax.swing.JTextField jvmArgsField;
    private javax.swing.JLabel jvmArgsLabel;
    private javax.swing.JButton managePlatformsButton;
    private javax.swing.JComboBox platformCombo;
    private javax.swing.JLabel platformLabel;
    private javax.swing.JTextField rubyOptionsField;
    private javax.swing.JLabel rubyOptionsLabel;
    private javax.swing.JTextField runArgsField;
    private javax.swing.JLabel runArgsLabel;
    private javax.swing.JTextField workDirField;
    private javax.swing.JLabel workDirLabel;
    // End of variables declaration//GEN-END:variables

}
