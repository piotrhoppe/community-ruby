/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2009 Sun Microsystems, Inc. All rights reserved.
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
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2007 Sun
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

package org.netbeans.modules.ruby.testrunner.ui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.netbeans.api.project.Project;
import org.netbeans.api.ruby.platform.RubyPlatform;
import org.netbeans.modules.csl.api.DeclarationFinder.DeclarationLocation;
import org.netbeans.modules.gsf.testrunner.api.DiffViewAction;
import org.netbeans.modules.gsf.testrunner.api.Locator;
import org.netbeans.modules.gsf.testrunner.api.Testcase;
import org.netbeans.modules.gsf.testrunner.api.TestMethodNode;
import org.netbeans.modules.ruby.RubyDeclarationFinder;
import org.netbeans.modules.ruby.RubyUtils;
import org.openide.filesystems.FileObject;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Marian Petras, Erno Mononen
 */
public final class RubyTestMethodNode extends TestMethodNode {

    public RubyTestMethodNode(Testcase testcase, Project project) {
        super(testcase, project, Lookups.singleton(new Locator() {

            public void jumpToSource(Node node) {
                node.getPreferredAction().actionPerformed(null);
            }
        }));
    }

    /**
     */
    @Override
    public Action getPreferredAction() {
        // the location to jump from the node
        String testLocation = getTestLocation(testcase, project);
        String stackTrace = getTestCaseLineFromStackTrace(testcase);
        String jumpToLocation = stackTrace != null
                ? stackTrace
                : testLocation;

        return jumpToLocation == null
                ? new JumpToTestAction(testcase, project, NbBundle.getMessage(RubyTestMethodNode.class, "LBL_GoToSource"), false)
                : new JumpToCallStackAction(this, jumpToLocation);
    }
    
    static String getTestLocation(Testcase testcase, Project project) {
        if (testcase.getLocation() == null) {
            return null;
        }
        RubyPlatform platform = RubyPlatform.platformFor(project);
        if (platform != null && platform.isJRuby()) {
            // XXX: return no location for JRuby -- ExampleMethods#implementation_backtrace
            // behaves differently for MRI and JRuby, on JRuby the test file itself is not present
            return null;
        }
        return testcase.getLocation();
    }

        /**
     * Gets the line from the stack trace representing the last line in the test class.
     * If that can't be resolved
     * then returns the second line of the stack trace (the
     * first line represents the error message) or <code>null</code> if there
     * was no (usable) stack trace attached.
     *
     * @return
     */
    private String getTestCaseLineFromStackTrace(Testcase testcase) {
        if (testcase.getTrouble() == null) {
            return null;
        }
        String[] stacktrace = testcase.getTrouble().getStackTrace();
        if (stacktrace == null || stacktrace.length <= 1) {
            return null;
        }
        if (stacktrace.length > 2) {
            String candidateLine = findLocationLine(stacktrace, testcase.getName(), getFileName(testcase));
            if (candidateLine != null) {
                return  candidateLine;
            }
        }
        // fall back to the second line (the first one contains the failure msg)
        return stacktrace[1];
    }

    // package private for unit tests
    static final String findLocationLine(String[] stacktrace, String testName, String fileName) {
        String candidateLine = null;
        for (int i = 0; i < stacktrace.length; i++) {
            if (stacktrace[i].contains(fileName)) {
                if (stacktrace[i].contains(testName)) {
                    // if both the class and test method names are present
                    // in the line, it is the one we will return
                    candidateLine = stacktrace[i];
                    break;
                }
                // as a fallback use a line that contains the test file name -- we want
                // to return the first line found in the stack trace, hence the check for null
                if (candidateLine == null) {
                    candidateLine = stacktrace[i];
                }
            }
        }
        if (candidateLine != null) {
            return candidateLine;
        }
        return null;
    }

    /**
     * @return the name of the file (possibly including the extension if it can be resolved)  
     * where the given test case is defined.
      */
    private String getFileName(Testcase testcase) {
        FileObject testRoot = BaseTestMethodNodeAction.getTestSourceRoot(project);
        String testName = BaseTestMethodNodeAction.getTestMethod(testcase);
        DeclarationLocation location = RubyDeclarationFinder.getTestDeclaration(testRoot, testName, true, true);
        FileObject testFile = location.getFileObject();
        if (testFile != null) {
            return testFile.getNameExt();
        }
        // fallback
        return RubyUtils.camelToUnderlinedName(testcase.getClassName());
    }

    @Override
    public Action[] getActions(boolean context) {
        if (context) {
            return new Action[0];
        }
        List<Action> actions = new ArrayList<Action>();
        actions.add(getPreferredAction());
        actions.add(new RunTestMethodAction(testcase, project, NbBundle.getMessage(RubyTestMethodNode.class, "LBL_RerunTest"), false));
        actions.add(new RunTestMethodAction(testcase, project, NbBundle.getMessage(RubyTestMethodNode.class, "LBL_DebugTest"), true));
        actions.add(new DiffViewAction(testcase));
//        actions.add(new DisplayOutputForNodeAction(testcase.getOutput(), testcase.getSession()));
        return actions.toArray(new Action[actions.size()]);
    }

}
