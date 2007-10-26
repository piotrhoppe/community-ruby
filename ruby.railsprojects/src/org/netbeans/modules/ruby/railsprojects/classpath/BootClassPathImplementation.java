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
package org.netbeans.modules.ruby.railsprojects.classpath;

import java.beans.PropertyChangeEvent;
import org.netbeans.api.ruby.platform.RubyInstallation;
import org.netbeans.spi.gsfpath.classpath.ClassPathImplementation;
import org.netbeans.spi.gsfpath.classpath.PathResourceImplementation;
import org.netbeans.spi.gsfpath.classpath.support.ClassPathSupport;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.netbeans.modules.ruby.rubyproject.SharedRubyProjectProperties;
import org.netbeans.modules.ruby.spi.project.support.rake.PropertyEvaluator;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.WeakListeners;

final class BootClassPathImplementation implements ClassPathImplementation, PropertyChangeListener {

//    private static final String PLATFORM_ACTIVE = "platform.active";        //NOI18N
//    private static final String ANT_NAME = "platform.ant.name";             //NOI18N
//    private static final String J2SE = "j2se";                              //NOI18N

    private File projectDirectory;
    private final PropertyEvaluator evaluator;
//    private JavaPlatformManager platformManager;
    //name of project active platform
    private String activePlatformName;
    //active platform is valid (not broken reference)
    private boolean isActivePlatformValid;
    private List<PathResourceImplementation> resourcesCache;
    private PropertyChangeSupport support = new PropertyChangeSupport(this);

    public BootClassPathImplementation(File projectDirectory, PropertyEvaluator evaluator) {
        this.projectDirectory = projectDirectory;
        assert evaluator != null;
        this.evaluator = evaluator;
        evaluator.addPropertyChangeListener(WeakListeners.propertyChange(this, evaluator));
    }

