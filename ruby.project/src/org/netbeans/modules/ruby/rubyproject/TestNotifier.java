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

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.editor.Coloring;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.SettingsDefaults;
import org.netbeans.editor.StatusBar;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.ruby.rubyproject.execution.OutputRecognizer;
import org.netbeans.modules.ruby.rubyproject.execution.OutputRecognizer.ActionText;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;

/**
 * Notifier which listens on test output and if it sees what looks like a a failure,
 * alerts to the problem in the editor status bar; otherwise it reports successes
 * in the status bar.
 *
 * @author Tor Norbye
 */
public class TestNotifier extends OutputRecognizer implements Runnable {
    /** Should we accumulate scores? */
    private boolean accumulate;
    /** Should we only post errors in the editor footer if there are failures? */
    private boolean showSuccesses;
    
    /** Counts for the various types of errors we see in the output */
    private int examples;
    private int failures;
    private int notImplemented;
    private int tests;
    private int assertions;
    private int errors;
    private boolean seenTestUnit;
    private boolean seenRSpec;
    private boolean seenRake;
    
    /** 
     * Create a new TestNotifier.
     * <p>
     * @param accumulate If true, accumulate the amounts reported and report a final
     * summary of all. Otherwise, it will simply warn if there are errors and
     * forward the message to the editor.
     * @param showSuccesses If true, post results to the editor window even if
     * there are no failures
     */
    public TestNotifier(boolean accumulate, boolean showSuccesses) {
        this.accumulate = accumulate;
        this.showSuccesses = showSuccesses;
    }
    
    /** Turn off notification? */
    private static final boolean QUIET = Boolean.getBoolean("ruby.quiet.tests"); // NOI18N

    // Test::Unit:   test/unit/testresult.rb#
    private final static Pattern TEST_UNIT_PATTERN = 
        // The final \\s? in the patterns is to allow \r on Windows
        Pattern.compile("(\\d+) tests, (\\d+) assertions, (\\d+) failures, (\\d+) errors\\s?"); // NOI18N
         
    // RSpec: see {rspec}/lib/spec/runner/formatter/base_text_formatter.rb#dump_summary
    // "not implemented" changed to "pending" in rspec 1.0.8
    private final static Pattern RSPEC_PATTERN = 
        Pattern.compile("(\\d+) examples?, (\\d)+ failures?(, (\\d+) (pending|not implemented))?\\s?"); // NOI18N
    
    // Rake: lib/tasks/testing.rake:53:  raise "Test failures" unless exceptions.empty?
    private final static Pattern RAKE_PATTERN =
        Pattern.compile("Test failures\\s?"); // NOI18N
    
    private final Pattern[] PATTERNS = new Pattern[] { TEST_UNIT_PATTERN, RSPEC_PATTERN, RAKE_PATTERN };
    
    /** Schedlued message to be displayed in the editor */
    private String message;
    /** Schedule message type - if true, show as warning */
    private boolean warning;

    @Override
    public ActionText processLine(String outputLine) {
        if (QUIET) {
            return null;
        }

        String line = outputLine;
        if (Util.containsAnsiColors(outputLine)) {
            line = Util.stripAnsiColors(outputLine);
        }

        for (Pattern pattern : PATTERNS) {
            Matcher match = pattern.matcher(line);
            if (match.matches()) {
                String summary;
                if (accumulate) {
                    addTotals(pattern, match, line);
                    summary = getSummary();
                } else {
                    summary = line;
                }
                
                StatusDisplayer.getDefault().setStatusText(NbBundle.getMessage(TestNotifier.class, "TestsCompleted", summary));
                // TODO - fail on "not implemented" too?
                boolean failure = false;
                if (pattern == RSPEC_PATTERN) {
                    if (line.indexOf("0 failures") == -1) {
                        failure = true;
                    }
                } else if (pattern == TEST_UNIT_PATTERN) {
                    if ((line.indexOf(" 0 failures") == -1) || (line.indexOf(" 0 errors") == -1)) { // NOI18N
                        failure = true;
                    }
                } else {
                    assert pattern == RAKE_PATTERN;
                    failure = true;
                }

                if (failure) {
                    warning = true;
                    if (!accumulate && pattern == RAKE_PATTERN) {
                        message = summary;
                    } else {
                        message = NbBundle.getMessage(AutoTestSupport.class, "TestsFailed", summary);
                    }
                    run();
                } else if (showSuccesses || notImplemented > 0) {
                    warning = false;
                    message = NbBundle.getMessage(AutoTestSupport.class, "TestsCompleted", summary);
                    run();
                }
            }
        }

        if (line != outputLine) {
            return new ActionText(new String[] { line }, null, null, null);
        }
        return null;
    }
    
