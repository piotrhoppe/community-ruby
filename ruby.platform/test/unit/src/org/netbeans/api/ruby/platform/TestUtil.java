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
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2007 Sun
 * Microsystems, Inc. All Rights Reserved.
 */

package org.netbeans.api.ruby.platform;

import java.io.File;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public final class TestUtil {

    private TestUtil() {
    }

    public static File getXTestJRubyHome() {
        String destDir = System.getProperty("xtest.jruby.home");
        if (destDir == null) {
            throw new RuntimeException("xtest.jruby.home property has to be set when running within binary distribution " + destDir);
        }
        return new File(destDir);
    }

    public static String getXTestJRubyHomePath() {
        return getXTestJRubyHome().getAbsolutePath();
    }
    
    public static FileObject getXTestJRubyHomeFO() {
        return FileUtil.toFileObject(getXTestJRubyHome());
    }
}