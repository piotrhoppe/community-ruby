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
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
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

package org.netbeans.modules.ruby.railsprojects;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.Collections;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.netbeans.api.gsfpath.classpath.ClassPath;
import org.netbeans.api.gsfpath.classpath.GlobalPathRegistry;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.ruby.platform.RubyPlatformProvider;
import org.netbeans.modules.ruby.railsprojects.classpath.ClassPathProviderImpl;
import org.netbeans.modules.ruby.railsprojects.queries.RailsProjectEncodingQueryImpl;
import org.netbeans.modules.ruby.railsprojects.server.RailsServerManager;
import org.netbeans.modules.ruby.railsprojects.ui.RailsLogicalViewProvider;
import org.netbeans.modules.ruby.railsprojects.ui.customizer.CustomizerProviderImpl;
import org.netbeans.spi.project.AuxiliaryConfiguration;
import org.netbeans.spi.project.SubprojectProvider;
import org.netbeans.spi.project.support.LookupProviderSupport;
import org.netbeans.modules.ruby.spi.project.support.rake.RakeProjectEvent;
import org.netbeans.modules.ruby.spi.project.support.rake.RakeProjectHelper;
import org.netbeans.modules.ruby.spi.project.support.rake.RakeProjectListener;
import org.netbeans.modules.ruby.spi.project.support.rake.FilterPropertyProvider;
import org.netbeans.modules.ruby.spi.project.support.rake.GeneratedFilesHelper;
import org.netbeans.modules.ruby.spi.project.support.rake.ProjectXmlSavedHook;
import org.netbeans.modules.ruby.spi.project.support.rake.PropertyEvaluator;
import org.netbeans.modules.ruby.spi.project.support.rake.PropertyProvider;
import org.netbeans.modules.ruby.spi.project.support.rake.PropertyUtils;
import org.netbeans.modules.ruby.spi.project.support.rake.ReferenceHelper;
import org.netbeans.spi.project.ui.PrivilegedTemplates;
import org.netbeans.spi.project.ui.ProjectOpenedHook;
import org.netbeans.spi.project.ui.RecommendedTemplates;
import org.netbeans.spi.project.ui.support.UILookupMergerSupport;
import org.openide.ErrorManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.Mutex;
import org.openide.util.Utilities;
import org.openide.util.lookup.Lookups;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Represents one plain Ruby project.
 * @author Jesse Glick, et al.
 */
public class RailsProject implements Project, RakeProjectListener {
    // TODO - keep in sync with RubyProject
    public static final String SOURCES_TYPE_RUBY = "ruby"; // NOI18N
    
    private static final Icon Ruby_PROJECT_ICON = new ImageIcon(Utilities.loadImage("org/netbeans/modules/ruby/railsprojects/ui/resources/rails.png")); // NOI18N

    protected final AuxiliaryConfiguration aux;
    protected final RakeProjectHelper helper;
    protected final PropertyEvaluator eval;
    protected final ReferenceHelper refHelper;
    protected final GeneratedFilesHelper genFilesHelper;
    protected final Lookup lookup;
    protected final UpdateHelper updateHelper;
//    private MainClassUpdater mainClassUpdater;
    protected SourceRoots sourceRoots;
    protected SourceRoots testRoots;
    private RailsSources sources;
    
    protected RailsProject(RakeProjectHelper helper) throws IOException {
        this.helper = helper;
        eval = createEvaluator();
        aux = helper.createAuxiliaryConfiguration();
        refHelper = new ReferenceHelper(helper, aux, eval);
        genFilesHelper = new GeneratedFilesHelper(helper);
        this.updateHelper = new UpdateHelper (this, this.helper, this.aux, this.genFilesHelper,
            UpdateHelper.createDefaultNotifier());

        lookup = createLookup(aux);
        helper.addRakeProjectListener(this);
    }

    /**
     * Returns the project directory
     * @return the directory the project is located in
     */
    public FileObject getProjectDirectory() {
        return helper.getProjectDirectory();
    }

