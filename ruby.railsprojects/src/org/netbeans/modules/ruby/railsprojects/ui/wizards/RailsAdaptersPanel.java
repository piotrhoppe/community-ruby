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
 * Portions Copyrighted 2007 Sun Microsystems, Inc.
 */
package org.netbeans.modules.ruby.railsprojects.ui.wizards;

import java.awt.Component;
import java.util.List;
import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.netbeans.modules.ruby.railsprojects.database.RailsAdapterFactory;
import org.netbeans.modules.ruby.railsprojects.database.RailsDatabaseConfiguration;
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;

/**
 * A panel for Rails database adapters.
 * 
 * TODO: currently only one (development) combo is displayed, test and production
 * adapters need to be inserted to database.yml after initial generation 
 * (AFAIK the rails generator doesn't let you specify those databases separately).
 * 
 * @author  Erno Mononen
 */
public class RailsAdaptersPanel extends SettingsPanel {

    /** Creates new form RailsAdaptersPanel */
    public RailsAdaptersPanel() {
        initComponents();
        List<RailsDatabaseConfiguration> adapters = RailsAdapterFactory.getAdapters();
        developmentComboBox.setModel(new AdapterListModel(adapters));
        productionComboBox.setModel(new AdapterListModel(adapters));
        testComboBox.setModel(new AdapterListModel(adapters));
        developmentComboBox.setRenderer(new AdapterListCellRendered());
        productionComboBox.setRenderer(new AdapterListCellRendered());
        testComboBox.setRenderer(new AdapterListCellRendered());

        //TODO: enable once the logic for editing database.yml
        // to change production and test databases is implemented
        productionComboBox.setVisible(false);
        testComboBox.setVisible(false);
        productionLabel.setVisible(false);
        testLabel.setVisible(false);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        developmentLabel = new javax.swing.JLabel();
        developmentComboBox = new javax.swing.JComboBox();
        productionComboBox = new javax.swing.JComboBox();
        productionLabel = new javax.swing.JLabel();
        testComboBox = new javax.swing.JComboBox();
        testLabel = new javax.swing.JLabel();

        developmentLabel.setText(org.openide.util.NbBundle.getMessage(RailsAdaptersPanel.class, "LBL_DatabaseAdapter")); // NOI18N

        productionLabel.setText(org.openide.util.NbBundle.getMessage(RailsAdaptersPanel.class, "LBL_ProductionConnection")); // NOI18N

        testLabel.setText(org.openide.util.NbBundle.getMessage(RailsAdaptersPanel.class, "LBL_TestConnection")); // NOI18N

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(productionLabel)
                    .add(developmentLabel)
                    .add(testLabel))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(testComboBox, 0, 399, Short.MAX_VALUE)
                    .add(developmentComboBox, 0, 399, Short.MAX_VALUE)
                    .add(productionComboBox, 0, 399, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(developmentLabel)
                    .add(developmentComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(18, 18, 18)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(productionLabel)
                    .add(productionComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(18, 18, 18)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(testLabel)
                    .add(testComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(27, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox developmentComboBox;
    private javax.swing.JLabel developmentLabel;
    private javax.swing.JComboBox productionComboBox;
    private javax.swing.JLabel productionLabel;
    private javax.swing.JComboBox testComboBox;
    private javax.swing.JLabel testLabel;
    // End of variables declaration//GEN-END:variables
    @Override
    void store( WizardDescriptor settings) {
        settings.putProperty(NewRailsProjectWizardIterator.RAILS_DEVELOPMENT_DB, developmentComboBox.getSelectedItem());
//        settings.putProperty(NewRailsProjectWizardIterator.RAILS_PRODUCTION_DB, StandardRailsAdapter.get(pr));
//        settings.putProperty(NewRailsProjectWizardIterator.RAILS_DEVELOPMENT_DB, StandardRailsAdapter.get(devel));
    }

    @Override
    void read( WizardDescriptor settings) {
    }

    @Override
    boolean valid( WizardDescriptor settings) {
        return true;
    }

    @Override
    void validate( WizardDescriptor settings) throws WizardValidationException {
    }


    private static class AdapterListModel extends AbstractListModel implements ComboBoxModel {

        private final List<? extends RailsDatabaseConfiguration> adapters;
        private Object selected;

        public AdapterListModel(List<? extends RailsDatabaseConfiguration> adapters) {
            this.adapters = adapters;
            this.selected = adapters.get(0);
        }

        public int getSize() {
            return adapters.size();
        }

        public Object getElementAt(int index) {
            return adapters.get(index);
        }

        public void setSelectedItem(Object adapter) {
            if (selected != adapter) {
                this.selected = adapter;
                fireContentsChanged(this, -1, -1);
            }
        }

        public Object getSelectedItem() {
            return selected;
        }
    }

    private static class AdapterListCellRendered extends JLabel implements ListCellRenderer {

        public AdapterListCellRendered() {
            setOpaque(true);
        }

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

            RailsDatabaseConfiguration dbConf = (RailsDatabaseConfiguration) value;

            setText(dbConf.railsGenerationParam());
            setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

            return this;
        }
    }
}
