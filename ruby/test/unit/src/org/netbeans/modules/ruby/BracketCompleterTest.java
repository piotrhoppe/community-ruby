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

package org.netbeans.modules.ruby;

import java.util.List;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import org.netbeans.api.gsf.CompilationInfo;
import org.netbeans.api.gsf.FormattingPreferences;
import org.netbeans.api.gsf.OffsetRange;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.ruby.lexer.LexUtilities;
import org.openide.filesystems.FileObject;

/**
 * @todo Test that if you insert x="" and then DELETE the ", it wipes out BOTH of them!
 * @todo Try typing in whole source files and other than tracking missing end and } closure
 *   statements the buffer should be identical - both in terms of quotes to the rhs not having
 *   accumulated as well as indentation being correct.
 * @todo
 *   // TODO: Test
 *   // - backspace deletion
 *   // - entering incomplete output
 *   // automatic reindentation of "end", "else" etc.
 *
 * 
 * 
 * @author Tor Norbye
 */
public class BracketCompleterTest extends RubyTestBase {
    
    public BracketCompleterTest(String testName) {
        super(testName);
    }

    private void match(String original) throws BadLocationException {
        BracketCompleter bc = new BracketCompleter();
        
        int caretPos = original.indexOf('^');
        
        original = original.substring(0, caretPos) + original.substring(caretPos+1);
        int matchingCaretPos = original.indexOf('^');
        assert caretPos < matchingCaretPos;
        original = original.substring(0, matchingCaretPos) + original.substring(matchingCaretPos+1);

        BaseDocument doc = getDocument(original);

        OffsetRange range = bc.findMatching(doc, caretPos);
        
        assertNotSame("Didn't find matching token for " + LexUtilities.getToken(doc, caretPos).text().toString(), 
                OffsetRange.NONE, range);
        assertEquals("forward match not found; found '" +
                doc.getText(range.getStart(), range.getLength()) + "' instead of " +
                LexUtilities.getToken(doc, matchingCaretPos).text().toString(), 
                matchingCaretPos, range.getStart());
        
        // Perform reverse match
        range = bc.findMatching(doc, matchingCaretPos);
        
        assertNotSame(OffsetRange.NONE, range);
        assertEquals("reverse match not found; found '" +
                doc.getText(range.getStart(), range.getLength()) + "' instead of " + 
                LexUtilities.getToken(doc, caretPos).text().toString(), 
                caretPos, range.getStart());
    }
    
    private void insertBreak(String original, String expected) throws BadLocationException {
        BracketCompleter bc = new BracketCompleter();
        
        int insertOffset = original.indexOf('^');
        int finalCaretPos = expected.indexOf('^');
        original = original.substring(0, insertOffset) + original.substring(insertOffset+1);
        expected = expected.substring(0, finalCaretPos) + expected.substring(finalCaretPos+1);

        BaseDocument doc = getDocument(original);

        JTextArea ta = new JTextArea(doc);
        Caret caret = ta.getCaret();
        caret.setDot(insertOffset);
        int newOffset = bc.beforeBreak(doc, insertOffset, ta);
        doc.atomicLock();
        DocumentUtilities.setTypingModification(doc, true);

        try {
            doc.insertString(caret.getDot(), "\n", null);
            // Indent the new line
            Formatter formatter = new Formatter();
            FormattingPreferences preferences = new IndentPrefs(2,2);
            //ParserResult result = parse(fo);

            int startPos = caret.getDot()+1;
            int endPos = startPos+1;

            //ParserResult result = parse(fo);
            formatter.reindent(doc, startPos, endPos, null, preferences);
            int indent = LexUtilities.getLineIndent(doc, insertOffset+1);

            //bc.afterBreak(doc, insertOffset, caret);
            String formatted = doc.getText(0, doc.getLength());
            assertEquals(expected, formatted);
            if (newOffset != -1) {
                caret.setDot(newOffset);
            } else {
                caret.setDot(insertOffset+1+indent);
            }
            if (finalCaretPos != -1) {
                assertEquals(finalCaretPos, caret.getDot());
            }
        } finally {
            DocumentUtilities.setTypingModification(doc, false);
            doc.atomicUnlock();
        }
    }

    private void insertChar(String original, char insertText, String expected) throws BadLocationException {
        insertChar(original, insertText, expected, null);
    }

