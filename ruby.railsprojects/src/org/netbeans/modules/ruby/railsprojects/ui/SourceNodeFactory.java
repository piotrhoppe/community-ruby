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

package org.netbeans.modules.ruby.railsprojects.ui;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.modules.ruby.railsprojects.Generator;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.modules.ruby.railsprojects.RailsProject;
import org.netbeans.modules.ruby.railsprojects.SourceRoots;
import org.netbeans.modules.ruby.railsprojects.ui.customizer.CustomizerProviderImpl;
import org.netbeans.spi.project.ui.support.NodeFactory;
import org.netbeans.spi.project.ui.support.NodeList;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author mkleint
 */
public final class SourceNodeFactory implements NodeFactory {
    public SourceNodeFactory() {
    }
    
    public NodeList createNodes(Project p) {
        RailsProject project = p.getLookup().lookup(RailsProject.class);
        assert project != null;
        return new SourcesNodeList(project);
    }
    
    private static class SourcesNodeList implements NodeList<SourceGroupKey>, ChangeListener {
        
        private RailsProject project;
        
        private List<ChangeListener> listeners = new ArrayList<ChangeListener>();
        
        public SourcesNodeList(RailsProject proj) {
            project = proj;
        }
        
        public List<SourceGroupKey> keys() {
            if (this.project.getProjectDirectory() == null || !this.project.getProjectDirectory().isValid()) {
                return Collections.emptyList();
            }
//            Sources sources = getSources();
//            SourceGroup[] groups = sources.getSourceGroups(RailsProject.SOURCES_TYPE_RUBY);
//            // Here we're adding sources, tests
//            List result =  new ArrayList(groups.length);
//            for( int i = 0; i < groups.length; i++ ) {
//                result.add(new SourceGroupKey(groups[i]));
//            }

            Sources sources = getSources();
            SourceGroup[] groups = sources.getSourceGroups(RailsProject.SOURCES_TYPE_RUBY);
            // Here we're adding sources, tests
            List<SourceGroupKey> result =  new ArrayList<SourceGroupKey>(groups.length);
            for( int i = 0; i < groups.length; i++ ) {
                result.add(new SourceGroupKey(groups[i], getGenerator(groups[i].getName())));
            }

            SourceRoots roots = project.getSourceRoots();
            if (roots != null) {
                FileObject[] extra = roots.getExtraFiles();
                if (extra != null && extra.length > 0) {
                    for (FileObject f : extra) {
                        result.add(new SourceGroupKey(f));
                    }
                }
            }

            return result;
        }
        
        private Generator getGenerator(String subdir) {
            if (subdir.equals("app/controllers")) { // NOI18N
                return Generator.CONTROLLER;
            }
            if (subdir.equals("app/views")) { // NOI18N
                return Generator.CONTROLLER;
            }
            if (subdir.equals("app/models")) { // NOI18N
                return Generator.MODEL;
            }
            if (subdir.equals("db")) { // NOI18N
                return Generator.MIGRATION;
            }
            return Generator.NONE;
        }
        
        public synchronized void addChangeListener(ChangeListener l) {
            listeners.add(l);
        }
        
        public synchronized void removeChangeListener(ChangeListener l) {
            listeners.remove(l);
        }
        
        private void fireChange() {
            ArrayList<ChangeListener> list = new ArrayList<ChangeListener>();
            synchronized (this) {
                list.addAll(listeners);
            }
            Iterator<ChangeListener> it = list.iterator();
            while (it.hasNext()) {
                ChangeListener elem = it.next();
                elem.stateChanged(new ChangeEvent( this ));
            }
        }
        
        public Node node(SourceGroupKey key) {
            if (key.group == null) {
                try {
                    DataObject dobj = DataObject.find(key.fileObject);
                    return dobj.getNodeDelegate();
                } catch (DataObjectNotFoundException ex) {
                    Exceptions.printStackTrace(ex);
                }
                
            }
            return new PackageViewFilterNode(key.group, key.generator, project);
        }
        
        public void addNotify() {
            getSources().addChangeListener(this);
        }
        
        public void removeNotify() {
            getSources().removeChangeListener(this);
        }
        