    @Override
    public String toString() {
        return "RailsProject[" + FileUtil.getFileDisplayName(getProjectDirectory()) + "]"; // NOI18N
    }
    
    private PropertyEvaluator createEvaluator() {
        // It is currently safe to not use the UpdateHelper for PropertyEvaluator; UH.getProperties() delegates to APH
        // Adapted from APH.getStandardPropertyEvaluator (delegates to ProjectProperties):
        PropertyEvaluator baseEval1 = PropertyUtils.sequentialPropertyEvaluator(
                helper.getStockPropertyPreprovider(),
                helper.getPropertyProvider(RailsConfigurationProvider.CONFIG_PROPS_PATH));
        PropertyEvaluator baseEval2 = PropertyUtils.sequentialPropertyEvaluator(
                helper.getStockPropertyPreprovider(),
                helper.getPropertyProvider(RakeProjectHelper.PRIVATE_PROPERTIES_PATH));
        return PropertyUtils.sequentialPropertyEvaluator(
                helper.getStockPropertyPreprovider(),
                helper.getPropertyProvider(RailsConfigurationProvider.CONFIG_PROPS_PATH),
                new ConfigPropertyProvider(baseEval1, "nbproject/private/configs", helper), // NOI18N
                helper.getPropertyProvider(RakeProjectHelper.PRIVATE_PROPERTIES_PATH),
                PropertyUtils.userPropertiesProvider(baseEval2,
                    "user.properties.file", FileUtil.toFile(getProjectDirectory())), // NOI18N
                new ConfigPropertyProvider(baseEval1, "nbproject/configs", helper), // NOI18N
                helper.getPropertyProvider(RakeProjectHelper.PROJECT_PROPERTIES_PATH));
    }
    private static final class ConfigPropertyProvider extends FilterPropertyProvider implements PropertyChangeListener {
        private final PropertyEvaluator baseEval;
        private final String prefix;
        private final RakeProjectHelper helper;
        public ConfigPropertyProvider(PropertyEvaluator baseEval, String prefix, RakeProjectHelper helper) {
            super(computeDelegate(baseEval, prefix, helper));
            this.baseEval = baseEval;
            this.prefix = prefix;
            this.helper = helper;
            baseEval.addPropertyChangeListener(this);
        }
        public void propertyChange(PropertyChangeEvent ev) {
            if (RailsConfigurationProvider.PROP_CONFIG.equals(ev.getPropertyName())) {
                setDelegate(computeDelegate(baseEval, prefix, helper));
            }
        }
        private static PropertyProvider computeDelegate(PropertyEvaluator baseEval, String prefix, RakeProjectHelper helper) {
            String config = baseEval.getProperty(RailsConfigurationProvider.PROP_CONFIG);
            if (config != null) {
                return helper.getPropertyProvider(prefix + "/" + config + ".properties"); // NOI18N
            } else {
                return PropertyUtils.fixedPropertyProvider(Collections.<String,String>emptyMap());
            }
        }
    }
    
    public PropertyEvaluator evaluator() {
        return eval;
    }

    public ReferenceHelper getReferenceHelper () {
        return this.refHelper;
    }

    public UpdateHelper getUpdateHelper() {
        return this.updateHelper;
    }
    
    public Lookup getLookup() {
        return lookup;
    }
    
    public RakeProjectHelper getRakeProjectHelper() {
        return helper;
    }
    