    private void insertChar(String original, char insertText, String expected, String selection) throws BadLocationException {
        int insertOffset = original.indexOf('^');
        int finalCaretPos = expected.indexOf('^');
        original = original.substring(0, insertOffset) + original.substring(insertOffset+1);
        expected = expected.substring(0, finalCaretPos) + expected.substring(finalCaretPos+1);

        BracketCompleter bc = new BracketCompleter();

        BaseDocument doc = getDocument(original);

        JTextArea ta = new JTextArea(doc);
        Caret caret = ta.getCaret();
        caret.setDot(insertOffset);
        if (selection != null) {
            int start = original.indexOf(selection);
            assertTrue(start != -1);
            assertTrue("Ambiguous selection - multiple occurrences of selection string",
                    original.indexOf(selection, start+1) == -1);
            ta.setSelectionStart(start);
            ta.setSelectionEnd(start+selection.length());
            assertEquals(selection, ta.getSelectedText());
        }

        doc.atomicLock();
        DocumentUtilities.setTypingModification(doc, true);

        try {
            boolean handled = bc.beforeCharInserted(doc, insertOffset, ta, insertText);
            if (!handled) {
                if (ta.getSelectedText() != null && ta.getSelectedText().length() > 0) {
                    insertOffset = ta.getSelectionStart();
                    doc.remove(ta.getSelectionStart(), ta.getSelectionEnd()-ta.getSelectionStart());
                    caret.setDot(insertOffset);
                }
                doc.insertString(caret.getDot(), ""+insertText, null);
                caret.setDot(insertOffset+1);
                bc.afterCharInserted(doc, insertOffset, ta, insertText);
            }
            String formatted = doc.getText(0, doc.getLength());
            assertEquals(expected, formatted);
            if (finalCaretPos != -1) {
                assertEquals(finalCaretPos, caret.getDot());
            }
        } finally {
            DocumentUtilities.setTypingModification(doc, false);
            doc.atomicUnlock();
        }
    }

    private void deleteChar(String original, String expected) throws BadLocationException {
        int afterRemoveOffset = original.indexOf('^');
        int finalCaretPos = expected.indexOf('^');
        original = original.substring(0, afterRemoveOffset) + original.substring(afterRemoveOffset+1);
        expected = expected.substring(0, finalCaretPos) + expected.substring(finalCaretPos+1);

        BracketCompleter bc = new BracketCompleter();

        BaseDocument doc = getDocument(original);

        JTextArea ta = new JTextArea(doc);
        Caret caret = ta.getCaret();
        caret.setDot(afterRemoveOffset);
        int dot = afterRemoveOffset;
        char ch = doc.getChars(dot-1, 1)[0];

        doc.atomicLock();
        DocumentUtilities.setTypingModification(doc, true);

        try {
            doc.remove(dot - 1, 1);
            caret.setDot(dot-1);
            boolean handled = bc.charBackspaced(doc, dot-1, ta, ch);
            String formatted = doc.getText(0, doc.getLength());
            assertEquals(expected, formatted);
            if (finalCaretPos != -1) {
                assertEquals(finalCaretPos, caret.getDot());
            }
        } finally {
            DocumentUtilities.setTypingModification(doc, false);
            doc.atomicUnlock();
        }
    }
    
    private void deleteWord(String original, String expected) throws BadLocationException {
        // Try deleting the word not just using the testcase but also surrounded by strings
        // to make sure there's no problem with lexer token directions
        deleteWordImpl(original, expected);
        deleteWordImpl(original+"foo", expected+"foo");
        deleteWordImpl("foo"+original, "foo"+expected);
        deleteWordImpl(original+"::", expected+"::");
        deleteWordImpl(original+"::", expected+"::");
    }
    
    private void deleteWordImpl(String original, String expected) throws BadLocationException {
        int afterRemoveOffset = original.indexOf('^');
        int finalCaretPos = expected.indexOf('^');
        original = original.substring(0, afterRemoveOffset) + original.substring(afterRemoveOffset+1);
        expected = expected.substring(0, finalCaretPos) + expected.substring(finalCaretPos+1);

        BracketCompleter bc = new BracketCompleter();

        BaseDocument doc = getDocument(original);

        JTextArea ta = new JTextArea(doc);
        Caret caret = ta.getCaret();
        caret.setDot(afterRemoveOffset);
        int dot = afterRemoveOffset;
        //REMOVE char ch = doc.getChars(dot-1, 1)[0];

        int begin = bc.getNextWordOffset(doc, dot, true);
        if (begin == -1) {
            begin = Utilities.getPreviousWord(ta, dot);
        }
        
        doc.atomicLock();
        DocumentUtilities.setTypingModification(doc, true);

        try {
            doc.remove(begin, dot-begin);
            caret.setDot(begin);
            String formatted = doc.getText(0, doc.getLength());
            assertEquals(expected, formatted);
            if (finalCaretPos != -1) {
                assertEquals(finalCaretPos, caret.getDot());
            }
        } finally {
            DocumentUtilities.setTypingModification(doc, false);
            doc.atomicUnlock();
        }
    }
    
