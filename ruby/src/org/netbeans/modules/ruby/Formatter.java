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
package org.netbeans.modules.ruby;

import java.util.ArrayList;
import java.util.List;

import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;

import org.netbeans.api.gsf.FormattingPreferences;
import org.netbeans.api.gsf.GsfTokenId;
import org.netbeans.api.gsf.OffsetRange;
import org.netbeans.api.gsf.ParserResult;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.ruby.lexer.LexUtilities;
import org.netbeans.modules.ruby.lexer.RubyTokenId;
import org.netbeans.modules.ruby.options.CodeStyle;
import org.openide.util.Exceptions;


/**
 * Formatting and indentation for Ruby.
 * 
 * @todo Handle RHTML! 
 *      - 4 space indents
 *      - conflicts with HTML formatter (HtmlIndentTask)
 *      - The Ruby indentation should be indented into the HTML level as well!
 *          (so I should look at the previous HTML level for my initial context
 *           whenever the balance is 0.)
 * @todo Use configuration object to pass in Ruby conventions
 * @todo Use the provided parse tree, if any, to for example check heredoc nodes
 *   and see if they are indentable.
 * @todo If you select a complete line, the endOffset is on a new line; adjust it back
 * @todo If line ends with \ I definitely have a line continuation!
 * @todo Use the Context.modifyIndent() method to change line indents instead of
 *   the current document/formatter method
 * @todo This line screws up formatting:
 *        alias __class__ class #:nodoc:
 * @todo Why doesn't this format correctly?
 * <pre>
class Module
  alias_method :class?, :===
end
 * </pre>
 *
 * @author Tor Norbye
 */
public class Formatter implements org.netbeans.api.gsf.Formatter {
    private boolean isRhtmlDocument;
    private CodeStyle codeStyle;
    private int rightMarginOverride = -1;

    public Formatter() {
        this.codeStyle = CodeStyle.getDefault(null);
    }
    
    public Formatter(CodeStyle codeStyle, int rightMarginOverride) {
        assert codeStyle != null;
        this.codeStyle = codeStyle;
        this.rightMarginOverride = rightMarginOverride;
    }
    
    // Compatibility only - remove soon
    public void reformat(Document document, ParserResult result, FormattingPreferences preferences,
        Caret caret) {
        reformat(document, 0, document.getLength(), result, preferences);
    }

    public void reformat(Document document, int startOffset, int endOffset, ParserResult result,
        FormattingPreferences preferences) {

        reindent(document, startOffset, endOffset, result, preferences, false);
    }
    
    public int indentSize() {
        return codeStyle.getIndentSize();
    }
    
    public int hangingIndentSize() {
        return codeStyle.getContinuationIndentSize();
    }

    /** Compute the initial balance of brackets at the given offset. */
    private int getFormatStableStart(BaseDocument doc, int offset) {
        TokenSequence<?extends GsfTokenId> ts = LexUtilities.getRubyTokenSequence(doc, offset);
        if (ts == null) {
            return 0;
        }

        ts.move(offset);

        if (!ts.movePrevious()) {
            return 0;
        }

        // Look backwards to find a suitable context - a class, module or method definition
        // which we will assume is properly indented and balanced
        do {
            Token<?extends GsfTokenId> token = ts.token();
            TokenId id = token.id();

            if (id == RubyTokenId.CLASS || id == RubyTokenId.MODULE || id == RubyTokenId.DEF) {
                return ts.offset();
            }
        } while (ts.movePrevious());

        return ts.offset();
    }
    