    protected Lookup createLookup(AuxiliaryConfiguration aux) {
        SubprojectProvider spp = refHelper.createSubprojectProvider();
        sources = new RailsSources (this.helper, evaluator(), getSourceRoots(), getTestSourceRoots());
        Lookup base = Lookups.fixed(new Object[] {
            new Info(),
            aux,
            helper.createCacheDirectoryProvider(),
            spp,
            new RailsActionProvider( this, this.updateHelper ),
            new RailsLogicalViewProvider(this, this.updateHelper, evaluator(), spp, refHelper),
            new ClassPathProviderImpl(this.helper, evaluator(), getSourceRoots(),getTestSourceRoots()), //Does not use APH to get/put properties/cfgdata
            // new RubyCustomizerProvider(this, this.updateHelper, evaluator(), refHelper),
            new CustomizerProviderImpl(this, this.updateHelper, evaluator(), refHelper, this.genFilesHelper),        
            new ProjectXmlSavedHookImpl(),
            new ProjectOpenedHookImpl(),
            sources,
            new RailsSharabilityQuery (this.helper, evaluator(), getSourceRoots(), getTestSourceRoots()), //Does not use APH to get/put properties/cfgdata
            new RailsFileBuiltQuery (this.helper, evaluator(),getSourceRoots(),getTestSourceRoots()), //Does not use APH to get/put properties/cfgdata
            new RecommendedTemplatesImpl (this.updateHelper),
            this, // never cast an externally obtained Project to RailsProject - use lookup instead
            new RailsProjectOperations(this),
            new RailsConfigurationProvider(this),
            UILookupMergerSupport.createPrivilegedTemplatesMerger(),
            UILookupMergerSupport.createRecommendedTemplatesMerger(),
            LookupProviderSupport.createSourcesMerger(),
            new RailsProjectEncodingQueryImpl(evaluator()),
            evaluator(),
            new RailsServerManager(this),
            new RailsFileLocator(null, this),
            new RubyPlatformProvider(evaluator())
        });
        return LookupProviderSupport.createCompositeLookup(base, "Projects/org-netbeans-modules-ruby-railsprojects/Lookup"); //NOI18N
    }

    public void configurationXmlChanged(RakeProjectEvent ev) {
        if (ev.getPath().equals(RakeProjectHelper.PROJECT_XML_PATH)) {
            // Could be various kinds of changes, but name & displayName might have changed.
            Info info = (Info)getLookup().lookup(ProjectInformation.class);
            info.firePropertyChange(ProjectInformation.PROP_NAME);
            info.firePropertyChange(ProjectInformation.PROP_DISPLAY_NAME);
        }
    }

    public void propertiesChanged(RakeProjectEvent ev) {
        // currently ignored (probably better to listen to evaluator() if you need to)
    }
    
    // Package private methods -------------------------------------------------------------

    /**
     * Returns the source roots of this project
     * @return project's source roots
     */
    public synchronized SourceRoots getSourceRoots() {        
        if (this.sourceRoots == null) { //Local caching, no project metadata access
            this.sourceRoots = new SourceRoots(this.updateHelper, evaluator(), getReferenceHelper(), "source-roots", false, "src.{0}{1}.dir"); //NOI18N
        }
        return this.sourceRoots;
    }
    
    public synchronized SourceRoots getTestSourceRoots() {
        if (this.testRoots == null) { //Local caching, no project metadata access
            this.testRoots = new SourceRoots(this.updateHelper, evaluator(), getReferenceHelper(), "test-roots", true, "test.{0}{1}.dir"); //NOI18N
        }
        return this.testRoots;
    }
    
    // Currently unused (but see #47230):
    /** Store configured project name. */
    public void setName(final String name) {
        ProjectManager.mutex().writeAccess(new Mutex.Action<Void>() {
            public Void run() {
                Element data = helper.getPrimaryConfigurationData(true);
                // XXX replace by XMLUtil when that has findElement, findText, etc.
                NodeList nl = data.getElementsByTagNameNS(RailsProjectType.PROJECT_CONFIGURATION_NAMESPACE, "name"); // NOI18N
                Element nameEl;
                if (nl.getLength() == 1) {
                    nameEl = (Element) nl.item(0);
                    NodeList deadKids = nameEl.getChildNodes();
                    while (deadKids.getLength() > 0) {
                        nameEl.removeChild(deadKids.item(0));
                    }
                } else {
                    nameEl = data.getOwnerDocument().createElementNS(RailsProjectType.PROJECT_CONFIGURATION_NAMESPACE, "name"); // NOI18N
                    data.insertBefore(nameEl, /* OK if null */data.getChildNodes().item(0));
                }
                nameEl.appendChild(data.getOwnerDocument().createTextNode(name));
                helper.putPrimaryConfigurationData(data, true);
                return null;
            }
        });
    }