    private void assertLogicalRange(String source, boolean up, String expected) throws Exception {
        String BEGIN = "%<%"; // NOI18N
        String END = "%>%"; // NOI18N
        int sourceStartPos = source.indexOf(BEGIN);
        if (sourceStartPos != -1) {
            source = source.substring(0, sourceStartPos) + source.substring(sourceStartPos+BEGIN.length());
        }
        
        int caretPos = source.indexOf('^');
        source = source.substring(0, caretPos) + source.substring(caretPos+1);

        int sourceEndPos = source.indexOf(END);
        if (sourceEndPos != -1) {
            source = source.substring(0, sourceEndPos) + source.substring(sourceEndPos+END.length());
        }
        
        int expectedStartPos = expected.indexOf(BEGIN);
        if (expectedStartPos != -1) {
            expected = expected.substring(0, expectedStartPos) + expected.substring(expectedStartPos+BEGIN.length());
        }

        int expectedCaretPos = expected.indexOf('^');
        expected = expected.substring(0, expectedCaretPos) + expected.substring(expectedCaretPos+1);
        
        int expectedEndPos = expected.indexOf(END);
        if (expectedEndPos != -1) {
            expected = expected.substring(0, expectedEndPos) + expected.substring(expectedEndPos+END.length());
        }

        assertEquals("Only range markers should differ", source,expected);

        OffsetRange selected = null;
        
        BaseDocument doc = getDocument(source);
        FileObject fileObject = null;
        CompilationInfo info = new TestCompilationInfo(this, fileObject, doc, source);
        
        BracketCompleter completer = new BracketCompleter();
        List<OffsetRange> ranges = completer.findLogicalRanges(info, caretPos);
        OffsetRange expectedRange;
        if (expectedStartPos != -1) {
            expectedRange = new OffsetRange(expectedStartPos, expectedEndPos);
        } else {
            expectedRange = new OffsetRange(expectedCaretPos, expectedCaretPos);
        }

        if (sourceStartPos != -1) {
            assert sourceEndPos != -1;
            selected = new OffsetRange(sourceStartPos, sourceEndPos);            

            for (int i = 0; i < ranges.size(); i++) {
                if (ranges.get(i).equals(selected)) {
                    if (up) {
                        assertTrue(i < ranges.size()-1);
                        OffsetRange was = ranges.get(i+1);
                        assertEquals("Wrong selection: expected \"" + 
                                expected.substring(expectedRange.getStart(),expectedRange.getEnd()) + "\" and was \"" +
                                source.substring(was.getStart(), was.getEnd()) + "\"",
                                expectedRange, was);
                        return;
                    } else {
                        if (i == 0) {
                            assertEquals(caretPos, expectedCaretPos);
                            return;
                        }
                        OffsetRange was = ranges.get(i-1);
                        assertEquals("Wrong selection: expected \"" + 
                                expected.substring(expectedRange.getStart(),expectedRange.getEnd()) + "\" and was \"" +
                                source.substring(was.getStart(), was.getEnd()) + "\"",
                                expectedRange, was);
                        return;
                    }
                }
            }
            fail("Selection range " + selected + " is not in the range");
        } else {
            assert ranges.size() > 0;
            OffsetRange was = ranges.get(0);
            assertEquals("Wrong selection: expected \"" + 
                    expected.substring(expectedRange.getStart(),expectedRange.getEnd()) + "\" and was \"" +
                    source.substring(was.getStart(), was.getEnd()) + "\"",
                    expectedRange, was);
            return;
        }
    }

    public void testInsertX() throws Exception {
        insertChar("c^ass", 'l', "cl^ass");
    }

    public void testInsertX2() throws Exception {
        insertChar("clas^", 's', "class^");
    }

    public void testNoMatchInComments() throws Exception {
        insertChar("# Hello^", '\'', "# Hello'^");
        insertChar("# Hello^", '"', "# Hello\"^");
        insertChar("# Hello^", '[', "# Hello[^");
        insertChar("# Hello^", '(', "# Hello(^");
    }

    public void testNoMatchInStrings() throws Exception {
        insertChar("x = \"^\"", '\'', "x = \"'^\"");
        insertChar("x = \"^\"", '[', "x = \"[^\"");
        insertChar("x = \"^\"", '(', "x = \"(^\"");
        insertChar("x = \"^)\"", ')', "x = \")^)\"");
        insertChar("x = '^'", '"', "x = '\"^'");
        insertChar("x = \"\nf^\n\"", '\'', "x = \"\nf'^\n\"");
        insertChar("x = \"\nf^\n\"", '[', "x = \"\nf[^\n\"");
        insertChar("x = \"\nf^\n\"", '(', "x = \"\nf(^\n\"");
        insertChar("x = '\nf^\n'", '"', "x = '\nf\"^\n'");
    }
    
    public void testSingleQuotes1() throws Exception {
        insertChar("x = ^", '\'', "x = '^'");
    }

    public void testSingleQuotes2() throws Exception {
        insertChar("x = '^'", '\'', "x = ''^");
    }

    public void testSingleQuotes3() throws Exception {
        insertChar("x = '^'", 'a', "x = 'a^'");
    }

    public void testSingleQuotes4() throws Exception {
        insertChar("x = '\\^'", '\'', "x = '\\'^'");
    }

    public void testDoubleQuotes1() throws Exception {
        insertChar("x = ^", '"', "x = \"^\"");
    }

    public void testDoubleQuotes2() throws Exception {
        insertChar("x = \"^\"", '"', "x = \"\"^");
    }

    public void testDoubleQuotes3() throws Exception {
        insertChar("x = \"^\"", 'a', "x = \"a^\"");
    }

    public void testDobuleQuotes4() throws Exception {
        insertChar("x = \"\\^\"", '"', "x = \"\\\"^\"");
    }

    public void testDocs() throws Exception {
        insertBreak("=begin^\n", "=begin\n^\n=end\n");
    }

    public void testDocsEnd() throws Exception {
        insertBreak("=begin^", "=begin\n^\n=end");
    }

    public void testDocsEnd2() throws Exception {
        insertBreak("def foo\nend\n=begin^", "def foo\nend\n=begin\n^\n=end");
    }

    public void testInsertEnd1() throws Exception {
        insertBreak("x^", "x\n^");
    }

