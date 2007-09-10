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

import javax.swing.JTextArea;
import javax.swing.text.Caret;
import org.netbeans.editor.BaseDocument;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tor Norbye
 */
public class ReflowParagraphActionTest extends RubyTestBase {
    
    public ReflowParagraphActionTest(String testName) {
        super(testName);
    }

    private void formatParagraph(String file, String caretLine) throws Exception {
        FileObject fo = getTestFile(file);
        assertNotNull(fo);
        BaseDocument doc = getDocument(fo);
        assertNotNull(doc);
        String before = doc.getText(0, doc.getLength());

        int caretDelta = caretLine.indexOf('^');
        assertTrue(caretDelta != -1);
        caretLine = caretLine.substring(0, caretDelta) + caretLine.substring(caretDelta + 1);
        int lineOffset = before.indexOf(caretLine);
        assertTrue(lineOffset != -1);
        int caretOffset = lineOffset+caretDelta;

        
        ReflowParagraphAction action = new ReflowParagraphAction();
        JTextArea ta = new JTextArea(doc);
        Caret caret = ta.getCaret();
        caret.setDot(caretOffset);
        action.actionPerformed(ta);
        
        String after = doc.getText(0, doc.getLength());
        assertEquals(before, after);
    }

    public void testScanfFormatting() throws Exception {
        formatParagraph("testfiles/scanf.comment", "Matches an opti^onally signed decimal integer");
    }
    
    public void testHttpHeaderFormatting() throws Exception {
        formatParagraph("testfiles/http-header.comment", " under the sa^me terms of ruby");
    }

    public void testHttpFormatting() throws Exception {
        formatParagraph("testfiles/http.comment", "This lib^rary provides your program functions");
    }
    
    public void testHttpFormatting2() throws Exception {
        formatParagraph("testfiles/http.comment", "#^ Example #4: More generic GET+prin");
    }
}