    private int getTokenBalanceDelta(TokenId id, Token<? extends GsfTokenId> token,
            BaseDocument doc, TokenSequence<? extends GsfTokenId> ts, boolean includeKeywords) {
        if (id == RubyTokenId.LPAREN || id == RubyTokenId.LBRACKET || id == RubyTokenId.LBRACE ||
                // In some cases, the [ shows up as an identifier, for example in this expression:
                //  for k, v in sort{|a1, a2| a1[0].id2name <=> a2[0].id2name}
                (id == RubyTokenId.IDENTIFIER && (token.text().toString().equals("[")))) { // NOI18N
            return 1;
        } else if (id == RubyTokenId.RPAREN || id == RubyTokenId.RBRACKET || id == RubyTokenId.RBRACE) {
            return -1;
        } else if (includeKeywords) {
            if (LexUtilities.isBeginToken(id, doc, ts)) {
                return 1;
            } else if (id == RubyTokenId.END) {
                return -1;
            }
        }

        return 0;
    }
    
    // TODO RHTML - there can be many discontiguous sections, I've gotta process all of them on the given line
    private int getTokenBalance(BaseDocument doc, int begin, int end, boolean includeKeywords) {
        int balance = 0;

        if (isRhtmlDocument) {
            TokenHierarchy<Document> th = TokenHierarchy.get((Document)doc);
            // Probably an RHTML file - gotta process it in sections since I can have lines
            // made up of both whitespace, ruby, html and delimiters and all ruby sections
            // can affect the token balance
            TokenSequence<? extends TokenId> t = th.tokenSequence();
            if (t == null) {
                return 0;
            }
            t.move(begin);
            if (!t.moveNext()) {
                return 0;
            }
            
            do {
                Token<?extends TokenId> token = t.token();
                TokenId id = token.id();
                
                if (id.primaryCategory().equals("ruby")) { // NOI18N
                    TokenSequence<? extends GsfTokenId> ts = t.embedded(RubyTokenId.language());
                    ts.move(begin);
                    ts.moveNext();
                    do {
                        Token<?extends GsfTokenId> rubyToken = ts.token();
                        if (rubyToken == null) {
                            break;
                        }
                        TokenId rubyId = rubyToken.id();

                        balance += getTokenBalanceDelta(rubyId, rubyToken, doc, ts, includeKeywords);
                    } while (ts.moveNext() && (ts.offset() < end));
                }

            } while (t.moveNext() && (t.offset() < end));
        } else {
            TokenSequence<?extends GsfTokenId> ts = LexUtilities.getRubyTokenSequence(doc, begin);
            if (ts == null) {
                return 0;
            }
            
            ts.move(begin);

            if (!ts.moveNext()) {
                return 0;
            }

            do {
                Token<?extends GsfTokenId> token = ts.token();
                TokenId id = token.id();
                
                balance += getTokenBalanceDelta(id, token, doc, ts, includeKeywords);
            } while (ts.moveNext() && (ts.offset() < end));
        }

        return balance;
    }