    public void testInsertEnd2() throws Exception {
        insertBreak("class Foo^", "class Foo\n  ^\nend");
    }
    
    public void testInsertEnd3() throws Exception {
        insertBreak("class Foo^\nend", "class Foo\n  ^\nend");
    }

    public void testInsertIf1() throws Exception {
        insertBreak("    if true^", "    if true\n      ^\n    end");
    }

    // This doesn't work
//    public void testInsertIf2() throws Exception {
//        insertBreak("    if true\n    else", 20, "    if true\n    else\n      end", 27);
//    }

    public void testBrackets1() throws Exception {
        insertChar("x = ^", '[', "x = [^]");
    }

    public void testBrackets2() throws Exception {
        insertChar("x = [^]", ']', "x = []^");
    }

    public void testBracketsSpecialName() throws Exception {
        // "[]" and "[]=" are valid method names!
        insertChar("def ^", '[', "def [^]");
    }

    public void testBracketsSpecialName2() throws Exception {
        // "[]" and "[]=" are valid method names!
        insertChar("def [^]", ']', "def []^");
    }
    
    public void testBrackets3() throws Exception {
        insertChar("x = [^]", 'a', "x = [a^]");
    }

    public void testBrackets4() throws Exception {
        insertChar("x = [^]", '[', "x = [[^]]");
    }

    public void testBrackets5() throws Exception {
        insertChar("x = [[^]]", ']', "x = [[]^]");
    }

    public void testBrackets6() throws Exception {
        insertChar("x = [[]^]", ']', "x = [[]]^");
    }

    public void testParens1() throws Exception {
        insertChar("x = ^", '(', "x = (^)");
    }

    public void testParens2() throws Exception {
        insertChar("x = (^)", ')', "x = ()^");
    }

    public void testParens3() throws Exception {
        insertChar("x = (^)", 'a', "x = (a^)");
    }

    public void testParens4() throws Exception {
        insertChar("x = (^)", '(', "x = ((^))");
    }

    public void testParens5() throws Exception {
        insertChar("x = ((^))", ')', "x = (()^)");
    }

    public void testParens6() throws Exception {
        insertChar("x = (()^)", ')', "x = (())^");
    }

    public void testRegexp1() throws Exception {
        insertChar("x = ^", '/', "x = /^/");
    }

    public void testRegexp2() throws Exception {
        insertChar("x = /^/", '/', "x = //^");
    }

    public void testRegexp3() throws Exception {
        insertChar("x = /^/", 'a', "x = /a^/");
    }
    
    public void testRegexp4() throws Exception {
        insertChar("x = /\\^/", '/', "x = /\\/^/");
    }

    public void testRegexp5() throws Exception {
        insertChar("    regexp = /fofo^\n      # Subsequently, you can make calls to it by name with <tt>yield</tt> in", '/',
                "    regexp = /fofo/^\n      # Subsequently, you can make calls to it by name with <tt>yield</tt> in");
    }

    public void testRegexp6() throws Exception {
        insertChar("    regexp = /fofo^\n", '/',
                "    regexp = /fofo/^\n");
    }

    public void testRegexp7() throws Exception {
        insertChar("x = ^\n", '/', "x = /^/\n");
    }

    public void testRegexp8() throws Exception {
        insertChar("x = /^/\n", '/', "x = //^\n");
    }

    public void testRegexp9() throws Exception {
        insertChar("x = /^/\n", 'a', "x = /a^/\n");
    }
    
    public void testRegexp10() throws Exception {
        insertChar("x = /\\^/\n", '/', "x = /\\/^/\n");
    }
    
    public void testRegexp11() throws Exception {
        insertChar("/foo^", '/',
                "/foo/^");
    }

    public void testNotRegexp1() throws Exception {
        insertChar("x = 10 ^", '/', "x = 10 /^");
    }

    public void testNotRegexp2() throws Exception {
        insertChar("x = 3.14 ^", '/', "x = 3.14 /^");
    }

    // This test doesn't work; the lexer identifies x = y / as the
    // beginning of a regular expression. Without the space it DOES
    // work (see regexp4)
    //public void testNotRegexp3() throws Exception {
    //    insertChar("x = y ^", '/', "x = y /^");
    //}

    public void testNotRegexp4() throws Exception {
        insertChar("x = y^", '/', "x = y/^");
    }

    public void testRegexpPercent1() throws Exception {
        insertChar("x = %r^", '(', "x = %r(^)");
    }

    public void testRegexpPercent2() throws Exception {
        insertChar("x = %r(^)", ')', "x = %r()^");
    }
    
    
    public void testSinglePercent1() throws Exception {
        insertChar("x = %q^", '(', "x = %q(^)");
    }

    public void testSinglePercent2() throws Exception {
        insertChar("x = %q(^)", ')', "x = %q()^");
    }
    
    // Broken!!
    // I've gotta handle proper parenthesis nesting here... e.g.
    // %q(())
//    public void testSinglePercent3() throws Exception {
//        insertChar("x = %q(^)", '(', "x = %q((^))");
//    }

    // Broken!!
//    public void testSinglePercent4() throws Exception {
//        insertChar("x = %q((^))", ')', "x = %q(()^)");
//    }

    public void testSinglePercent5() throws Exception {
        insertChar("x = %q((^))", 'a', "x = %q((a^))");
    }
    
