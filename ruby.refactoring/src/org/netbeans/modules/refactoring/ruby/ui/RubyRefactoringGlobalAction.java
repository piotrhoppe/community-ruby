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
package org.netbeans.modules.refactoring.ruby.ui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.Hashtable;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import org.openide.awt.Actions;
import org.openide.cookies.EditorCookie;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.text.CloneableEditorSupport.Pane;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.NodeAction;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.windows.TopComponent;

/**
 * 
 * RubyRefactoringGlobalAction
 * This class is copy of RefactoringGlobalAction, which is not in public packages
 * @author Jan Becicka
 */
public abstract class RubyRefactoringGlobalAction extends NodeAction {

    /** Creates a new RubyRefactoringGlobalActiongGlobalAction */
    public RubyRefactoringGlobalAction(String name, Icon icon) {
        setName(name);
        setIcon(icon);
    }
    
    public final String getName() {
        return (String) getValue(Action.NAME);
    }
    
    protected void setName(String name) {
        putValue(Action.NAME, name);
    }
    
    protected void setMnemonic(char m) {
        putValue(Action.MNEMONIC_KEY, new Integer(m));
    }
    
    public org.openide.util.HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    protected Lookup getLookup(Node[] n) {
        InstanceContent ic = new InstanceContent();
        for (Node node:n)
            ic.add(node);
        if (n.length>0) {
            EditorCookie tc = getTextComponent(n[0]);
            if (tc != null) {
                ic.add(tc);
            }
        }
        ic.add(new Hashtable(0));
        return new AbstractLookup(ic);
    }

    
    protected static EditorCookie getTextComponent(Node n) {
        DataObject dobj = n.getCookie(DataObject.class);
        if (dobj != null) {
            EditorCookie ec = dobj.getCookie(EditorCookie.class);
            if (ec != null) {
                TopComponent activetc = TopComponent.getRegistry().getActivated();
                if (activetc instanceof Pane) {
                    return ec;
                }
            }
        }
        return null;
    }
    
    public abstract void performAction(Lookup context);
    
    protected abstract boolean enable(Lookup context);
    
    public final void performAction(final Node[] activatedNodes) {
        performAction(getLookup(activatedNodes));
    }

    protected boolean enable(Node[] activatedNodes) {
        return enable(getLookup(activatedNodes));
    }
    
    
    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new ContextAction(actionContext);
    }
    
    public class ContextAction implements Action, Presenter.Menu, Presenter.Popup, Presenter.Toolbar {

        Lookup context;

        public ContextAction(Lookup context) {
            this.context=context;
        }
        
        public Object getValue(String arg0) {
            return RubyRefactoringGlobalAction.this.getValue(arg0);
        }
        
        public void putValue(String arg0, Object arg1) {
            RubyRefactoringGlobalAction.this.putValue(arg0, arg1);
        }
        
        public void setEnabled(boolean arg0) {
            RubyRefactoringGlobalAction.this.setEnabled(arg0);
        }
        
        public boolean isEnabled() {
            return enable(context);
        }
        
        public void addPropertyChangeListener(PropertyChangeListener arg0) {
            RubyRefactoringGlobalAction.this.addPropertyChangeListener(arg0);
        }
        
        public void removePropertyChangeListener(PropertyChangeListener arg0) {
            RubyRefactoringGlobalAction.this.removePropertyChangeListener(arg0);
        }
        
        public void actionPerformed(ActionEvent arg0) {
            RubyRefactoringGlobalAction.this.performAction(context);
        }
        public JMenuItem getMenuPresenter() {
            if (isMethodOverridden(RubyRefactoringGlobalAction.this, "getMenuPresenter")) { // NOI18N

                return RubyRefactoringGlobalAction.this.getMenuPresenter();
            } else {
                return new Actions.MenuItem(this, true);
            }
        }

        public JMenuItem getPopupPresenter() {
            if (isMethodOverridden(RubyRefactoringGlobalAction.this, "getPopupPresenter")) { // NOI18N

                return RubyRefactoringGlobalAction.this.getPopupPresenter();
            } else {
                return new Actions.MenuItem(this, false);
            }
        }

        public Component getToolbarPresenter() {
            if (isMethodOverridden(RubyRefactoringGlobalAction.this, "getToolbarPresenter")) { // NOI18N

                return RubyRefactoringGlobalAction.this.getToolbarPresenter();
            } else {
                return new Actions.ToolbarButton(this);
            }
        }

        private boolean isMethodOverridden(NodeAction d, String name) {
            try {
                Method m = d.getClass().getMethod(name, new Class[0]);

                return m.getDeclaringClass() != CallableSystemAction.class;
            } catch (java.lang.NoSuchMethodException ex) {
                ex.printStackTrace();
                throw new IllegalStateException("Error searching for method " + name + " in " + d); // NOI18N
            }
        }        
    }
}