    public void run() {
        if (message == null) {
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this);
            return;
        }

        JTextComponent pane = EditorRegistry.lastFocusedComponent();
        if (pane != null) {
            if (pane.isShowing()) {
                if (warning) {
                    org.netbeans.editor.Utilities.setStatusBoldText(pane, message);
                } else {
                    // Attempt to show the text in green
                    EditorUI eui = Utilities.getEditorUI(pane);
                    if (eui != null) {
                        StatusBar statusBar = eui.getStatusBar();
                        if (statusBar != null) {
                            Coloring coloring = null;
                            if (notImplemented > 0) {
                                // Not implemented rspecs: show yellow rather than green
                                coloring = new Coloring(SettingsDefaults.defaultFont, Color.BLACK, Color.YELLOW);
                            } else {
                                coloring = new Coloring(SettingsDefaults.defaultFont, Color.BLACK, Color.GREEN);
                            }
                            statusBar.setText(StatusBar.CELL_MAIN, message, coloring);
                            return;
                        }
                    }

                    org.netbeans.editor.Utilities.setStatusText(pane, message);
                }
            }
        }
    }
    
    /** For unit tests only.
     * @todo Use a mock object for the status displayer and test processLine itself instead
     */
    boolean recognizeLine(String line) {
        for (Pattern pattern : PATTERNS) {
            Matcher match = pattern.matcher(line);
            if (match.matches()) {
                return true;
            }
        }
        
        return false;
    }

    private void addTotals(Pattern pattern, Matcher matcher, String line) {
        assert PATTERNS.length == 3; // If you add more patterns, make sure you update the below logic
        if (pattern == TEST_UNIT_PATTERN) {
            seenTestUnit = true;
            tests += Integer.parseInt(matcher.group(1));
            assertions += Integer.parseInt(matcher.group(2));
            failures += Integer.parseInt(matcher.group(3));
            errors += Integer.parseInt(matcher.group(4));
        } else if (pattern == RSPEC_PATTERN) {
            seenRSpec = true;
            examples += Integer.parseInt(matcher.group(1));
            failures += Integer.parseInt(matcher.group(2));
            if (matcher.group(4) != null) {
                notImplemented += Integer.parseInt(matcher.group(4));
            }
        } else {
            assert pattern == RAKE_PATTERN;
            seenRake = true;
            errors += 1;
        }
    }

    private void appendSummary(StringBuilder sb, String s) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(s);
    }
    
    private String getCountDescription(String oneKey, String manyKey, int count) {
        String countString = Integer.toString(count);
        
        return NbBundle.getMessage(TestNotifier.class, count == 1 ? oneKey : manyKey, countString);
    }
    
    String getSummary() {
        StringBuilder sb = new StringBuilder(80);
        if (seenTestUnit || tests > 0) {
            appendSummary(sb, getCountDescription("OneTest", "ManyTests", tests));
        }
        if (seenTestUnit || assertions > 0) {
            appendSummary(sb, getCountDescription("OneAssert", "ManyAssert", assertions));
        }
        if (seenRSpec || examples > 0) {
            appendSummary(sb, getCountDescription("OneExample", "ManyExamples", examples));
        }
        if (!seenRake || seenTestUnit || seenRSpec) {
            appendSummary(sb, getCountDescription("OneFailure", "ManyFailures", failures));
        }
        if (seenTestUnit || errors > 0) {
            appendSummary(sb, getCountDescription("OneError", "ManyErrors", errors));
        }
        if (notImplemented > 0) {
            appendSummary(sb, getCountDescription("OneNotImpl", "ManyNotImpl", notImplemented));
        }
        
        return sb.toString();
    }
}