    public void testSinglePercent6() throws Exception {
        insertChar("x = %q^", '-', "x = %q-^-");
    }

    public void testSinglePercent7() throws Exception {
        insertChar("x = %q-^-", '-', "x = %q--^");
    }
    
    public void testSinglePercent8() throws Exception {
        insertChar("x = %q^", ' ', "x = %q ^ ");
    }

    // Broken!
//    public void testSinglePercent9() throws Exception {
//        insertChar("x = %q ^ ", ' ', "x = %q  ^");
//    }
    
    public void testSinglePercent10() throws Exception {
        insertChar("x = %q ^ ", 'x', "x = %q x^ ");
    }

    public void testSinglePercent11() throws Exception {
        insertChar("x = %q-\\^-", '-', "x = %q-\\-^-");
    }

    public void testHeredoc1() throws Exception {
        insertBreak("x=<<FOO^\n", "x=<<FOO\n^\nFOO\n");
    }

    public void testHeredoc2() throws Exception {
        insertBreak("x=f(<<FOO,^\n", "x=f(<<FOO,\n^\nFOO\n");
    }
    
    public void testFindMatching1() throws Exception {
        match("^if true\n^end");
    }
    
    public void testFindMatching2() throws Exception {
        match("x=^(true^)\ny=5");
    }
    
    public void testFindMatching3() throws Exception {
        match("x=^(true || (false)^)\ny=5");
    }

    public void testFindMatching4() throws Exception {
        match("^def foo\nif true\nend\n^end\nend");
    }

    public void testFindMatching5() throws Exception {
        // Test heredocs
        match("x=f(^<<ABC,\"hello\")\nfoo\nbar\n^ABC\n");
    }
    
    public void testBackspace1() throws Exception {
        deleteChar("x^", "^");
    }

    public void testBackspace2() throws Exception {
        deleteChar("x^y", "^y");
    }
    
    public void testBackspace3() throws Exception {
        deleteChar("xy^z", "x^z");
    }
    
    public void testBackspace4() throws Exception {
        deleteChar("xy^z", "x^z");
    }

    public void testBackspace5() throws Exception {
        deleteChar("x=\"^\"", "x=^");
    }
    
    public void testBackspace6() throws Exception {
        deleteChar("x='^'", "x=^");
    }
    
    public void testBackspace7() throws Exception {
        deleteChar("x=(^)", "x=^");
    }

    public void testBackspace7b() throws Exception {
        deleteChar("x=[^]", "x=^");
    }

    public void testBackspace8() throws Exception {
        // See bug 111534
        deleteChar("x={^}", "x=^");
    }
    
    public void testBackspace9() throws Exception {
        deleteChar("x=/^/", "x=^");
    }
    

    public void testPercentBackspace() throws Exception {
        deleteChar("x=\"#{^}\"", "x=\"#^\"");
    }

    public void testPercentBackspace2() throws Exception {
        deleteChar("x=\"#{a^}\"", "x=\"#{^}\"");
    }

    public void testPercentBackspace3() throws Exception {
        deleteChar("x=\"a#{^}b\"", "x=\"a#^b\"");
    }
    
    public void testPercentBackspace4() throws Exception {
        deleteChar("x=/#{^}/", "x=/#^/");
    }

    public void testPercentBackspace5() throws Exception {
        deleteChar("x=/#{a^}/", "x=/#{^}/");
    }

    public void testPercentBackspace6() throws Exception {
        deleteChar("x=/a#{^}b/", "x=/a#^b/");
    }
    
    public void testContComment() throws Exception {
        if (BracketCompleter.CONTINUE_COMMENTS) {
            insertBreak("# ^", "# \n# ^");
        } else {
            insertBreak("# ^", "# \n^");
        }
    }
    
    public void testContComment2() throws Exception {
        // No auto-# on new lines
        if (BracketCompleter.CONTINUE_COMMENTS) {
            insertBreak("   #  ^", "   #  \n   #  ^");
        } else {
            insertBreak("   #  ^", "   #  \n   ^");
        }
    }
    
    public void testContComment3() throws Exception {
        // No auto-# on new lines
        if (BracketCompleter.CONTINUE_COMMENTS) {
            insertBreak("   #\t^", "   #\t\n   #\t^");
        } else {
            insertBreak("   #\t^", "   #\t\n   ^");
        }
    }

    public void testContComment4() throws Exception {
        insertBreak("# foo\n^", "# foo\n\n^");
    }

    public void testContComment5() throws Exception {
        // No auto-# on new lines
        if (BracketCompleter.CONTINUE_COMMENTS) {
            insertBreak("      # ^", "      # \n      # ^");
        } else {
            insertBreak("      # ^", "      # \n      ^");
        }
    }
    
    public void testContComment6() throws Exception {
        insertBreak("   # foo^bar", "   # foo\n   # ^bar");
    }
    
    public void testContComment7() throws Exception {
        insertBreak("   # foo^\n   # bar", "   # foo\n   # ^\n   # bar");
    }
    
    public void testContComment8() throws Exception {
        insertBreak("   # foo^bar", "   # foo\n   # ^bar");
    }

    public void testNoContComment() throws Exception {
        // No auto-# on new lines
        insertBreak("foo # ^", "foo # \n^");
    }

