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
 * Portions Copyrighted 2007 Sun Microsystems, Inc.
 */
package org.netbeans.modules.ruby.hints;

import java.util.Collections;
import org.netbeans.api.gsf.OffsetRange;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.ruby.RubyTestBase;
import java.util.Map;
import org.netbeans.api.gsf.CompilationInfo;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.text.Document;
import org.jruby.ast.Node;
import org.netbeans.api.gsf.CompilationInfo;
import org.netbeans.api.gsf.OffsetRange;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.ruby.AstUtilities;
import org.netbeans.modules.ruby.hints.infrastructure.AbstractHint;
import org.netbeans.modules.ruby.hints.infrastructure.AstRule;
import org.netbeans.modules.ruby.hints.infrastructure.RubyHintsProvider;
import org.netbeans.modules.ruby.hints.infrastructure.RulesManager;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.LazyFixList;
import org.openide.filesystems.FileUtil;

/**
 * Common utility methods for testing a hint
 *
 * @author Tor Norbye
 */
public abstract class HintTestBase extends RubyTestBase {

    public HintTestBase(String testName) {
        super(testName);
    }

    private String annotate(BaseDocument doc, List<ErrorDescription> result, int caretOffset) throws Exception {
        Map<OffsetRange, ErrorDescription> posToDesc = new HashMap<OffsetRange, ErrorDescription>();
        Set<OffsetRange> ranges = new HashSet<OffsetRange>();
        for (ErrorDescription desc : result) {
            int start = desc.getRange().getBegin().getOffset();
            int end = desc.getRange().getEnd().getOffset();
            OffsetRange range = new OffsetRange(start, end);
            posToDesc.put(range, desc);
            ranges.add(range);
        }
        StringBuilder sb = new StringBuilder();
        String text = doc.getText(0, doc.getLength());
        Map<Integer, OffsetRange> starts = new HashMap<Integer, OffsetRange>(100);
        Map<Integer, OffsetRange> ends = new HashMap<Integer, OffsetRange>(100);
        for (OffsetRange range : ranges) {
            starts.put(range.getStart(), range);
            ends.put(range.getEnd(), range);
        }

        int index = 0;
        int length = text.length();
        while (index < length) {
            int lineStart = Utilities.getRowStart(doc, index);
            int lineEnd = Utilities.getRowEnd(doc, index);
            OffsetRange lineRange = new OffsetRange(lineStart, lineEnd);
            boolean skipLine = true;
            for (OffsetRange range : ranges) {
                if (lineRange.containsInclusive(range.getStart()) || lineRange.containsInclusive(range.getEnd())) {
                    skipLine = false;
                }
            }
            if (!skipLine) {
                List<ErrorDescription> descsOnLine = null;
                int underlineStart = -1;
                int underlineEnd = -1;
                for (int i = lineStart; i <= lineEnd; i++) {
                    if (i == caretOffset) {
                        sb.append("^");
                    }
                    if (starts.containsKey(i)) {
                        if (descsOnLine == null) {
                            descsOnLine = new ArrayList<ErrorDescription>();
                        }
                        underlineStart = i-lineStart;
                        OffsetRange range = starts.get(i);
                        ErrorDescription desc = posToDesc.get(range);
                        if (desc != null) {
                            descsOnLine.add(desc);
                        }
                    }
                    if (ends.containsKey(i)) {
                        underlineEnd = i-lineStart;
                    }
                    sb.append(text.charAt(i));
                }
                if (underlineStart != -1) {
                    for (int i = 0; i < underlineStart; i++) {
                        sb.append(" ");
                    }
                    for (int i = underlineStart; i < underlineEnd; i++) {
                        sb.append("-");
                    }
                    sb.append("\n");
                }
                if (descsOnLine != null) {
                    for (ErrorDescription desc : descsOnLine) {
                        sb.append("HINT:");
                        sb.append(desc.getDescription());
                        sb.append("\n");
                        LazyFixList list = desc.getFixes();
                        if (list != null) {
                            List<Fix> fixes = list.getFixes();
                            if (fixes != null) {
                                for (Fix fix : fixes) {
                                    sb.append("FIX:");
                                    sb.append(fix.getText());
                                    sb.append("\n");
                                }
                            }
                        }
                    }
                }
            }
            index = lineEnd + 1;
        }

        return sb.toString();
    }
    
