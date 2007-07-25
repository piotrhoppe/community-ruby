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
package org.netbeans.modules.ruby.rubyproject;

import java.io.File;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import org.netbeans.api.project.Project;
import org.netbeans.api.ruby.platform.RubyInstallation;
import org.netbeans.modules.ruby.rubyproject.api.RubyExecution;
import org.netbeans.modules.ruby.rubyproject.execution.ExecutionDescriptor;
import org.netbeans.modules.ruby.rubyproject.execution.OutputRecognizer;
import org.netbeans.modules.ruby.rubyproject.execution.OutputRecognizer.FileLocation;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;


/**
 * Various methods for supporting AutoTest execution
 *
 * @author Tor Norbye
 */
public class AutoTestSupport {
    private Project project;
    private Lookup context;
    private String charsetName;

    public AutoTestSupport(Lookup context, Project project, String charsetName) {
        this.context = context;
        this.project = project;
        this.charsetName = charsetName;
    }

    public static boolean isInstalled() {
        return RubyInstallation.getInstance().isValidAutoTest(false);
    }

    public void start() {
        if (!RubyInstallation.getInstance().isValidAutoTest(true)) {
            return;
        }

        // TODO - the output here should emit into the Tasklist!
        // TODO - if you select this a second time, I've gotta front an existing
        // one if it's already running
        // I can store the existing autotest input window from execution service's
        // getInputOutput method, and reopen it. Maybe I could just call isClosed
        // on it first to see if it's still running, or if not, I could add a
        // task listener and stash these references per project.
        File pwd = FileUtil.toFile(project.getProjectDirectory());

        RubyFileLocator fileLocator = new RubyFileLocator(context, project);
        String displayName = NbBundle.getMessage(AutoTestSupport.class, "AutoTest");
        ExecutionDescriptor desc = new ExecutionDescriptor(displayName, pwd, RubyInstallation.getInstance().getAutoTest());
        desc.additionalArgs("-v"); // NOI18N
        desc.fileLocator(fileLocator);
        desc.showProgress(false);
        desc.addOutputRecognizer(new AutoTestNotifier());
        desc.addOutputRecognizer(RubyExecution.RUBY_COMPILER);
        new RubyExecution(desc, charsetName).run();
    }

    private static class AutoTestNotifier extends OutputRecognizer implements Runnable {

        private String message;

        @Override
        public FileLocation processLine(String line) {
            if (line.matches("\\d+ tests, \\d+ assertions, \\d+ failures, \\d+ errors")) { // NOI18N
                // NOI18N
                StatusDisplayer.getDefault().setStatusText(NbBundle.getMessage(AutoTestSupport.class, "AutoTestCompleted", line));

                // Extra attention on failures
                if ((line.indexOf(" 0 failures") == -1) || (line.indexOf(" 0 errors") == -1)) { // NOI18N
                    // NOI18N
                    message = NbBundle.getMessage(AutoTestSupport.class, "AutoTestFailure", line);
                    run(); // might redispatch to event thread
                }
            }

            return null;
        }

        public void run() {
            if (message == null) {
                return;
            }

            if (!SwingUtilities.isEventDispatchThread()) {
                // getOpenedPanes on the EditorCookie requires it
                SwingUtilities.invokeLater(this);

                return;
            }

            org.openide.nodes.Node[] nodes = TopComponent.getRegistry().getActivatedNodes();

            if (nodes == null) {
                return;
            }

            for (org.openide.nodes.Node node : nodes) {
                EditorCookie ec = node.getCookie(EditorCookie.class);

                if (ec == null) {
                    continue;
                }

                JEditorPane[] panes = ec.getOpenedPanes();

                if (panes == null) {
                    continue;
                }

                for (JEditorPane pane : panes) {
                    if (pane.isShowing()) {
                        //org.netbeans.editor.Utilities.setStatusText(pane, message, coloring);
                        org.netbeans.editor.Utilities.setStatusBoldText(pane, message);

                        return;
                    }
                }
            }
        }
    }
}