    public void testDeleteContComment() throws Exception {
        deleteChar("# ^", "^");
        deleteChar("\n# ^", "\n^");
    }
    
    public void testDeleteContComment2() throws Exception {
        deleteChar("# ^  ", "^  ");
        deleteChar("\n# ^  ", "\n^  ");
    }
    
    public void testNoDeleteContComment() throws Exception {
        deleteChar("#  ^", "# ^");
        deleteChar("#^", "^");
        deleteChar("puts '# ^'", "puts '#^'");
    }

    public void testFindMatching6() throws Exception {
        // Test heredocs
        match("x=f(^<<ABC,'hello',<<-DEF,'bye')\nfoo\nbar\n^ABC\nbaz\n  DEF\nwhatever");
    }

    public void testFindMatching7() throws Exception {
        // Test heredocs
        match("x=f(<<ABC,'hello',^<<-DEF,'bye')\nfoo\nbar\nABC\nbaz\n  ^DEF\nwhatever");
    }

//    public void testFreakOutEditor1() throws Exception {
//        String before = "x = method_call(50, <<TOKEN1, \"arg3\", <<TOKEN2, /startofregexp/^\nThis is part of the string\nTOKEN1\nrestofregexp/)";
//        String  after = "x = method_call(50, <<TOKEN1, \"arg3\", <<TOKEN2, /startofregexp^\nThis is part of the string\nTOKEN1\nrestofregexp/)";
//        deleteChar(before, after);
//    }
//    


    public void testInsertPercentInString() throws Exception {
        insertChar("x = \"foo ^\"", '#', "x = \"foo #{^}\"");
    }

    public void testInsertPercentInString2() throws Exception {
        // Make sure type-through works
        insertChar("x = \"foo #{^}\"", '}', "x = \"foo #{}^\"");
    }

    public void testInsertPercentInString3() throws Exception {
        insertChar("x = \"foo #{^}\"", '{', "x = \"foo #{^}\"");
    }

    public void testInsertPercentInString4() throws Exception {
        insertChar("x = \"foo #{^a}\"", '}', "x = \"foo #{}^a}\"");
    }

    public void testInsertPercentInString5() throws Exception {
        insertChar("x = \"foo {^}\"", '}', "x = \"foo {}^}\"");
    }

    public void testInsertPercentInString6() throws Exception {
        insertChar("x = \"foo {^}\"", '{', "x = \"foo {{^}\"");
    }

    public void testNoInsertPercentInString() throws Exception {
        insertChar("x = 'foo ^'", '#', "x = 'foo #^'");
    }

    public void testNoInsertPercentElsewhere() throws Exception {
        insertChar("x = ^", '#', "x = #^");
    }
    
    public void testInsertPercentInRegexp() throws Exception {
        insertChar("x = /foo ^/", '#', "x = /foo #{^}/");
    }

    public void testInsertPercentInRegexp2() throws Exception {
        // Make sure type-through works
        insertChar("x = /foo #{^}/", '}', "x = /foo #{}^/");
    }

    public void testInsertPercentInRegexp3() throws Exception {
        insertChar("x = /foo #{^}/", '{', "x = /foo #{^}/");
    }

    public void testInsertPercentInRegexp4() throws Exception {
        insertChar("x = /foo #{^a}/", '}', "x = /foo #{}^a}/");
    }

    public void testInsertPercentInRegexp5() throws Exception {
        insertChar("x = /foo {^}/", '}', "x = /foo {}^}/");
    }

    public void testInsertPercentInRegexp6() throws Exception {
        insertChar("x = /foo {^}/", '{', "x = /foo {{^}/");
    }

    public void testReplaceSelection1() throws Exception {
        insertChar("x = foo^", 'y', "x = y^", "foo");
    }

    public void testReplaceSelection2() throws Exception {
        insertChar("x = foo^", '"', "x = \"foo\"^", "foo");
    }

    public void testReplaceSelection3() throws Exception {
        insertChar("x = \"foo^bar\"", '#', "x = \"#{foo}^bar\"", "foo");
    }
    
    public void testReplaceSelection4() throws Exception {
        insertChar("x = 'foo^bar'", '#', "x = '#^bar'", "foo");
    }
    
    public void testReplaceCommentSelectionBold() throws Exception {
        insertChar("# foo^", '*', "# *foo*^", "foo");
    }

    public void testReplaceCommentSelectionTerminal() throws Exception {
        insertChar("# foo^", '+', "# +foo+^", "foo");
    }

    public void testReplaceCommentSelectionItalic() throws Exception {
        insertChar("# foo^", '_', "# _foo_^", "foo");
    }

    public void testReplaceCommentSelectionWords() throws Exception {
        // No replacement if it contains multiple lines
        insertChar("# foo bar^", '*', "# *^", "foo bar");
    }

    public void testReplaceCommentOther() throws Exception {
        // No replacement if it's not one of the three chars
        insertChar("# foo^", 'x', "# x^", "foo");
    }
    
    public void testdeleteWord() throws Exception {
        deleteWord("foo_bar_baz^", "foo_bar_^");
    }

