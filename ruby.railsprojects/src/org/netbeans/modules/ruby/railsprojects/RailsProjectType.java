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

package org.netbeans.modules.ruby.railsprojects;

import java.io.IOException;
import org.netbeans.api.project.Project;
import org.netbeans.modules.ruby.spi.project.support.rake.RakeBasedProjectType;
import org.netbeans.modules.ruby.spi.project.support.rake.RakeProjectHelper;

/**
 * Factory for simple Ruby projects.
 * @author Jesse Glick
 */
public final class RailsProjectType implements RakeBasedProjectType {

    public static final String TYPE = "org.netbeans.modules.ruby.railsprojects"; // NOI18N
    private static final String PROJECT_CONFIGURATION_NAME = "data"; // NOI18N
    public static final String PROJECT_CONFIGURATION_NAMESPACE = "http://www.netbeans.org/ns/rails-project/1"; // NOI18N
    private static final String PRIVATE_CONFIGURATION_NAME = "data"; // NOI18N
    private static final String PRIVATE_CONFIGURATION_NAMESPACE = "http://www.netbeans.org/ns/rails-project-private/1"; // NOI18N
    
    /** Do nothing, just a service. */
    public RailsProjectType() {}
    
    public String getType() {
        return TYPE;
    }
    
    public Project createProject(RakeProjectHelper helper) throws IOException {
        return new RailsProject(helper);
    }

    public String getPrimaryConfigurationDataElementName(boolean shared) {
        return shared ? PROJECT_CONFIGURATION_NAME : PRIVATE_CONFIGURATION_NAME;
    }
    
    public String getPrimaryConfigurationDataElementNamespace(boolean shared) {
        return shared ? PROJECT_CONFIGURATION_NAMESPACE : PRIVATE_CONFIGURATION_NAMESPACE;
    }
    
}
