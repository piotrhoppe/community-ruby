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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.netbeans.api.ruby.platform.RubyInstallation;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;


/**
 * @todo PLUGIN has a --with-generator flag - how do I support that?
 *
 * @author Tor Norbye
 */
public class Generator {
    public static Generator NONE = new Generator(null, null, 0);
    public static Generator CONTROLLER = new Generator("controller", null, null, "Views", null, 1); // NOI18N
    public static Generator INTEGRATION_TEST = new Generator("integration_test", null, 1); // NOI18N
    public static Generator MAILER = new Generator("mailer", null, null, "Views", null, 1); // NOI18N
    public static Generator MIGRATION = new Generator("migration", null, 1); // NOI18N
    public static Generator MODEL = new Generator("model", null, 1); // NOI18N
    public static Generator PLUGIN = new Generator("plugin", null, 1); // NOI18N
    public static Generator SCAFFOLD =
        new Generator("scaffold", null, "ModelName", "ScaffControllerName", "ScaffoldActions", 1); // NOI18N
    public static Generator SESSION_MIGRATION = new Generator("session_migration", null, 1); // NOI18N
    public static Generator WEB_SERVICE =
        new Generator("web_service", null, null, "ApiMethods", null, 1); // NOI18N

    private String name;
    private FileObject location;
    private String nameKey;
    private String arg1Key;
    private String arg2Key;
    private int argsRequired;

    public Generator(String name, FileObject location, int argsRequired) {
        this.name = name;
        this.location = location;
        // Unknown meaning of the first argument
        this.nameKey = "Arguments";  // NOI18N
        this.argsRequired = argsRequired;
    }

    private Generator(String name, FileObject location, String nameKey, String arg1Key,
        String arg2Key, int argsRequired) {
        this(name, location, argsRequired);
        this.nameKey = nameKey;
        this.arg1Key = arg1Key;
        this.arg2Key = arg2Key;
    }
    
    /** Add in the "known" or builtin generators. */
    static List<Generator> getBuiltinGenerators() {
        List<Generator> list = new ArrayList<Generator>();
        list.add(CONTROLLER);
        list.add(INTEGRATION_TEST);
        list.add(MODEL);
        list.add(MIGRATION);
        list.add(MAILER);
        list.add(PLUGIN);
        list.add(SCAFFOLD);
        list.add(SESSION_MIGRATION);
        list.add(WEB_SERVICE);
        // TODO - missing scaffold_resource!
        
        return list;
    }
    
    public int getArgsRequired() {
        return argsRequired;
    }

    public String getName() {
        return name;
    }

    public FileObject getDir() {
        return location;
    }

    public String getUsage() {
        if (this == NONE) {
            return null;
        }

        File generatorDir = null;

        if (location == null) {
            // Lazy evaluation for the builtin generators
            // Generator dir
            File gemLocation = null;
            
            String version = RubyInstallation.getInstance().getVersion("rails"); // NOI18N
            
            if (version != null) {
                gemLocation =
                    new File(RubyInstallation.getInstance().getRubyLibGemDir() + File.separator +
                        "gems" + File.separator + "rails" + "-" + version); // NOI18N
            } else if (!Utilities.isWindows()) {
                File rubyHome = RubyInstallation.getInstance().getRubyHome();
                if (rubyHome != null) {
                    File railsDir = new File(rubyHome, "/share/rails/railties"); // NOI18N
                    if (railsDir.exists()) {
                        gemLocation = railsDir;
                    }
                }
            }

            if (gemLocation != null) {
                //Example: jruby-0.9.2/lib/ruby/gems/1.8/gems/rails-1.1.6/lib/rails_generator/generators/components       
                generatorDir = new File(gemLocation,
                        "lib" + File.separator + // NOI18N
                        "rails_generator" + File.separator + "generators" + File.separator + // NOI18N
                        "components" + File.separator + name); // NOI18N
            }
        } else {
            generatorDir = FileUtil.toFile(location);
        }

        File usageFile = new File(generatorDir, "USAGE"); // NOI18N

        if (!usageFile.exists()) {
            // At least the "resource" generator on railties seems to live
            // in the "wrong" place; check the additional location
            usageFile = new File(generatorDir, "templates" + File.separator + "USAGE"); // NOI18N
            if (!usageFile.exists()) {
                return null;
            }
        }

        StringBuilder sb = new StringBuilder();

        try {
            BufferedReader fr = new BufferedReader(new FileReader(usageFile));

            while (true) {
                String line = fr.readLine();

                if (line == null) {
                    break;
                }

                sb.append(line);
                sb.append("\n"); // NOI18N
            }

            if (sb.length() > 0) {
                return sb.toString();
            }
        } catch (IOException ioe) {
            Exceptions.printStackTrace(ioe);
        }

        return null;
    }

    String getNameLabel() {
        if (nameKey != null) {
            return NbBundle.getMessage(Generator.class, nameKey);
        } else {
            return NbBundle.getMessage(Generator.class, "Name");
        }
    }

    String getArg1Label() {
        if (arg1Key != null) {
            return NbBundle.getMessage(Generator.class, arg1Key);
        } else {
            return null;
        }
    }

    String getArg2Label() {
        if (arg2Key != null) {
            return NbBundle.getMessage(Generator.class, arg2Key);
        } else {
            return null;
        }
    }
}