    public void testdeleteWord111303() throws Exception {
        deleteWord("foo::bar^", "foo::^");
        deleteWord("Foo::Bar^", "Foo::^");
        deleteWord("Foo::Bar_Baz^", "Foo::Bar_^");
    }
    public void testdeleteWordx111305() throws Exception {
        deleteWord("foo_bar^", "foo_^");
        deleteWord("x.foo_bar^.y", "x.foo_^.y");
    }

    public void testdeleteWord2() throws Exception {
        deleteWord("foo_bar_baz ^", "foo_bar_baz^");
        deleteWord("foo_bar_^", "foo_^");
    }

    public void testdeleteWord3() throws Exception {
        deleteWord("FooBarBaz^", "FooBar^");
    }
    
    public void testDeleteWord4_110998() throws Exception {
        deleteWord("Blah::Set^Foo", "Blah::^Foo");
    }

    public void testdeleteWord5() throws Exception {
        deleteWord("foo_bar_^", "foo_^");
    }

    public void testdeleteWords() throws Exception {
        deleteWord("foo bar^", "foo ^");
    }


    public void testDeleteWord4_110998c() throws Exception {
        String before = "  snark^\n";
        String after = "  ^\n";
        deleteWord(before, after);
    }
    
    public void testDeleteWord4_110998b() throws Exception {
        String before = "" +
"  snark(%w(a b c))\n" +
"  snark(%W(a b c))\n" +
"  snark^\n" +
"  snark(%Q(a b c))\n" +
"  snark(%w(a b c))\n";
        String after = "" +
"  snark(%w(a b c))\n" +
"  snark(%W(a b c))\n" +
"  ^\n" +
"  snark(%Q(a b c))\n" +
"  snark(%w(a b c))\n";
        deleteWord(before, after);
    }
    
    public void testBackwardsDeletion() throws Exception {
        String s = "Foo::Bar = whatever('hello')  \n  nextline";
        BracketCompleter bc = new BracketCompleter();
        for (int i = s.length(); i >= 1; i--) {
            String shortened = s.substring(0, i);
            BaseDocument doc = getDocument(shortened);

            JTextArea ta = new JTextArea(doc);
            Caret caret = ta.getCaret();
            int dot = i;
            caret.setDot(dot);
            int begin = bc.getNextWordOffset(doc, dot, true);
            if (begin == -1) {
                begin = Utilities.getPreviousWord(ta, dot);
            }
            
            assert begin != -1 && begin < i;
        }
    }
    
    public void test108889() throws Exception {
        // Reproduce 108889: AIOOBE and AE during editing
        // NOTE: While the test currently throws an exception, when the 
        // exception is fixed the test won't actually pass; that's an expected
        // fail I will deal with later
        insertChar("x = %q((^))", 'a', "x = %q((a^))");
    }
    
    
    public void test110332() throws Exception {
        String before = "args = {\n" +
            "      :name => args[:name],\n" +
            "      :status => :missing,\n" +
            "      :s2_test_comments => comments, \n" +
            "      :metric => '', \n" +
            "      :duration => '', \n" +
            "      :setback? => true,\n" +
            "      :progress? => false, :compare_metric => 0, ^:compare_duration => 0}\n" +
            "    OpenStruct.new\n" +
                            "";
        String after = "args = {\n" +
            "      :name => args[:name],\n" +
            "      :status => :missing,\n" +
            "      :s2_test_comments => comments, \n" +
            "      :metric => '', \n" +
            "      :duration => '', \n" +
            "      :setback? => true,\n" +
            "      :progress? => false, :compare_metric => 0, \n      ^:compare_duration => 0}\n" +
            "    OpenStruct.new\n" +
                            "";
        insertBreak(before, after);
    }
    
    public void test110332b() throws Exception {
        String before = "args = {\n" +
            "      :name => args[:name],\n" +
            "      :status => :missing,\n" +
            "      :s2_test_comments => comments, \n" +
            "      :metric => '', \n" +
            "      :duration => '', \n" +
            "      :setback? => true,\n" +
            "      :progress? => false, :compare_metric => 0,^ :compare_duration => 0}\n" +
            "    OpenStruct.new\n" +
                            "";
        String after = "args = {\n" +
            "      :name => args[:name],\n" +
            "      :status => :missing,\n" +
            "      :s2_test_comments => comments, \n" +
            "      :metric => '', \n" +
            "      :duration => '', \n" +
            "      :setback? => true,\n" +
            "      :progress? => false, :compare_metric => 0,\n      ^:compare_duration => 0}\n" +
            "    OpenStruct.new\n" +
                            "";
        insertBreak(before, after);
    }
    