    private boolean isInLiteral(BaseDocument doc, int offset) throws BadLocationException {
        // TODO: Handle arrays better
        // %w(January February March April May June July
        //    August September October November December)
        // I should indent to the same level

        // Can't reformat these at the moment because reindenting a line
        // that is a continued string array causes incremental lexing errors
        // (which further screw up formatting)
        int pos = Utilities.getRowFirstNonWhite(doc, offset);
        //int pos = offset;

        if (pos != -1) {
            // I can't look at the first position on the line, since
            // for a string array that is indented, the indentation portion
            // is recorded as a blank identifier
            Token<?extends GsfTokenId> token = LexUtilities.getToken(doc, pos);

            if (token != null) {
                TokenId id = token.id();
                // If we're in a string literal (or regexp or documentation) leave
                // indentation alone!
                if ((id == RubyTokenId.STRING_LITERAL) ||
                        id == RubyTokenId.DOCUMENTATION ||
                        (id == RubyTokenId.QUOTED_STRING_LITERAL) ||
                        (id == RubyTokenId.REGEXP_LITERAL)) {
                    // No indentation for literal strings in Ruby, since they can
                    // contain newlines. Leave it as is.
                    return true;
                }
                
                if (id == RubyTokenId.STRING_END || id == RubyTokenId.QUOTED_STRING_END) {
                    // Possibly a heredoc
                    TokenSequence<? extends GsfTokenId> ts = LexUtilities.getRubyTokenSequence(doc, pos);
                    ts.move(pos);
                    OffsetRange range = LexUtilities.findHeredocBegin(ts, token);
                    if (range != OffsetRange.NONE) {
                        String text = doc.getText(range.getStart(), range.getLength());
                        if (text.startsWith("<<-")) { // NOI18N
                            return false;
                        } else {
                            return true;
                        }
                    }
                }
            } else {
                // No ruby token -- leave the formatting alone!
                // (Probably in an RHTML file on a line with no Ruby)
                return true;
            }
        } else {
            // Empty line inside a string, documentation etc. literal?
            Token<?extends GsfTokenId> token = LexUtilities.getToken(doc, offset);

            if (token != null) {
                TokenId id = token.id();
                // If we're in a string literal (or regexp or documentation) leave
                // indentation alone!
                if ((id == RubyTokenId.STRING_LITERAL) ||
                        id == RubyTokenId.DOCUMENTATION ||
                        (id == RubyTokenId.QUOTED_STRING_LITERAL) ||
                        (id == RubyTokenId.REGEXP_LITERAL)) {
                    // No indentation for literal strings in Ruby, since they can
                    // contain newlines. Leave it as is.
                    return true;
                }
            }
        }

        return false;
    }
    
    /** 
     * Get the first token on the given line. Similar to LexUtilities.getToken(doc, lineBegin)
     * except (a) it computes the line begin from the offset itself, and more importantly,
     * (b) it handles RHTML tokens specially; e.g. if a line begins with
     * {@code
     *    <% if %>
     * }
     * then the "if" embedded token will be returned rather than the RHTML delimiter, or even
     * the whitespace token (which is the first Ruby token in the embedded sequence).
     *    
     * </pre>   
     */
    private Token<? extends GsfTokenId> getFirstToken(BaseDocument doc, int offset) throws BadLocationException {
        int lineBegin = Utilities.getRowFirstNonWhite(doc, offset);

        if (lineBegin != -1) {
            if (isRhtmlDocument) {
                TokenSequence<? extends GsfTokenId> ts = LexUtilities.getRubyTokenSequence(doc, lineBegin);
                if (ts != null) {
                    ts.moveNext();
                    Token<?extends GsfTokenId> token = ts.token();
                    while (token != null && token.id() == RubyTokenId.WHITESPACE) {
                        if (!ts.moveNext()) {
                            return null;
                        }
                        token = ts.token();
                    }
                    return token;
                }
            } else {
                return LexUtilities.getToken(doc, lineBegin);
            }
        }
        
        return null;
    }

    private boolean isEndIndent(BaseDocument doc, int offset) throws BadLocationException {
        int lineBegin = Utilities.getRowFirstNonWhite(doc, offset);

        if (lineBegin != -1) {
            Token<?extends GsfTokenId> token = getFirstToken(doc, offset);
            
            if (token == null) {
                return false;
            }
            
            TokenId id = token.id();

            // If the line starts with an end-marker, such as "end", "}", "]", etc.,
            // find the corresponding opening marker, and indent the line to the same
            // offset as the beginning of that line.
            return (LexUtilities.isIndentToken(id) && !LexUtilities.isBeginToken(id, doc, offset)) || id == RubyTokenId.END ||
                id == RubyTokenId.RBRACE || id == RubyTokenId.RBRACKET || id == RubyTokenId.RPAREN;
        }
        
        return false;
    }
    