        public void stateChanged(ChangeEvent e) {
            // setKeys(getKeys());
            // The caller holds ProjectManager.mutex() read lock
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    fireChange();
                }
            });
        }
        
        private Sources getSources() {
            return ProjectUtils.getSources(project);
        }
        
    }
    
    private static class SourceGroupKey {
        
        public final SourceGroup group;
        public final FileObject fileObject;
        public final Generator generator;
        
        SourceGroupKey(SourceGroup group, Generator generator) {
            this.group = group;
            this.fileObject = group.getRootFolder();
            this.generator = generator;
        }
        
        SourceGroupKey(FileObject fileObject) {
            this.group = null;
            this.fileObject = fileObject;
            this.generator = Generator.NONE;
        }
        
        @Override
        public int hashCode() {
            return fileObject.hashCode();
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SourceGroupKey)) {
                return false;
            } else {
                SourceGroupKey otherKey = (SourceGroupKey) obj;
                if (group == null || otherKey.group == null) {
                    if (group == null) {
                        return otherKey.group == null;
                    } else if (otherKey.group == null) {
                        return false;
                    } else {
                        return fileObject.equals(otherKey.fileObject);
                    }
                }
                String thisDisplayName = this.group.getDisplayName();
                String otherDisplayName = otherKey.group.getDisplayName();
                // XXX what is the operator binding order supposed to be here??
                return fileObject.equals(otherKey.fileObject) &&
                        thisDisplayName == null ? otherDisplayName == null : thisDisplayName.equals(otherDisplayName);
            }
        }
    }
    
    // Copied from inner class in Java Projects' PackageView class:
    /**
     * FilterNode which listens on the PackageViewSettings and changes the view to 
     * the package view or tree view
     *
     */
    private static final class RootNode extends FilterNode { // implements PropertyChangeListener {
        
        private SourceGroup sourceGroup;
        
        private RootNode (SourceGroup group, Generator generator) {
            // XXX?
            super(getOriginalNode(group, generator));
            this.sourceGroup = group;
            //JavaProjectSettings.addPropertyChangeListener(WeakListeners.propertyChange(this, JavaProjectSettings.class));
        }
        
//        public void propertyChange (PropertyChangeEvent event) {
//            if (JavaProjectSettings.PROP_PACKAGE_VIEW_TYPE.equals(event.getPropertyName())) {
//                changeOriginal(getOriginalNode(sourceGroup), true);
//            }
//        }
        
        private static Node getOriginalNode(SourceGroup group, Generator generator) {
            FileObject root = group.getRootFolder();
            //Guard condition, if the project is (closed) and deleted but not yet gced
            // and the view is switched, the source group is not valid.
            if ( root == null || !root.isValid()) {
                return new AbstractNode (Children.LEAF, Lookups.singleton(Generator.NONE));
            }
//            switch (JavaProjectSettings.getPackageViewType()) {
//                case JavaProjectSettings.TYPE_PACKAGE_VIEW:
//                    return new PackageRootNode(group);
//                case JavaProjectSettings.TYPE_TREE:
                    return new TreeRootNode(group, generator);
//                default:
//                    assert false : "Unknown PackageView Type"; //NOI18N
//                    return new PackageRootNode(group);
//            }
        }        
    }
    
    
    
    /** Yet another cool filter node just to add properties action
     */
    private static class PackageViewFilterNode extends FilterNode {
        
        private String nodeName;
        private Project project;
        
        Action[] actions;
        
        public PackageViewFilterNode(SourceGroup sourceGroup, Generator generator, Project project) {
            //super(PackageView.createPackageView(sourceGroup));
            super(new RootNode(sourceGroup, generator));
            
            this.project = project;
            this.nodeName = "Sources"; // NOI18N
        }
        
        
        public Action[] getActions(boolean context) {
            if (!context) {
                if (actions == null) {
                    Action superActions[] = super.getActions(context);
                    actions = new Action[superActions.length + 2];
                    System.arraycopy(superActions, 0, actions, 0, superActions.length);
                    actions[superActions.length] = null;
                    actions[superActions.length + 1] = new PreselectPropertiesAction(project, nodeName);
                }
                return actions;
            } else {
                return super.getActions(context);
            }
        }
        
    }
    
    
    /** The special properties action
     */
    static class PreselectPropertiesAction extends AbstractAction {
        
        private final Project project;
        private final String nodeName;
        private final String panelName;
        
        public PreselectPropertiesAction(Project project, String nodeName) {
            this(project, nodeName, null);
        }
        
        public PreselectPropertiesAction(Project project, String nodeName, String panelName) {
            super(NbBundle.getMessage(SourceNodeFactory.class, "LBL_Properties_Action"));
            this.project = project;
            this.nodeName = nodeName;
            this.panelName = panelName;
        }
        
        public void actionPerformed(ActionEvent e) {
            // RubyCustomizerProvider cp = (RubyCustomizerProvider) project.getLookup().lookup(RubyCustomizerProvider.class);
            CustomizerProviderImpl cp = (CustomizerProviderImpl) project.getLookup().lookup(CustomizerProviderImpl.class);
            if (cp != null) {
                cp.showCustomizer(nodeName, panelName);
            }
            
        }
    }
    
}
