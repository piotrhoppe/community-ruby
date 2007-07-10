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

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JEditorPane;
import javax.swing.text.JTextComponent;

import org.netbeans.editor.Utilities;
import org.netbeans.modules.ruby.AstUtilities;
import org.netbeans.modules.ruby.NbUtilities;
import org.netbeans.modules.ruby.RubyUtils;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;


/**
 * Rails action for jumping to the action corresponding to a view, or the
 * view corresponding to an action.
 * 
 * @author Tor Norbye
 */
public class GotoActionView extends AbstractAction {
    public GotoActionView() {
        super(NbBundle.getMessage(GotoActionView.class, "rails-goto-action-view")); // NOI18N
        putValue("PopupMenuText",
            NbBundle.getBundle(GotoActionView.class).getString("editor-popup-goto-action-view")); // NOI18N
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public void actionPerformed(ActionEvent ev) {
        JEditorPane pane = NbUtilities.getOpenPane();

        if (pane != null) {
            actionPerformed(pane);
        }
    }

    private void actionPerformed(final JTextComponent target) {
        FileObject fo = NbUtilities.findFileObject(target);

        if (fo != null) {
            // TODO - Look up project and complain if it's not a Rails project

            // See if it's a controller:
            if (fo.getName().endsWith("_controller")) {
                gotoView(target, fo, "_controller", "controllers");
            } else if (fo.getName().endsWith("_helper")) {
                gotoView(target, fo, "_helper", "helpers");
            } else if (RubyUtils.isRhtmlFile(fo) || fo.getExt().equalsIgnoreCase("mab") || fo.getExt().equalsIgnoreCase("rjs")) { // NOI18N
                // It's a view
                gotoAction(target, fo);
            } else {
                Utilities.setStatusBoldText(target,
                    "This action only applies to Controllers and Views in Ruby On Rails projects");
            }

            return;
        }
    }

    private void notFound(JTextComponent target) {
        Utilities.setStatusBoldText(target, "Controller file not found: Error in the project?");
    }

    // Move from something like app/controllers/credit_card_controller.rb#debit()
    // to app/views/credit_card/debit.rhtml
    private void gotoView(JTextComponent target, FileObject file, String fileSuffix, String parentAppDir) {
        // This should be a view.
        if (!file.getName().endsWith(fileSuffix)) {
            Utilities.setStatusBoldText(target, "This action only applies to Rails Actions");

            return;
        }

        FileObject controllerFile = file;

        int offset = 0;

        // Find the offset of the file we're in, if any
        if (target.getCaret() != null) {
            offset = target.getCaret().getDot();
        }

        // Get the name of the method corresponding to the offset
        String methodName = AstUtilities.getMethodName(controllerFile, offset);

        FileObject viewFile = null;

        try {
            String controllerName =
                file.getName().substring(0, file.getName().length() - fileSuffix.length());

            String path = controllerName;

            // Find app dir, and build up a relative path to the view file in the process
            FileObject app = file.getParent();

            while (app != null) {
                if (app.getName().equals(parentAppDir) && // NOI18N
                        ((app.getParent() == null) || app.getParent().getName().equals("app"))) { // NOI18N
                    app = app.getParent();

                    break;
                }

                path = app.getNameExt() + "/" + path; // NOI18N
                app = app.getParent();
            }

            if (app == null) {
                notFound(target);
            }

            FileObject viewsFolder = app.getFileObject("views/" + path); // NOI18N

            if (viewsFolder == null) {
                notFound(target);
            }

            if (methodName != null) {
                String[] exts = { "rhtml", "html.erb", "erb", "rjs", "mab" }; // NOI18N
                for (String ext : exts) {
                    viewFile = viewsFolder.getFileObject(methodName, ext);
                    if (viewFile != null) {
                        break;
                    }
                }
            }

            if (viewFile == null) {
                // The caret was likely not inside any of the methods, or in a method that
                // isn't directly tied to a view
                // Just pick one of the views. Try index first.
                viewFile = viewsFolder.getFileObject("index.rhtml"); // NOI18N
                if (viewFile == null) {
                    for (FileObject child : viewsFolder.getChildren()) {
                        String ext = child.getExt();
                        if (RubyUtils.isRhtmlFile(child) || ext.equalsIgnoreCase("mab") || ext.equalsIgnoreCase("rjs")) { // NOI18N
                            viewFile = child;

                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            notFound(target);

            return;
        }

        if (viewFile == null) {
            notFound(target);

            return;
        }

        NbUtilities.open(viewFile, 0, null);
    }

    // Move from something like app/views/credit_card/debit.rhtml to
    //  app/controllers/credit_card_controller.rb#debit()
    private void gotoAction(JTextComponent target, FileObject file) {
        // This should be a view.
        String ext = file.getExt();
        if (!RubyUtils.isRhtmlFile(file) && !ext.equalsIgnoreCase("mab") && !ext.equalsIgnoreCase("rjs")) { // NOI18N
            Utilities.setStatusBoldText(target, "This action only applies to Views");

            return;
        }

        // TODO - instead of relying on Path manipulation here, should I just
        // use the RubyIndex to locate the class and method?
        FileObject controllerFile = null;
        String action = file.getName();

        try {
            file = file.getParent();

            String fileName = file.getName();
            String path = "";

            if (!fileName.startsWith("_")) { // NOI18N
                                             // For partials like "_foo", just use the surrounding view
                path = fileName;
            }

            // Find app dir, and build up a relative path to the view file in the process
            FileObject app = file.getParent();

            while (app != null) {
                if (app.getName().equals("views") && // NOI18N
                        ((app.getParent() == null) || app.getParent().getName().equals("app"))) { // NOI18N
                    app = app.getParent();

                    break;
                }

                path = app.getNameExt() + "/" + path; // NOI18N
                app = app.getParent();
            }

            if (app == null) {
                notFound(target);
            }

            controllerFile = app.getFileObject("controllers/" + path + "_controller.rb"); // NOI18N
        } catch (Exception e) {
            notFound(target);

            return;
        }

        if (controllerFile == null) {
            notFound(target);

            return;
        }

        // TODO: Find the position of the #view method
        int offset = AstUtilities.findOffset(controllerFile, action);

        NbUtilities.open(controllerFile, offset, "def " + action);
    }
}