    private boolean isLineContinued(BaseDocument doc, int offset, int bracketBalance) throws BadLocationException {
        // TODO RHTML - this isn't going to work for rhtml embedded strings...
        offset = Utilities.getRowLastNonWhite(doc, offset);
        if (offset == -1) {
            return false;
        }

        
        TokenSequence<?extends GsfTokenId> ts = LexUtilities.getRubyTokenSequence(doc, offset);

        if (ts == null) {
            return false;
        }
        ts.move(offset);

        if (!ts.moveNext() && !ts.movePrevious()) {
            return false;
        }

        Token<?extends GsfTokenId> token = ts.token();

        if (token != null) {
            TokenId id = token.id();
            
            // http://www.netbeans.org/issues/show_bug.cgi?id=115279
            boolean isContinuationOperator = (id == RubyTokenId.NONUNARY_OP || id == RubyTokenId.DOT);
            
            if (ts.offset() == offset && token.length() > 1 && token.text().toString().startsWith("\\")) {
                // Continued lines have different token types
                isContinuationOperator = true;
            }
            
            if (token.length() == 1 && id == RubyTokenId.IDENTIFIER && token.text().toString().equals(",")) {
                // If there's a comma it's a continuation operator, but inside arrays, hashes or parentheses
                // parameter lists we should not treat it as such since we'd "double indent" the items, and
                // NOT the first item (where there's no comma, e.g. you'd have
                //  foo(
                //    firstarg,
                //      secondarg,  # indented both by ( and hanging indent ,
                //      thirdarg)
                if (bracketBalance == 0) {
                    isContinuationOperator = true;
                }
            }
            
            if (isContinuationOperator) {
                // Make sure it's not a case like this:
                //    alias eql? ==
                // or
                //    def ==
                token = LexUtilities.getToken(doc, Utilities.getRowFirstNonWhite(doc, offset));
                if (token != null) {
                    id = token.id();
                    if (id == RubyTokenId.DEF || id == RubyTokenId.ANY_KEYWORD && token.text().toString().equals("alias")) { // NOI18N
                        return false;
                    }
                }

                return true;
            } else if (id == RubyTokenId.ANY_KEYWORD) {
                String text = token.text().toString();
                if ("or".equals(text) || "and".equals(text)) { // NOI18N
                    return true;
                }
            }
        }

        return false;
    }

    /** @todo Rewrite to handleposition inside adef to be way off.
     * This needs to be working to pass in handing indents as well!!!
     *  I need to 
     */
    public void reindent(Document document, int startOffset, int endOffset, ParserResult result,
        FormattingPreferences preferences) {
        reindent(document, startOffset, endOffset, result, preferences, true);
    }

