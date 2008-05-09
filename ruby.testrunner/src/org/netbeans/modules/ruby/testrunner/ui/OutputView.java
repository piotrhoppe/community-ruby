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
 * 
 * Contributor(s):
 * 
 * Portions Copyrighted 2007 Sun Microsystems, Inc.
 */

package org.netbeans.modules.ruby.testrunner.ui;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.util.Collections;
import java.util.Map;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainView;
import javax.swing.text.Segment;
import javax.swing.text.Utilities;
import org.netbeans.modules.ruby.testrunner.ui.OutputDocument.DocElement;

/**
 * 
 * @author  Marian Petras
 * @author  Tim Boudreau
 */
final class OutputView extends PlainView {

    private final Segment SEGMENT = new Segment(); 
    private final OutputDocument.RootElement rootElement;

    private int selStart, selEnd;
    private static Color selectedErr;
    private static Color unselectedErr;
    private static Map hintsMap = null;

    private Color selectedFg, unselectedFg;

    /* set antialiasing hints when it's requested */
    private static final boolean antialias
            = Boolean.getBoolean("swing.aatext")                        //NOI18N
              || "Aqua".equals(UIManager.getLookAndFeel().getID());     //NOI18N

    static {
        selectedErr = UIManager.getColor("nb.output.err.foreground.selected");  //NOI18N
        if (selectedErr == null) {
            selectedErr = new Color(164, 0, 0);
        }
        unselectedErr = UIManager.getColor("nb.output.err.foreground"); //NOI18N
        if (unselectedErr == null) {
            unselectedErr = selectedErr;
        }
    }

    @SuppressWarnings("unchecked")
    static final Map getHints() {
        if (hintsMap == null) {
            //Thanks to Phil Race for making this possible
            hintsMap = (Map) Toolkit.getDefaultToolkit().getDesktopProperty(
                                         "awt.font.desktophints");      //NOI18N
            if (hintsMap == null) {
                hintsMap = antialias
                           ? Collections.singletonMap(
                                    RenderingHints.KEY_TEXT_ANTIALIASING,
                                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                           : Collections.emptyMap();
            }
        }
        return hintsMap;
    }

    OutputView(Element element) {
        super(element);
        rootElement = (OutputDocument.RootElement) element;
    }

    @Override
    public void paint(Graphics g, Shape a) {
        ((Graphics2D) g).addRenderingHints(getHints());

	final JTextComponent textComp = (JTextComponent) getContainer();
        selStart = textComp.getSelectionStart();
        selEnd = textComp.getSelectionEnd();
        unselectedFg = textComp.isEnabled()
                       ? textComp.getForeground()
                       : textComp.getDisabledTextColor();
        selectedFg = textComp.getCaret().isSelectionVisible()
                     ? textComp.getSelectedTextColor()
                     : unselectedFg;
        super.paint(g, a);
    }

    @Override
    protected void drawLine(int lineIndex, Graphics g, int x, int y) {
        DocElement docElem = rootElement.getDocElement(lineIndex);
	try {
            drawLine(docElem, g, x, y);
        } catch (BadLocationException e) {
            throw new IllegalStateException("cannot draw line " + lineIndex);   //NOI18N
        }
    }
   
    private void drawLine(DocElement elem, Graphics g, int x, int y) throws BadLocationException {
	final int p0 = elem.getStartOffset();
        final int p1 = elem.getEndOffset();
        final boolean isError = elem.isError;

        if ((selStart == selEnd) || (selectedFg == unselectedFg)) {
            /* no selection or invisible selection */
            x = drawText(g, x, y, p0, p1, isError, false, elem);
        } else if ((p0 >= selStart && p0 <= selEnd)
                && (p1 >= selStart && p1 <= selEnd)) {
            /* whole line selected */
            x = drawText(g, x, y, p0, p1, isError, true,  elem);
        } else if (selStart >= p0 && selStart <= p1) {
            if (selEnd >= p0 && selEnd <= p1) {
                x = drawText(g, x, y, p0,       selStart, isError, false, elem);
                x = drawText(g, x, y, selStart, selEnd,   isError, true,  elem);
                x = drawText(g, x, y, selEnd,   p1,       isError, false, elem);
            } else {
                x = drawText(g, x, y, p0,       selStart, isError, false, elem);
                x = drawText(g, x, y, selStart, p1,       isError, true,  elem);
            }
        } else if (selEnd >= p0 && selEnd <= p1) {
            x = drawText(g, x, y, p0,     selEnd, isError, true,  elem);
            x = drawText(g, x, y, selEnd, p1,     isError, false, elem);
        } else {
            x = drawText(g, x, y, p0,     p1,     isError, false, elem);
        }
    }

    private int drawText(Graphics g,
                         int x, int y,
                         int startOffset, int endOffset,
                         boolean error,
                         boolean selected,
                         DocElement docElem) throws BadLocationException {
        Segment s = EventQueue.isDispatchThread() ? SEGMENT : new Segment(); 
        s.array = docElem.getChars();
        s.offset = startOffset - docElem.offset;
        s.count = endOffset - startOffset;

        g.setColor(getColor(error, selected));

        return Utilities.drawTabbedText(s, x, y, g, this, startOffset);
    }
    
    private Color getColor(boolean error, boolean selected) {
        return error ? (selected ? selectedErr
                                 : unselectedErr)
                     : (selected ? selectedFg
                                 : unselectedFg);
    }

}