    // Private innerclasses ----------------------------------------------------------------
    
    protected class Info implements ProjectInformation {
        
        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
        
        public Info() {}
        
        void firePropertyChange(String prop) {
            pcs.firePropertyChange(prop, null, null);
        }
        
        public String getName() {
            return PropertyUtils.getUsablePropertyName(getDisplayName());
        }
        
        public String getDisplayName() {
            return ProjectManager.mutex().readAccess(new Mutex.Action<String>() {
                public String run() {
                    Element data = updateHelper.getPrimaryConfigurationData(true);
                    // XXX replace by XMLUtil when that has findElement, findText, etc.
                    NodeList nl = data.getElementsByTagNameNS(RailsProjectType.PROJECT_CONFIGURATION_NAMESPACE, "name"); // NOI18N
                    if (nl.getLength() == 1) {
                        nl = nl.item(0).getChildNodes();
                        if (nl.getLength() == 1 && nl.item(0).getNodeType() == Node.TEXT_NODE) {
                            return ((Text) nl.item(0)).getNodeValue();
                        }
                    }
                    return "???"; // NOI18N
                }
            });
        }
        
        public Icon getIcon() {
            return Ruby_PROJECT_ICON;
        }
        
        public Project getProject() {
            return RailsProject.this;
        }
        
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            pcs.addPropertyChangeListener(listener);
        }
        
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            pcs.removePropertyChangeListener(listener);
        }
        
    }

    void notifyDeleting() {
        helper.removeRakeProjectListener(this);
        sources.notifyDeleting();
    }

    protected final class ProjectXmlSavedHookImpl extends ProjectXmlSavedHook {
        
        public ProjectXmlSavedHookImpl() {}
        
        protected void projectXmlSaved() throws IOException {
            //May be called by {@link AuxiliaryConfiguration#putConfigurationFragment}
            //which didn't affect the j2seproject 
/*
            if (updateHelper.isCurrent()) {
                //Refresh build-impl.xml only for j2seproject/2
                genFilesHelper.refreshBuildScript(
                    GeneratedFilesHelper.BUILD_IMPL_XML_PATH,
                    RailsProject.class.getResource("resources/build-impl.xsl"),
                    false);
                genFilesHelper.refreshBuildScript(
                    GeneratedFilesHelper.BUILD_XML_PATH,
                    RailsProject.class.getResource("resources/build.xsl"),
                    false);
            }
*/
        }
        
    }

    //static boolean bootRegistered = false;
    
    protected final class ProjectOpenedHookImpl extends ProjectOpenedHook {
        
        public ProjectOpenedHookImpl() {}
        
        protected void projectOpened() {
            open();
        }
        
        protected void projectClosed() {
            // Probably unnecessary, but just in case:
            try {
                ProjectManager.getDefault().saveProject(RailsProject.this);
            } catch (IOException e) {
                ErrorManager.getDefault().notify(e);
            }
            
            // unregister project's classpaths to GlobalPathRegistry
            ClassPathProviderImpl cpProvider = lookup.lookup(ClassPathProviderImpl.class);
            //GlobalPathRegistry.getDefault().unregister(ClassPath.BOOT, cpProvider.getProjectClassPaths(ClassPath.BOOT));
            GlobalPathRegistry.getDefault().unregister(ClassPath.SOURCE, cpProvider.getProjectClassPaths(ClassPath.SOURCE));
            //GlobalPathRegistry.getDefault().unregister(ClassPath.COMPILE, cpProvider.getProjectClassPaths(ClassPath.COMPILE));

            //XXX: to compile workaround            
//            if (mainClassUpdater != null) {
//                mainClassUpdater.unregister ();
//                mainClassUpdater = null;
//            }
            
        }
        
    }

    /*
     * Run the open hook.
     * For use from unit tests.
     */
    void open() {
        // Check up on build scripts.
/*
        try {
            if (updateHelper.isCurrent()) {
                //Refresh build-impl.xml only for j2seproject/2
                genFilesHelper.refreshBuildScript(
                    GeneratedFilesHelper.BUILD_IMPL_XML_PATH,
                    RailsProject.class.getResource("resources/build-impl.xsl"),
                    true);
                genFilesHelper.refreshBuildScript(
                    GeneratedFilesHelper.BUILD_XML_PATH,
                    RailsProject.class.getResource("resources/build.xsl"),
                    true);
            }                
        } catch (IOException e) {
            ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, e);
        }
*/
        // register project's classpaths to GlobalPathRegistry
        ClassPathProviderImpl cpProvider = lookup.lookup(ClassPathProviderImpl.class);
        GlobalPathRegistry.getDefault().register(ClassPath.BOOT, cpProvider.getProjectClassPaths(ClassPath.BOOT));
        GlobalPathRegistry.getDefault().register(ClassPath.SOURCE, cpProvider.getProjectClassPaths(ClassPath.SOURCE));
        //GlobalPathRegistry.getDefault().register(ClassPath.COMPILE, cpProvider.getProjectClassPaths(ClassPath.COMPILE));
        
        //register updater of main.class
        //the updater is active only on the opened projects

/*
        // Make it easier to run headless builds on the same machine at least.
        ProjectManager.mutex().writeAccess(new Mutex.Action<Void>() {
            public Void run() {
                EditableProperties ep = updateHelper.getProperties(RakeProjectHelper.PRIVATE_PROPERTIES_PATH);
                File buildProperties = new File(System.getProperty("netbeans.user"), "build.properties"); // NOI18N
                ep.setProperty("user.properties.file", buildProperties.getAbsolutePath()); //NOI18N                    
                updateHelper.putProperties(RakeProjectHelper.PRIVATE_PROPERTIES_PATH, ep);
                try {
                    ProjectManager.getDefault().saveProject(RailsProject.this);
                } catch (IOException e) {
                    ErrorManager.getDefault().notify(e);
                }
                return null;
            }
        });
        RailsLogicalViewProvider physicalViewProvider = (RailsLogicalViewProvider)
            RailsProject.this.getLookup().lookup (RailsLogicalViewProvider.class);
        if (physicalViewProvider != null &&  physicalViewProvider.hasBrokenLinks()) {   
            BrokenReferencesSupport.showAlert();
        }
*/
    }
        
    private static final class RecommendedTemplatesImpl implements RecommendedTemplates, PrivilegedTemplates {
        RecommendedTemplatesImpl (UpdateHelper helper) {
            this.helper = helper;
        }
        
        private UpdateHelper helper;
        
        // List of primarily supported templates
        
        private static final String[] APPLICATION_TYPES = new String[] { 
            "rails",         // NOI18N
            "ruby",         // NOI18N
            "XML",                  // NOI18N
            "simple-files"          // NOI18N
        };
        
        private static final String[] PRIVILEGED_NAMES = new String[] {
            "Templates/Ruby/_view.rhtml", // NOI18N
            "Templates/Ruby/_view.erb", // NOI18N
            "Templates/Ruby/main.rb", // NOI18N
            "Templates/Ruby/test.rb", // NOI18N
            "Templates/Ruby/class.rb", // NOI18N
            "Templates/Ruby/module.rb", // NOI18N
            "Templates/Ruby/rakefile.rb", // NOI18N
            "Templates/Ruby/rspec.rb", // NOI18N
            "Templates/Ruby/empty.rjs", // NOI18N
        };
        
        public String[] getRecommendedTypes() {
            return APPLICATION_TYPES;
        }
        
        public String[] getPrivilegedTemplates() {
            return PRIVILEGED_NAMES;
        }
        
    }
}