    protected ComputedHints getHints(NbTestCase test, AstRule hint, String relFilePath, String caretLine) throws Exception {
        File rubyFile = new File(test.getDataDir(), relFilePath);
        if (!rubyFile.exists()) {
            NbTestCase.fail("File " + rubyFile + " not found.");
        }

        CompilationInfo info = getInfo(relFilePath);
        Node root = AstUtilities.getRoot(info);
        assertNotNull("Unexpected parse error in test case " + 
                FileUtil.getFileDisplayName(info.getFileObject()) + "\nErrors = " + 
                info.getDiagnostics(), root);

        String text = info.getText();

        int caretOffset = -1;
        if (caretLine != null) {
            int caretDelta = caretLine.indexOf("^");
            assertTrue(caretDelta != -1);
            caretLine = caretLine.substring(0, caretDelta) + caretLine.substring(caretDelta + 1);
            int lineOffset = text.indexOf(caretLine);
            assertTrue(lineOffset != -1);

            caretOffset = lineOffset + caretDelta;
        }

        RubyHintsProvider provider = new RubyHintsProvider();

        // Create a hint registry which contains ONLY our hint (so other registered
        // hints don't interfere with the test)
        Map<Integer, List<AstRule>> testHints = new HashMap<Integer, List<AstRule>>();
        for (int nodeId : hint.getKinds()) {
            testHints.put(nodeId, Collections.singletonList(hint));
        }
        List<ErrorDescription> result = new ArrayList<ErrorDescription>();
        if (hint instanceof AbstractHint && ((AbstractHint)hint).getSeverity() == AbstractHint.HintSeverity.CURRENT_LINE_WARNING) {
            provider.setTestingHints(null, testHints);
            provider.computeSuggestions(info, result, caretOffset);
        } else {
            provider.setTestingHints(testHints, null);
            provider.computeHints(info, result);
        }

        return new ComputedHints(info, result, caretOffset);
    }

    protected void findHints(NbTestCase test, AstRule hint, String relFilePath, String caretLine) throws Exception {
        ComputedHints r = getHints(test, hint, relFilePath, caretLine);
        CompilationInfo info = r.info;
        List<ErrorDescription> result = r.hints;
        int caretOffset = r.caretOffset;
        
        String annotatedSource = annotate((BaseDocument)info.getDocument(), result, caretOffset);

        File goldenFile = new File(test.getDataDir(), relFilePath + "." + getName() + ".hints");
        if (!goldenFile.exists()) {
            if (!goldenFile.createNewFile()) {
                NbTestCase.fail("Cannot create file " + goldenFile);
            }
            FileWriter fw = new FileWriter(goldenFile);
            try {
                fw.write(annotatedSource.toString());
            }
            finally{
                fw.close();
            }
            NbTestCase.fail("Created generated golden file " + goldenFile + "\nPlease re-run the test.");
        }

        String ruby = readFile(test, goldenFile);
        assertEquals(ruby, annotatedSource);
    }
    
    protected void applyHint(NbTestCase test, AstRule hint, String relFilePath, 
            String caretLine, String fixDesc) throws Exception {
        ComputedHints r = getHints(test, hint, relFilePath, caretLine);
        CompilationInfo info = r.info;
        List<ErrorDescription> result = r.hints;
        int caretOffset = r.caretOffset;
        
        Fix fix = findApplicableFix(r, fixDesc);
        assertNotNull(fix);
        
        fix.implement();
        
        Document doc = info.getDocument();
        String fixed = doc.getText(0, doc.getLength());

        File goldenFile = new File(test.getDataDir(), relFilePath + "." + getName() + ".fixed");
        if (!goldenFile.exists()) {
            if (!goldenFile.createNewFile()) {
                NbTestCase.fail("Cannot create file " + goldenFile);
            }
            FileWriter fw = new FileWriter(goldenFile);
            try {
                fw.write(fixed.toString());
            }
            finally{
                fw.close();
            }
            NbTestCase.fail("Created generated golden file " + goldenFile + "\nPlease re-run the test.");
        }

        String expected = readFile(test, goldenFile);
        assertEquals(expected, fixed);
    }
    
    public void ensureRegistered(AstRule hint) throws Exception {
        Map<Integer, List<AstRule>> hints = RulesManager.getInstance().getHints();
        Set<Integer> kinds = hint.getKinds();
        for (int nodeType : kinds) {
            List<AstRule> rules = hints.get(nodeType);
            assertNotNull(rules);
            boolean found = false;
            for (AstRule rule : rules) {
                if (rule instanceof BlockVarReuse) {
                    found  = true;
                    break;
                }
            }
            
            assertTrue(found);
        }
    }

    private Fix findApplicableFix(ComputedHints r, String text) {
        int caretOffset = r.caretOffset;
        for (ErrorDescription desc : r.hints) {
            int start = desc.getRange().getBegin().getOffset();
            int end = desc.getRange().getEnd().getOffset();
            OffsetRange range = new OffsetRange(start, end);
            if (range.containsInclusive(caretOffset)) {
                // Optionally make sure the text is the one we're after such that
                // tests can disambiguate among multiple fixes
                LazyFixList list = desc.getFixes();
                assertNotNull(list);
                for (Fix fix : list.getFixes()) {
                    if (text == null ||
                            fix.getText().indexOf(text) != -1) {
                        return fix;
                    }
                }
            }
        }
        
        return null;
    }
    
    private class ComputedHints {
        ComputedHints(CompilationInfo info, List<ErrorDescription> hints, int caretOffset) {
            this.info = info;
            this.hints = hints;
            this.caretOffset = caretOffset;
        }

        CompilationInfo info;
        List<ErrorDescription> hints;
        int caretOffset;
    }
}