    private void reindent(Document document, int startOffset, int endOffset, ParserResult result,
        FormattingPreferences preferences, boolean indentOnly) {
        isRhtmlDocument = RubyUtils.isRhtmlDocument(document);

        try {
            BaseDocument doc = (BaseDocument)document; // document.getText(0, document.getLength())

            syncOptions(doc, codeStyle);

            if (endOffset > doc.getLength()) {
                endOffset = doc.getLength();
            }
            
            startOffset = Utilities.getRowStart(doc, startOffset);
            int lineStart = startOffset;//Utilities.getRowStart(doc, startOffset);
            int initialOffset = 0;
            int initialIndent = 0;
            if (startOffset > 0) {
                int prevOffset = Utilities.getRowStart(doc, startOffset-1);
                initialOffset = getFormatStableStart(doc, prevOffset);
                initialIndent = LexUtilities.getLineIndent(doc, initialOffset);
            }
            
            // Build up a set of offsets and indents for lines where I know I need
            // to adjust the offset. I will then go back over the document and adjust
            // lines that are different from the intended indent. By doing piecemeal
            // replacements in the document rather than replacing the whole thing,
            // a lot of things will work better: breakpoints and other line annotations
            // will be left in place, semantic coloring info will not be temporarily
            // damaged, and the caret will stay roughly where it belongs.
            List<Integer> offsets = new ArrayList<Integer>();
            List<Integer> indents = new ArrayList<Integer>();

            // When we're formatting sections, include whitespace on empty lines; this
            // is used during live code template insertions for example. However, when
            // wholesale formatting a whole document, leave these lines alone.
            boolean indentEmptyLines = (startOffset != 0 || endOffset != doc.getLength());

            boolean includeEnd = endOffset == doc.getLength() || indentOnly;
            
            // TODO - remove initialbalance etc.
            computeIndents(doc, initialIndent, initialOffset, endOffset, result, 
                    offsets, indents, indentEmptyLines, includeEnd, indentOnly);
            
            try {
                doc.atomicLock();

                // Iterate in reverse order such that offsets are not affected by our edits
                assert indents.size() == offsets.size();
                org.netbeans.editor.Formatter editorFormatter = doc.getFormatter();
                for (int i = indents.size() - 1; i >= 0; i--) {
                    int indent = indents.get(i);
                    int lineBegin = offsets.get(i);
                    
                    if (lineBegin < lineStart) {
                        // We're now outside the region that the user wanted reformatting;
                        // these offsets were computed to get the correct continuation context etc.
                        // for the formatter
                        break;
                    }
                    
                    if (lineBegin == lineStart && i > 0) {
                        // Look at the previous line, and see how it's indented
                        // in the buffer.  If it differs from the computed position,
                        // offset my computed position (thus, I'm only going to adjust
                        // the new line position relative to the existing editing.
                        // This avoids the situation where you're inserting a newline
                        // in the middle of "incorrectly" indented code (e.g. different
                        // size than the IDE is using) and the newline position ending
                        // up "out of sync"
                        int prevOffset = offsets.get(i-1);
                        int prevIndent = indents.get(i-1);
                        int actualPrevIndent = LexUtilities.getLineIndent(doc, prevOffset);
                        if (actualPrevIndent != prevIndent) {
                            // For blank lines, indentation may be 0, so don't adjust in that case
                            if (!(Utilities.isRowEmpty(doc, prevOffset) || Utilities.isRowWhite(doc, prevOffset))) {
                                indent = actualPrevIndent + (indent-prevIndent);
                            }
                        }
                    }

                    // Adjust the indent at the given line (specified by offset) to the given indent
                    int currentIndent = LexUtilities.getLineIndent(doc, lineBegin);

                    if (currentIndent != indent) {
                        editorFormatter.changeRowIndent(doc, lineBegin, indent);
                    }
                }
                
                if (!indentOnly && codeStyle.reformatComments()) {
                    reformatComments(doc, startOffset, endOffset);
                }
            } finally {
                doc.atomicUnlock();
            }
        } catch (BadLocationException ble) {
            Exceptions.printStackTrace(ble);
        }
    }