    public void testLogicalRange1() throws Exception {
        String code = "if (true)\n  fo^o\nend";
        String next = "if (true)\n  %<%fo^o%>%\nend";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRange2() throws Exception {
        String code = "if (true)\n  %<%fo^o%>%\nend";
        String next = "%<%if (true)\n  fo^o\nend%>%";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRange3() throws Exception {
        String code = "def foo\nif (true)\n  %<%fo^o%>%\nend\nend";
        String next = "def foo\n%<%if (true)\n  fo^o\nend%>%\nend";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRange4() throws Exception {
        String code = "class Foo\ndef foo\nif (true)\n  %<%fo^o%>%\nend\nend\nend";
        String next = "class Foo\ndef foo\n%<%if (true)\n  fo^o\nend%>%\nend\nend";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRange5() throws Exception {
        String code = "class Foo\ndef foo\n%<%if (true)\n  fo^o\nend%>%\nend\nend";
        String next = "class Foo\n%<%def foo\nif (true)\n  fo^o\nend\nend%>%\nend";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRange6() throws Exception {
        String code = "class Foo\n%<%def fo^o\nif (true)\n  foo\nend\nend%>%\nend";
        String next = "%<%class Foo\ndef fo^o\nif (true)\n  foo\nend\nend\nend%>%";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRangeComment1() throws Exception {
        String code = "foo\n  # Foo Bar\n  # Foo^y Baary\n  # Bye\ndef foo\nend\n";
        String next = "foo\n  # Foo Bar\n  %<%# Foo^y Baary%>%\n  # Bye\ndef foo\nend\n";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRangeComment2() throws Exception {
        String code = "foo\n  # Foo Bar\n  %<%# Foo^y Baary%>%\n  # Bye\ndef foo\nend\n";
        String next = "foo\n  %<%# Foo Bar\n  # Foo^y Baary\n  # Bye%>%\ndef foo\nend\n";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRangeComment3() throws Exception {
        String code = "foo\n  # Foo Bar\n\n  %<%# Foo^y Baary%>%\n  # Bye\ndef foo\nend\n";
        String next = "foo\n  # Foo Bar\n\n  %<%# Foo^y Baary\n  # Bye%>%\ndef foo\nend\n";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }
    public void testLogicalRangeComment5() throws Exception {
        String code = "foo\n  foo # Foo Bar\n  %<%# Foo^y Baary%>%\n  # Bye\ndef foo\nend\n";
        String next = "foo\n  foo # Foo Bar\n  %<%# Foo^y Baary\n  # Bye%>%\ndef foo\nend\n";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }
    
    public void testLogicalRangeStrings1() throws Exception {
        String code = "x = 'foo b^ar baz', y = \"whatever\"";
        String next = "x = %<%'foo b^ar baz'%>%, y = \"whatever\"";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRangeStrings2() throws Exception {
        String code = "x = %q-foo b^ar baz-, y = \"whatever\"";
        String next = "x = %<%%q-foo b^ar baz-%>%, y = \"whatever\"";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRangeStrings3() throws Exception {
        String code = "def foo\nif (true)\nx = %<%'foo b^ar baz'%>%\nend\nend";
        String next = "def foo\nif (true)\n%<%x = 'foo b^ar baz'%>%\nend\nend";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRangeStrings4() throws Exception {
        String code = "def foo\nif (true)\n%<%x = 'foo b^ar baz'%>%\nend\nend";
        String next = "def foo\n%<%if (true)\nx = 'foo b^ar baz'\nend%>%\nend";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRangeStrings5() throws Exception {
        String code = "def test\n 'return^ me'\nend";
        String next = "def test\n %<%'return^ me'%>%\nend";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRangeStrings6() throws Exception {
        String code = "def test\n %<%'return^ me'%>%\nend";
        String next = "%<%def test\n 'return^ me'\nend%>%";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }

    public void testLogicalRangeRegexps1() throws Exception {
        String code = "x = /foo b^ar baz/, y = \"whatever\"";
        // Uhm - is it good that we're selecting the -inside- of the regexp?
        String next = "x = /%<%foo b^ar baz%>%/, y = \"whatever\"";
        assertLogicalRange(code, true, next);
        assertLogicalRange(next, false, code);
    }
    
    public void testPipes1() throws Exception {
        insertChar("5.each { ^", '|', "5.each { |^|");
    }

    public void testPipes2() throws Exception {
        insertChar("5.each { ^}", '|', "5.each { |^|}");
    }
    
    public void testPipes3() throws Exception {
        insertChar("5.each { |^|}", '|', "5.each { ||^}");
    }
    
    public void testPipes4() throws Exception {
        insertChar("5.each { |foo^|}", '|', "5.each { |foo|^}");
    }

    public void testNegativePipes1() throws Exception {
        insertChar("'^'", '|', "'|^'");
    }

    public void testNegativePipes2() throws Exception {
        insertChar("/^/", '|', "/|^/");
    }

    public void testNegativePipes3() throws Exception {
        insertChar("#^", '|', "#|^");
    }

    public void testNegativePipes4() throws Exception {
        insertChar("\"^\"", '|', "\"|^\"");
    }

    public void testNegativePipes5() throws Exception {
        insertChar("5.each { |f^oo|}", '|', "5.each { |f|^oo|}");
    }

    public void testNegativePipes6() throws Exception {
        insertChar("5.each { |^|foo|}", '|', "5.each { ||^foo|}");
    }

    public void testNegativePipes7() throws Exception {
        insertChar("x = true ^", '|', "x = true |^");
    }

    public void testNegativePipes8() throws Exception {
        insertChar("x = true |^", '|', "x = true ||^");
    }

    public void testBackspacePipes() throws Exception {
        deleteChar("x=|^|", "x=^");
    }

    public void testBackspacePipes2() throws Exception {
        deleteChar("x=|^x", "x=^x");
    }
    
    public void testBackspacePipes3() throws Exception {
        deleteChar("x=|^", "x=^");
    }
}