    public synchronized List<PathResourceImplementation> getResources() {
        if (this.resourcesCache == null) {
//            JavaPlatform jp = findActivePlatform ();
//            if (jp != null) {
                //TODO: May also listen on CP, but from Platform it should be fixed.
            List<PathResourceImplementation> result = new ArrayList<PathResourceImplementation>();
            Set<URL> nonGemUrls = RubyInstallation.getInstance().getNonGemLoadPath();
            
            for (URL url : nonGemUrls) {
                result.add(ClassPathSupport.createResource(url));
            }

            Map<String,URL> gemUrls = RubyInstallation.getInstance().getGemUrls();
            Map<String,String> gemVersions = RubyInstallation.getInstance().getGemVersions();
            
            // Perhaps I can filter vendor/rails iff the installation contains it

            // Add in all the vendor/ paths, if any
            File vendor = new File(projectDirectory, "vendor");
            if (vendor.exists()) {
                List<URL> vendorPlugins = getVendorPlugins(vendor);
                for (URL url : vendorPlugins) {
                    result.add(ClassPathSupport.createResource(url));
                }

                
                // TODO - handle multiple gem versions in the same repository
                List<URL> combinedGems = mergeVendorGems(vendor, 
                        new HashMap<String,String>(gemVersions), 
                        new HashMap<String,URL>(gemUrls));
                for (URL url : combinedGems) {
                    result.add(ClassPathSupport.createResource(url));
                }

            } else {
                for (URL url : gemUrls.values()) {
                    result.add(ClassPathSupport.createResource(url));
                }
            }
            
            // Java support?
            if (RubyInstallation.getInstance().isJRubySet()) {
                String java = evaluator.getProperty(SharedRubyProjectProperties.INCLUDE_JAVA);
                if (java != null && Boolean.valueOf(java)) {
                    try {
                        FileObject javaSupport = RubyInstallation.getInstance().getJRubyJavaSupport();
                        if (javaSupport != null) {
                            URL url = FileUtil.toFile(javaSupport).toURI().toURL();
                            result.add(ClassPathSupport.createResource(url));
                        }
                    } catch (MalformedURLException mufe) {
                        Exceptions.printStackTrace(mufe);
                    }
                }
            }

            resourcesCache = Collections.unmodifiableList (result);
            RubyInstallation.getInstance().removePropertyChangeListener(this);
            RubyInstallation.getInstance().addPropertyChangeListener(this);
        }
        return this.resourcesCache;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.support.addPropertyChangeListener (listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.support.removePropertyChangeListener (listener);
    }

//    private JavaPlatform findActivePlatform () {
//        if (this.platformManager == null) {
//            this.platformManager = JavaPlatformManager.getDefault();
//            this.platformManager.addPropertyChangeListener(WeakListeners.propertyChange(this, this.platformManager));
//        }                
//        this.activePlatformName = evaluator.getProperty(PLATFORM_ACTIVE);
//        final JavaPlatform activePlatform = RubyProjectUtil.getActivePlatform (this.activePlatformName);
//        this.isActivePlatformValid = activePlatform != null;
//        return activePlatform;
//    }
//    
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() == RubyInstallation.getInstance() && evt.getPropertyName().equals("roots")) {
            resetCache();
        }
//        if (evt.getSource() == this.evaluator && evt.getPropertyName().equals(PLATFORM_ACTIVE)) {
//            //Active platform was changed
//            resetCache ();
//        }
//        else if (evt.getSource() == this.platformManager && JavaPlatformManager.PROP_INSTALLED_PLATFORMS.equals(evt.getPropertyName()) && activePlatformName != null) {
//            //Platform definitions were changed, check if the platform was not resolved or deleted
//            if (this.isActivePlatformValid) {
//                if (RubyProjectUtil.getActivePlatform (this.activePlatformName) == null) {
//                    //the platform was not removed
//                    this.resetCache();
//                }
//            }
//            else {
//                if (RubyProjectUtil.getActivePlatform (this.activePlatformName) != null) {
//                    this.resetCache();
//                }
//            }
//        }
    }

    /**
     * Resets the cache and firesPropertyChange
     */
    private void resetCache () {
        synchronized (this) {
            resourcesCache = null;
        }
        support.firePropertyChange(PROP_RESOURCES, null, null);
    }

    private List<URL> mergeVendorGems(File vendorFile, Map<String,String> gemVersions, Map<String,URL> gemUrls) {
        chooseGems(vendorFile.listFiles(), gemVersions, gemUrls);
        
        return new ArrayList<URL>(gemUrls.values());
    }
    
    private static void chooseGems(File[] gems, Map<String,String> gemVersions, Map<String,URL> gemUrls) {        
        // Try to match foo-1.2.3, foo-bar-1.2.3, foo-bar-1.2.3-ruby
        Pattern GEM_FILE_PATTERN = Pattern.compile("(\\S|-)+-((\\d+)\\.(\\d+)\\.(\\d+))(-\\S+)?"); // NOI18N

        for (File f : gems) {
            if (!f.isDirectory()) {
                continue;
            }

            String n = f.getName();
            
            if ("plugins".equals(n)) {
                // Special cased separately
                continue;
            }

            if ("rails".equals(n)) { // NOI18N
                // Special case - what do we do here?
                chooseRails(f.listFiles(), gemVersions, gemUrls);
                continue;
            }

            if ("gems".equals(n) || "gems-jruby".equals(n)) { // NOI18N
                // Support both having gems in the vendor/ top directory as well as in a gems/ subdirectory            }
                chooseGems(f.listFiles(), gemVersions, gemUrls);
            }

            if (n.indexOf('-') == -1) {
                continue;
            }

            Matcher m = GEM_FILE_PATTERN.matcher(n);
            if (!m.matches()) {
                continue;
            }
            
            File lib = new File(f, "lib");
            if (lib.exists()) {
                try {
                    URL url = lib.toURI().toURL();
                    String name = m.group(1);
                    String version = m.group(2);
                    addGem(gemVersions, gemUrls, name, version, url);
                } catch (MalformedURLException mufe) {
                    Exceptions.printStackTrace(mufe);
                }
            }
        }
    }
    
    private static void addGem(Map<String,String> gemVersions, Map<String,URL> gemUrls, String name, String version, URL url) {
        if (!gemVersions.containsKey(name) || 
                RubyInstallation.compareGemVersions(version, gemVersions.get(name)) > 0) {
            gemVersions.put(name, version);
            gemUrls.put(name, url);
        }
    }

    private static void chooseRails(File[] gems, Map<String,String> gemVersions, Map<String,URL> gemUrls) {        
        for (File f : gems) {
            if (!f.isDirectory()) {
                continue;
            }
            
            String name = f.getName();
            // actionpack/lib/action_pack/version.r
            String middleName = name;
            if (name.indexOf('_') == -1) {
                if (name.startsWith("action") || name.startsWith("active")) {
                    middleName = name.substring(0, 6) + "_" +name.substring(6);
                }
            }
            File lib = new File(f, "lib");
            if (lib.exists()) {
                File versionFile = new File(lib, middleName + File.separator + "version.rb");
                if (versionFile.exists()) {
                    String version = getVersionString(versionFile);
                    if (version != null) {
                        try {
                            URL url = lib.toURI().toURL();
                            addGem(gemVersions, gemUrls, name, version, url);
                        } catch (MalformedURLException mufe) {
                            Exceptions.printStackTrace(mufe);
                        }
                    }
                }
            }
        }
    }
    
    private static String getVersionString(File version) {
        try {
            Pattern VERSION_ELEMENT = Pattern.compile("\\s*[A-Z]+\\s*=\\s*(\\d+)\\s*");
            BufferedReader br = new BufferedReader(new FileReader(version));
            StringBuilder sb = new StringBuilder();
            int major = 0;
            int minor = 0;
            int tiny = 0;
            for (int line = 0; line < 10; line++) {
                String s = br.readLine();
                if (s == null) {
                    break;
                }

                if (s.indexOf("MAJOR") != -1) {
                    Matcher m = VERSION_ELEMENT.matcher(s);
                    if (m.matches()) {
                        major = Integer.parseInt(m.group(1));
                    }
                } else if (s.indexOf("MINOR") != -1) {
                    Matcher m = VERSION_ELEMENT.matcher(s);
                    if (m.matches()) {
                        minor = Integer.parseInt(m.group(1));
                    }
                } else if (s.indexOf("TINY") != -1) {
                    Matcher m = VERSION_ELEMENT.matcher(s);
                    if (m.matches()) {
                        tiny = Integer.parseInt(m.group(1));
                    }
                }
            }
            br.close();
            
        
            return major + "." + minor + "." + tiny;
        } catch (IOException ioe) {
            Exceptions.printStackTrace(ioe);
        }
        
        return null;
    }
    
    private List<URL> getVendorPlugins(File vendor) {
        assert vendor != null;
        
        File plugins = new File(vendor, "plugins");
        if (!plugins.exists()) {
            return Collections.emptyList();
        }
        
        List<URL> urls = new ArrayList<URL>();

        for (File f : plugins.listFiles()) {
            File lib = new File(f, "lib");
            if (lib.exists()) {
                // TODO - preindex via version lookup somehow?
                try {
                    URL url = lib.toURI().toURL();
                    urls.add(url);
                    // TODO - find versions for the plugins?
                    //Map<String, File> nameMap = gemFiles.get(name);
                    //if (nameMap != null) {
                    //    String version = nameMap.keySet().iterator().next();
                    //    RubyInstallation.getInstance().setGemRoot(url, name+ "-" + version);
                    //}
                } catch (MalformedURLException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
        
        return urls;
    }
}