    public void computeIndents(BaseDocument doc, int initialIndent, int startOffset, int endOffset, ParserResult result,
            List<Integer> offsets,
            List<Integer> indents,
            boolean indentEmptyLines, boolean includeEnd, boolean indentOnly
        ) {
        // PENDING:
        // The reformatting APIs in NetBeans should be lexer based. They are still
        // based on the old TokenID apis. Once we get a lexer version, convert this over.
        // I just need -something- in place until that is provided.

        try {
            // Algorithm:
            // Iterate over the range.
            // Accumulate a token balance ( {,(,[, and keywords like class, case, etc. increases the balance, 
            //      },),] and "end" decreases it
            // If the line starts with an end marker, indent the line to the level AFTER the token
            // else indent the line to the level BEFORE the token (the level being the balance * indentationSize)
            // Compute the initial balance and indentation level and use that as a "base".
            // If the previous line is not "done" (ends with a comma or a binary operator like "+" etc.
            // add a "hanging indent" modifier.
            // At the end of the day, we're recording a set of line offsets and indents.
            // This can be used either to reformat the buffer, or indent a new line.

            // State:
            int offset = Utilities.getRowStart(doc, startOffset); // The line's offset
            int end = endOffset;
            
            int indentSize = codeStyle.getIndentSize();
            int hangingIndentSize = codeStyle.getContinuationIndentSize();
            
            // Pending - apply comment formatting too?

            // XXX Look up RHTML too
            //int indentSize = EditorOptions.get(RubyInstallation.RUBY_MIME_TYPE).getSpacesPerTab();
            //int hangingIndentSize = indentSize;
            

            // Build up a set of offsets and indents for lines where I know I need
            // to adjust the offset. I will then go back over the document and adjust
            // lines that are different from the intended indent. By doing piecemeal
            // replacements in the document rather than replacing the whole thing,
            // a lot of things will work better: breakpoints and other line annotations
            // will be left in place, semantic coloring info will not be temporarily
            // damaged, and the caret will stay roughly where it belongs.

            // The token balance at the offset
            int balance = 0;
            // The bracket balance at the offset ( parens, bracket, brace )
            int bracketBalance = 0;
            boolean continued = false;
            boolean indentHtml = false;
            if (isRhtmlDocument) {
                indentHtml = codeStyle.indentHtml();
            }

            while ((!includeEnd && offset < end) || (includeEnd && offset <= end)) {
                int indent; // The indentation to be used for the current line

                int hangingIndent = continued ? (hangingIndentSize) : 0;

                if (isRhtmlDocument && !indentOnly) {
                    // Pick up the indentation level assigned by the HTML indenter; gets HTML structure
                    initialIndent = LexUtilities.getLineIndent(doc, offset);
                }
                
                if (isInLiteral(doc, offset)) {
                    // Skip this line - leave formatting as it is prior to reformatting 
                    indent = LexUtilities.getLineIndent(doc, offset);

                    if (isRhtmlDocument && indentHtml && balance > 0) {
                        indent += balance * indentSize;
                    }
                } else if (isEndIndent(doc, offset)) {
                    indent = (balance-1) * indentSize + hangingIndent + initialIndent;
                } else {
                    indent = balance * indentSize + hangingIndent + initialIndent;
                }

                if (indent < 0) {
                    indent = 0;
                }
                
                int lineBegin = Utilities.getRowFirstNonWhite(doc, offset);

                // Insert whitespace on empty lines too -- needed for abbreviations expansion
                if (lineBegin != -1 || indentEmptyLines) {
                    // Don't do a hanging indent if we're already indenting beyond the parent level?
                    
                    indents.add(Integer.valueOf(indent));
                    offsets.add(Integer.valueOf(offset));
                }

                int endOfLine = Utilities.getRowEnd(doc, offset) + 1;

                if (lineBegin != -1) {
                    balance += getTokenBalance(doc, lineBegin, endOfLine, true);
                    bracketBalance += getTokenBalance(doc, lineBegin, endOfLine, false);
                    continued = isLineContinued(doc, offset, bracketBalance);
                }

                offset = endOfLine;
            }
        } catch (BadLocationException ble) {
            Exceptions.printStackTrace(ble);
        }
    }
    
    void reformatComments(BaseDocument doc, int start, int end) {
        int rightMargin = rightMarginOverride;
        if (rightMargin == -1) {
            CodeStyle style = codeStyle;
            if (style == null) {
                style = CodeStyle.getDefault(null);
            }

            rightMargin = style.getRightMargin();
        }

        ReflowParagraphAction action = new ReflowParagraphAction();
        action.reflowComments(doc, start, end, rightMargin);
    }
    
    /**
     * Ensure that the editor-settings for tabs match our code style, since the
     * primitive "doc.getFormatter().changeRowIndent" calls will be using
     * those settings
     */
    private static void syncOptions(BaseDocument doc, CodeStyle style) {
        org.netbeans.editor.Formatter formatter = doc.getFormatter();
        if (formatter.getSpacesPerTab() != style.getIndentSize()) {
            formatter.setSpacesPerTab(style.getIndentSize());
        }
    }
}
