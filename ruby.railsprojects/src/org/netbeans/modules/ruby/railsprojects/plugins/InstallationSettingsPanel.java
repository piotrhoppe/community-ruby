/*
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at http://www.netbeans.org/cddl.html
 * or http://www.netbeans.org/cddl.txt.
 *
 * When distributing Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://www.netbeans.org/cddl.txt.
 * If applicable, add the following below the CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 */

package org.netbeans.modules.ruby.railsprojects.plugins;

/**
 *
 * @author  Tor Norbye
 */
public class InstallationSettingsPanel extends javax.swing.JPanel {
    
    /** Creates new form InstallationSettingsPanel */
    public InstallationSettingsPanel(Plugin plugin) {
        initComponents();
        nameField.setText(plugin.getName());
    }
    
    public void setMessage(String message) {
        messageLabel.setText(message);
    }
    
    public boolean isSvnExternals() {
        return subversionToggle.isSelected() && externalsRadio.isSelected();
    }

    public boolean isSvnCheckout() {
        return subversionToggle.isSelected() && checkoutRadio.isSelected();
    }
    
    
    public String getRevision() {
        String s = revisionField.getText().trim();
        return (subversionToggle.isSelected() && revisionCheckBox.isSelected()) ? s : null;
    }
    
    public String getPluginName() {
        return nameField.getText().trim();
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {

        svnGroup = new javax.swing.ButtonGroup();
        revisionCheckBox = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();
        nameField = new javax.swing.JTextField();
        messageLabel = new javax.swing.JLabel();
        revisionField = new javax.swing.JTextField();
        subversionToggle = new javax.swing.JCheckBox();
        externalsRadio = new javax.swing.JRadioButton();
        checkoutRadio = new javax.swing.JRadioButton();

        revisionCheckBox.setText("Specific revision:");
        revisionCheckBox.setEnabled(false);
        revisionCheckBox.setMargin(new java.awt.Insets(0, 0, 0, 0));

        jLabel1.setText(org.openide.util.NbBundle.getMessage(InstallationSettingsPanel.class, "InstallationSettingsPanel.jLabel1.text")); // NOI18N

        nameField.setText(org.openide.util.NbBundle.getMessage(InstallationSettingsPanel.class, "InstallationSettingsPanel.nameField.text")); // NOI18N

        messageLabel.setText(org.openide.util.NbBundle.getMessage(InstallationSettingsPanel.class, "InstallationSettingsPanel.messageLabel.text")); // NOI18N

        revisionField.setEnabled(false);

        subversionToggle.setText("Use Subversion");
        subversionToggle.setMargin(new java.awt.Insets(0, 0, 0, 0));
        subversionToggle.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggledSubversion(evt);
            }
        });

        svnGroup.add(externalsRadio);
        externalsRadio.setText("Use svn:externals to grab the plugin");
        externalsRadio.setEnabled(false);
        externalsRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));

        svnGroup.add(checkoutRadio);
        checkoutRadio.setSelected(true);
        checkoutRadio.setText("Use svn checkout to grab the plugin");
        checkoutRadio.setEnabled(false);
        checkoutRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(jLabel1)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(nameField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 354, Short.MAX_VALUE))
                    .add(messageLabel)
                    .add(subversionToggle)
                    .add(layout.createSequentialGroup()
                        .add(22, 22, 22)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(checkoutRadio)
                            .add(externalsRadio)
                            .add(layout.createSequentialGroup()
                                .add(revisionCheckBox)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(revisionField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 164, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(nameField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(subversionToggle)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(checkoutRadio)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(externalsRadio)
                .add(18, 18, 18)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(messageLabel)
                    .add(revisionCheckBox)
                    .add(revisionField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

private void toggledSubversion(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggledSubversion
    // Enable/disable other fields
    boolean enabled = subversionToggle.isSelected();
    revisionCheckBox.setEnabled(enabled);
    revisionField.setEnabled(enabled);
    externalsRadio.setEnabled(enabled);
    checkoutRadio.setEnabled(enabled);
}//GEN-LAST:event_toggledSubversion
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton checkoutRadio;
    private javax.swing.JRadioButton externalsRadio;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel messageLabel;
    private javax.swing.JTextField nameField;
    private javax.swing.JCheckBox revisionCheckBox;
    private javax.swing.JTextField revisionField;
    private javax.swing.JCheckBox subversionToggle;
    private javax.swing.ButtonGroup svnGroup;
    // End of variables declaration//GEN-END:variables
    
}
