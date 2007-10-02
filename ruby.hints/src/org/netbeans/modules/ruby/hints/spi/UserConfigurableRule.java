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
 * Portions Copyrighted 2007 Sun Microsystems, Inc.
 */
package org.netbeans.modules.ruby.hints.spi;

import java.util.prefs.Preferences;
import javax.swing.JComponent;

/**
 * A rule which is configurable (enabled, properties, etc) by the user.
 *
 * @author Tor Norbye
 */
public interface UserConfigurableRule extends Rule {

    /** Gets unique ID of the rule
     */
    public String getId();

    /** Gets longer description of the rule
     */
    public String getDescription();

    /** Finds out whether the rule is currently enabled.
     * @return true if enabled false otherwise.
     */
    public boolean getDefaultEnabled();
    
    // XXX Add Others
    // public JPanel getCustomizer() or Hash map getParameters()
//    /** Gets the UI description for this rule. It is fine to return null
//     * to get the default behavior. Notice that the Preferences node is a copy
//     * of the node returned from {link:getPreferences()}. This is in oder to permit 
//     * canceling changes done in the options dialog.<BR>
//     * Default implementation return null, which results in no customizer.
//     * It is fine to return null (as default implementation does)
//     * @param node Preferences node the customizer should work on.
//     * @return Component which will be shown in the options dialog.
//     */    
    public JComponent getCustomizer(Preferences node);
}
