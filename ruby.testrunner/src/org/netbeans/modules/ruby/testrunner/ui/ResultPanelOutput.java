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

package org.netbeans.modules.ruby.testrunner.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.accessibility.AccessibleContext;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.openide.ErrorManager;
import org.openide.util.NbBundle;

/**
 *
 * @author Marian Petras
 */
final class ResultPanelOutput extends JScrollPane
                              implements ActionListener {
    
    private static final boolean LOG = false;
    
    static final Color selectedFg;
    static final Color unselectedFg;
    static final Color selectedErr;
    static final Color unselectedErr;
    
    private static final int UPDATE_DELAY = 300;         //milliseconds
    
    static {
        
        /*
         * The property names and colour value constants were copied from class
         * org.netbeans.core.output2.WrappedTextView.
         */
        
        Color color;
        
        color = UIManager.getColor("nb.output.foreground.selected");    //NOI18N
        if (color == null) {
            color = UIManager.getColor("textText");                     //NOI18N
            if (color == null) {
                color = Color.BLACK;
            }
        }
        selectedFg = color;
        
        color = UIManager.getColor("nb.output.foreground");             //NOI18N
        if (color == null) {
            color = selectedFg;
        }
        unselectedFg = color;

        color = UIManager.getColor("nb.output.err.foreground.selected");//NOI18N
        if (color == null) {
            color = new Color(164, 0, 0);
        }
        selectedErr = color;
        
        color = UIManager.getColor("nb.output.err.foreground");         //NOI18N
        if (color == null) {
            color = selectedErr;
        }
        unselectedErr = color;
    }
    
    /** */
    private final JEditorPane textPane;
    /** */
    private final Document doc;
    /** */
    private final ResultDisplayHandler displayHandler;
    
    private Timer timer = null;
    
    /*
     * accessed from multiple threads but accessed only from blocks
     * synchronized with the ResultDisplayHandler's output queue lock
     */
    private volatile boolean timerRunning = false;
    
    /**
     * Creates a new instance of ResultPanelOutput
     */
    ResultPanelOutput(ResultDisplayHandler displayHandler) {
        super();
        if (LOG) {
            System.out.println("ResultPanelOutput.<init>");
        }
        
        textPane = new JEditorPane();
        textPane.setFont(new Font("monospaced", Font.PLAIN, getFont().getSize()));
        textPane.setEditorKit(new OutputEditorKit());
        textPane.setEditable(false);
        textPane.getCaret().setVisible(true);
        textPane.getCaret().setBlinkRate(0);
        textPane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        setViewportView(textPane);

        /*
         * On GTK L&F, background of the text pane is gray, even though it is
         * white on a JTextArea. The following is a hack to fix it:
         */
        Color background = UIManager.getColor("TextPane.background");   //NOI18N
        if (background != null) {
            textPane.setBackground(background);
        }

        doc = textPane.getDocument();

        AccessibleContext accessibleContext = textPane.getAccessibleContext();
        accessibleContext.setAccessibleName(
                NbBundle.getMessage(getClass(), "ACSN_OutputTextPane"));//NOI18N
        accessibleContext.setAccessibleDescription(
                NbBundle.getMessage(getClass(), "ACSD_OutputTextPane"));//NOI18N
        
        this.displayHandler = displayHandler;
    }
    
    /**
     */
    @Override
    public void addNotify() {
        super.addNotify();
        
        final Object[] pendingOutput;
        
        if (LOG) {
            System.out.println("ResultPanelOutput.addNotify()");
        }
        
        /*
         * We must make the following block synchronized using the output queue
         * lock to prevent a scenario that some new output would be delivered to
         * the display handler and the output listener would not be set yet.
         */
        synchronized (displayHandler.getOutputQueueLock()) {
            pendingOutput = displayHandler.consumeOutput();
            if (pendingOutput.length == 0) {
                displayHandler.setOutputListener(this);
            }
        }
        
        if (pendingOutput.length != 0) {
            displayOutput(pendingOutput);
            startTimer();
        }
    }
    
    /**
     */
    void outputAvailable() {

        /* Called from the AntLogger's thread */

        if (LOG) {
            System.out.println("ResultOutputPanel.outputAvailable() - called by the AntLogger");
        }
        //synchronized (displayHandler.getOutputQueueLock()):
        final Object[] pendingOutput = displayHandler.consumeOutput();
        assert pendingOutput.length != 0;
        new OutputDisplayer(pendingOutput).run();
        displayHandler.setOutputListener(null);
        if (!timerRunning) {
            startTimer();
        }
    }

    final class OutputDisplayer implements Runnable {
        private final Object[] output;
        OutputDisplayer(Object[] output) {
            this.output = output;
        }
        public void run() {
            if (!EventQueue.isDispatchThread()) {
                EventQueue.invokeLater(this);
                return;
            }
            displayOutput(output);
        }
    }
    
    /**
     * This method is called by a Swing timer (in the dispatch thread).
     */
    public void actionPerformed(ActionEvent e) {
        
        /* Called by the Swing timer (in the EventDispatch thread) */
        
        assert EventQueue.isDispatchThread();
        
        if (LOG) {
            System.out.println("ResultOutputPanel.actionPerformed(...) - called by the timer");
        }
        final Object[] pendingOutput = displayHandler.consumeOutput();
        if (pendingOutput.length != 0) {
            displayOutput(pendingOutput);
        } else {
            synchronized (displayHandler.getOutputQueueLock()) {
                stopTimer();
                displayHandler.setOutputListener(this);
            }
        }
    }
    
    /**
     */
    private void startTimer() {
        if (LOG) {
            System.out.println("ResultPanelOutput.startTimer()");
        }
        if (timer == null) {
            timer = new Timer(UPDATE_DELAY, this);
        }
        timerRunning = true;
        timer.start();
    }
    
    /**
     */
    private void stopTimer() {
        if (LOG) {
            System.out.println("ResultPanelOutput.stopTimer()");
        }
        if (timer != null) {
            timer.stop();
            timerRunning = false;
        }
    }

    /**
     */
    void displayOutput(final Object[] output) {
        assert EventQueue.isDispatchThread();

        if (LOG) {
            System.out.println("ResultPanelOutput.displayOutput(...):");
            for (int i = 0; output[i] != null; i++) {
                System.out.println("    " + output[i]);
            }
        }
        Object o;
        int index = 0;
        while ((o = output[index++]) != null) {
            boolean errOutput = false;
            if (o == Boolean.TRUE) {
                o = output[index++];
                errOutput = true;
            }
            displayOutputLine(o.toString(), errOutput);
        }
    }
    
    /**
     */
    private void displayOutputLine(final String text, final boolean error) {
        try {
            doc.insertString(doc.getLength(),
                             text + "\n",                               //NOI18N
                             error ? OutputDocument.attrs : null);
        } catch (BadLocationException ex) {
            ErrorManager.getDefault().notify(ErrorManager.ERROR, ex);
        }
    }
    
    //<editor-fold defaultstate="collapsed" desc="displayReport(Report)">
    /* *
     * /
    private void displayReport(final Report report) {
        if (report == null) {
            clear();
            return;
        }
        
        try {
            doc.insertString(
                    0,
                    NbBundle.getMessage(getClass(), "MSG_StdOutput"),   //NOI18N
                    headingStyle);
            doc.insertString(
                    doc.getLength(),
                    "\n",                                               //NOI18N
                    headingStyle);
            if ((report.outputStd != null) && (report.outputStd.length != 0)) {
                displayText(report.outputStd);
            }
            doc.insertString(
                    doc.getLength(),
                    "\n\n",                                             //NOI18N
                    outputStyle);
            doc.insertString(
                    doc.getLength(),
                    NbBundle.getMessage(getClass(), "MSG_ErrOutput"),   //NOI18N
                    headingStyle);
            if ((report.outputErr != null) && (report.outputErr.length != 0)) {
                doc.insertString(
                        doc.getLength(),
                        "\n",                                           //NOI18N
                        headingStyle);
                displayText(report.outputErr);
            }
        } catch (BadLocationException ex) {
            ErrorManager.getDefault().notify(ErrorManager.ERROR, ex);
        }
    }
    */
    //</editor-fold>
    
    /**
     */
    private void clear() {
        assert EventQueue.isDispatchThread();
        
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException ex) {
            ErrorManager.getDefault().notify(ErrorManager.ERROR, ex);
        }
    }
    
    //<editor-fold defaultstate="collapsed" desc="displayText(String[])">
    /* *
     * /
    private void displayText(final String[] lines) throws BadLocationException {
        final int limit = lines.length - 1;
        for (int i = 0; i < limit; i++) {
            doc.insertString(doc.getLength(),
                             lines[i] + '\n',
                             outputStyle);
        }
        doc.insertString(doc.getLength(),
                         lines[limit],
                         outputStyle);
    }
    */
    //</editor-fold>
    
    //<editor-fold defaultstate="collapsed" desc="display(DisplayContents)">
    /* *
     * /
    private void display(ResultDisplayHandler.DisplayContents display) {
        assert EventQueue.isDispatchThread();
        
        Report report = display.getReport();
        String msg = display.getMessage();
        if (report != null) {
            displayReport(report);
        } else {
            clear();
        }
    }
    //</editor-fold>

    /* *
     * /
    //<editor-fold defaultstate="collapsed" desc="updateDisplay()">
    private void updateDisplay() {
        ResultDisplayHandler.DisplayContents display
                                                = displayHandler.getDisplay();
        if (display != null) {
            display(display);
        }
    }
    */
    //</editor-fold>
    
    //<editor-fold defaultstate="collapsed" desc="stateChanged(ChangeEvent)">
    /* *
     * /
    public void stateChanged(ChangeEvent e) {
        updateDisplay();
    }
     */
    //</editor-fold>
    
    /**
     */
    @Override
    public boolean requestFocusInWindow() {
        return textPane.requestFocusInWindow();
    }

}
