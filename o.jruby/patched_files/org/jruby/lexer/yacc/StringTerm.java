/***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.lexer.yacc;

import org.jruby.ast.RegexpNode;
import org.jruby.ast.StrNode;
import org.jruby.lexer.yacc.SyntaxException.PID;
import org.jruby.parser.ReOptions;
import org.jruby.parser.Tokens;
import org.jruby.util.ByteList;

public class StringTerm extends StrTerm {
    // Expand variables, Indentation of final marker
    private int flags;

    // Start of string ([, (, {, <, ', ", \n) 
    private final char begin;

    // End of string (], ), }, >, ', ", \0)
    private final char end;

    // How many strings are nested in the current string term
    private int nest;

    public StringTerm(int flags, int begin, int end) {
        this.flags = flags;
        this.begin = (char) begin;
        this.end   = (char) end;
        this.nest  = 0;
    }

    public int parseString(RubyYaccLexer lexer, LexerSource src) throws java.io.IOException {
        boolean spaceSeen = false;
        int c;

        // FIXME: How much more obtuse can this be?
        // Heredoc already parsed this and saved string...Do not parse..just return
        if (flags == -1) {
            lexer.setValue(new Token("\"", lexer.getPosition()));
            return Tokens.tSTRING_END;
        }

        c = src.read();
        if ((flags & RubyYaccLexer.STR_FUNC_QWORDS) != 0 && Character.isWhitespace(c)) {
            do {
                c = src.read();
            } while (Character.isWhitespace(c));
            spaceSeen = true;
        }

        // BEGIN NETBEANS MODIFICATIONS
        //if (c == end && nest == 0) {
        if ((processingEmbedded == IGNORE_EMBEDDED || processingEmbedded == LOOKING_FOR_EMBEDDED) && (c == end) && (nest == 0)) {
        // END NETBEANS MODIFICATIONS
            if ((flags & RubyYaccLexer.STR_FUNC_QWORDS) != 0) {
                // BEGIN NETBEANS MODIFICATIONS
                if (processingEmbedded == LOOKING_FOR_EMBEDDED) { // Only make this change when lexing, not parsing
                    // I want the terminating ")" to be passed as a string closure token,
                    // not as a plain rparen, since I want it to match up with the 
                    // string opening tag (and I don't want an unbalanced right paren)
                    lexer.setValue(new Token(""+end, lexer.getPosition()));
                    return Tokens.tSTRING_END;
                }
                // END NETBEANS MODIFICATIONS
                flags = -1;
                lexer.getPosition();
                return ' ';
            }
            
            if ((flags & RubyYaccLexer.STR_FUNC_REGEXP) != 0) {
                lexer.setValue(new RegexpNode(src.getPosition(), ByteList.create(""), parseRegexpFlags(src)));
                return Tokens.tREGEXP_END;
            }
            
            lexer.setValue(new Token("\"", lexer.getPosition()));
            return Tokens.tSTRING_END;
        }
        
        if (spaceSeen) {
            src.unread(c);
            lexer.getPosition();
            return ' ';
        }

        // BEGIN NETBEANS MODIFICATIONS
        if ((processingEmbedded == EMBEDDED_DEXPR) && (c == '}')) {
            processingEmbedded = LOOKING_FOR_EMBEDDED;
            lexer.setValue(new Token("}", lexer.getPosition()));
            return Tokens.tSTRING_CONTENT;
        }
        // END NETBEANS MODIFICATIONS
        
        // Single-quote fast path
// XXX CHECK THIS! May not work with #{}'s    
        if (begin == '\0' && flags == 0) {
            ByteList buffer = new ByteList();
            src.unread(c);
            if (parseSimpleStringIntoBuffer(src, buffer) == RubyYaccLexer.EOF) {
                throw new SyntaxException(PID.STRING_HITS_EOF, src.getPosition(), "unterminated string meets end of file");
            }
            
            /*
            ByteList buffer;
            src.unread(c);
            if ((buffer = src.readUntil(end)) == null) {
                throw new SyntaxException(src.getPosition(), "unterminated string meets end of file");
            }
            */
            lexer.setValue(new StrNode(lexer.getPosition(), buffer)); 
            return Tokens.tSTRING_CONTENT;
        }
        
        ByteList buffer = new ByteList();

        if ((flags & RubyYaccLexer.STR_FUNC_EXPAND) != 0 && c == '#') {
            c = src.read();
            switch (c) {
            case '$':
            case '@':
                // BEGIN NETBEANS MODIFICATIONS
                if (processingEmbedded == LOOKING_FOR_EMBEDDED) {
                    processingEmbedded = EMBEDDED_DVAR;
                }
                // END NETBEANS MODIFICATIONS
                src.unread(c);
                lexer.setValue(new Token("#" + c, lexer.getPosition()));
                return Tokens.tSTRING_DVAR;
            case '{':
                // BEGIN NETBEANS MODIFICATIONS
                if (processingEmbedded == LOOKING_FOR_EMBEDDED) {
                    processingEmbedded = EMBEDDED_DEXPR;
                }
                // END NETBEANS MODIFICATIONS
                lexer.setValue(new Token("#" + c, lexer.getPosition())); 
                return Tokens.tSTRING_DBEG;
            }
            buffer.append((byte) '#');
        }
        src.unread(c);
        // BEGIN NETBEANS MODIFICATIONS
        //if (parseStringIntoBuffer(lexer, src, buffer) == RubyYaccLexer.EOF) {
        int parsed;
        if (processingEmbedded == EMBEDDED_DEXPR) {
            parsed = parseDExprIntoBuffer(lexer, src, buffer);
        } else {
            parsed = parseStringIntoBuffer(lexer, src, buffer);
        }
        if (parsed == RubyYaccLexer.EOF) {
        // END NETBEANS MODIFICATIONS
            // BEGIN NETBEANS MODIFICATIONS
            // We've read to the end of input and haven't found a corresponding String
            // terminator. However, we don't always want to return the rest of the input as
            // erroneous; in lexing mode, we want to stop at the first newline
            // (at least or normal quoted strings, possibly not for heredocs etc.)
            // and resume parsing from there, since it's likely that we're in the middle
            // of typing a string.
            // We've gotta push the "unused portion" of the string back into the input;
            // the unused portion is the portion after the first newline.
//            int n = buffer.length();
//            for (int j = 0; j < n; j++) {
//                if (buffer.charAt(j) == '\n') {
//                    // Found it.
//                    j++; // Include at least one
//                    for (int k = n-1; k >= j; k--) {
//                        // push input back in reverse order
//                        src.unread(buffer.charAt(k));
//                    }
//                    // Fall through outer loop and throw SyntaxException
//                    break;
//                }
//            }
            //throw new SyntaxException(PID.STRING_HITS_EOF, src.getPosition(), "unterminated string meets end of file");
            throw new UnterminatedStringException(src.getPosition(), "unterminated string meets end of file");
            // END NETBEANS MODIFICATIONS
        }

        lexer.setValue(new StrNode(lexer.getPosition(), buffer)); 

        // BEGIN NETBEANS MODIFICATIONS
        // DVARs last only for a single string token so shut if off here.
        if (processingEmbedded == EMBEDDED_DVAR) {
            processingEmbedded = LOOKING_FOR_EMBEDDED;
        } else if ((processingEmbedded == EMBEDDED_DEXPR) && (buffer.length() == 0)) {
            // Unbalanced expression - see #96485
            processingEmbedded = LOOKING_FOR_EMBEDDED;
        }
        // END NETBEANS MODIFICATIONS

        return Tokens.tSTRING_CONTENT;
    }

    private int parseRegexpFlags(final LexerSource src) throws java.io.IOException {
        char kcode = 0;
        int options = 0;
        int c;
        StringBuilder unknownFlags = new StringBuilder(10);

        for (c = src.read(); c != RubyYaccLexer.EOF
                && Character.isLetter(c); c = src.read()) {
            switch (c) {
            case 'i':
                options |= ReOptions.RE_OPTION_IGNORECASE;
                break;
            case 'x':
                options |= ReOptions.RE_OPTION_EXTENDED;
                break;
            case 'm':
                options |= ReOptions.RE_OPTION_MULTILINE;
                break;
            case 'o':
                options |= ReOptions.RE_OPTION_ONCE;
                break;
            case 'n':
                kcode = 16;
                break;
            case 'e':
                kcode = 32;
                break;
            case 's':
                kcode = 48;
                break;
            case 'u':
                kcode = 64;
                break;
            case 'j':
                options |= 256; // Regexp engine 'java'
                break;
            default:
                unknownFlags.append((char) c);
                break;
            }
        }
        src.unread(c);
        if (unknownFlags.length() != 0) {
            throw new SyntaxException(PID.REGEXP_UNKNOWN_OPTION, src.getPosition(), "unknown regexp option"
                    + (unknownFlags.length() > 1 ? "s" : "") + " - "
                    + unknownFlags.toString(), unknownFlags.toString());
        }
        return options | kcode;
    }
    
    public int parseSimpleStringIntoBuffer(LexerSource src, ByteList buffer) throws java.io.IOException {
        int c;

        while ((c = src.read()) != RubyYaccLexer.EOF) {
            if (c == end) {
                src.unread(c);
                break;
            } else if (c == '\\') {
                c = src.read();
                if ((c == '\n' || c != end) && c != '\\') buffer.append('\\');
            } 

            buffer.append(c);
        }
        
        return c;
    }
    
    public int parseStringIntoBuffer(RubyYaccLexer lexer, LexerSource src, ByteList buffer) throws java.io.IOException {
        boolean qwords = (flags & RubyYaccLexer.STR_FUNC_QWORDS) != 0;
        boolean expand = (flags & RubyYaccLexer.STR_FUNC_EXPAND) != 0;
        boolean escape = (flags & RubyYaccLexer.STR_FUNC_ESCAPE) != 0;
        boolean regexp = (flags & RubyYaccLexer.STR_FUNC_REGEXP) != 0;
        int c;

        while ((c = src.read()) != RubyYaccLexer.EOF) {
            if (begin != '\0' && c == begin) {
                nest++;
            // BEGIN NETBEANS MODIFICATIONS
            } else if (processingEmbedded == EMBEDDED_DEXPR && c == '}') {
                src.unread(c);
                break;
            } else if (processingEmbedded == EMBEDDED_DVAR && !((c == '_') || c == '$' || c == '@' || Character.isLetter(c))) {
                 src.unread(c);
                 break;
            // END NETBEANS MODIFICATIONS
            } else if (c == end) {
                if (nest == 0) {
                    src.unread(c);
                    break;
                }
                nest--;
            } else if (c == '#' && expand && !src.peek('\n')) {
                int c2 = src.read();

                if (c2 == '$' || c2 == '@' || c2 == '{') {
                    src.unread(c2);
                    src.unread(c);
                    break;
                }
                src.unread(c2);
            } else if (c == '\\') {
                c = src.read();
                switch (c) {
                case '\n':
                    if (qwords) break;
                    if (expand) continue;
                    buffer.append('\\');
                    break;

                case '\\':
                    if (escape) buffer.append(c);
                    break;

                default:
                    if (regexp) {
                        src.unread(c);
                        parseEscapeIntoBuffer(src, buffer);
                        continue;
                    } else if (expand) {
                        src.unread(c);
                        if (escape) buffer.append('\\');
                        c = lexer.readEscape();
                    } else if (qwords && Character.isWhitespace(c)) {
                        /* ignore backslashed spaces in %w */
                    } else if (c != end && !(begin != '\0' && c == begin)) {
                        buffer.append('\\');
                    }
                }
            } else if (qwords && Character.isWhitespace(c)) {
                src.unread(c);
                break;
            }
            buffer.append(c);
        }
        
        return c;
    }

    // BEGIN NETBEANS MODIFICATIONS
    public int parseDExprIntoBuffer(RubyYaccLexer lexer, LexerSource src, ByteList buffer) throws java.io.IOException {
        boolean qwords = (flags & RubyYaccLexer.STR_FUNC_QWORDS) != 0;
        boolean expand = (flags & RubyYaccLexer.STR_FUNC_EXPAND) != 0;
        boolean escape = (flags & RubyYaccLexer.STR_FUNC_ESCAPE) != 0;
        boolean regexp = (flags & RubyYaccLexer.STR_FUNC_REGEXP) != 0;
        int c;

        while ((c = src.read()) != RubyYaccLexer.EOF) {
            if (c == '{') {
                nest++;
            } else if (c == '}') {
                if (nest == 0) {
                    src.unread(c);
                    break;
                }
                nest--;
//            } else if (c == end) {
//                if (nest == 0) {
//                    src.unread(c);
//                    break;
//                }
//                nest--;
            } else if (c == '\\') {
                c = src.read();
                switch (c) {
                case '\n':
                    if (qwords) break;
                    if (expand) continue;
                    buffer.append('\\');
                    break;

                case '\\':
                    if (escape) buffer.append(c);
                    break;

                default:
                    if (regexp) {
                        src.unread(c);
                        parseEscapeIntoBuffer(src, buffer);
                        continue;
                    } else if (expand) {
                        src.unread(c);
                        if (escape) buffer.append('\\');
                        c = lexer.readEscape();
                    } else if (qwords && Character.isWhitespace(c)) {
                        /* ignore backslashed spaces in %w */
                    } else if (c != end && !(begin != '\0' && c == begin)) {
                        buffer.append('\\');
                    }
                }
            } else if (qwords && Character.isWhitespace(c)) {
                src.unread(c);
                break;
            }
            buffer.append(c);
        }
        
        return c;
    }
    // END NETBEANS MODIFICATIONS
    
    
    // Was a goto in original ruby lexer
    private void escaped(LexerSource src, ByteList buffer) throws java.io.IOException {
        int c;

        switch (c = src.read()) {
        case '\\':
            parseEscapeIntoBuffer(src, buffer);
            break;
        case RubyYaccLexer.EOF:
            throw new SyntaxException(PID.INVALID_ESCAPE_SYNTAX, src.getPosition(), "Invalid escape character syntax");
        default:
            buffer.append(c);
        }
    }

    private void parseEscapeIntoBuffer(LexerSource src, ByteList buffer) throws java.io.IOException {
        int c;

        switch (c = src.read()) {
        case '\n':
            break; /* just ignore */
        case '0':
        case '1':
        case '2':
        case '3': /* octal constant */
        case '4':
        case '5':
        case '6':
        case '7':
            buffer.append('\\');
            buffer.append(c);
            for (int i = 0; i < 2; i++) {
                c = src.read();
                if (c == RubyYaccLexer.EOF) {
                    throw new SyntaxException(PID.INVALID_ESCAPE_SYNTAX, src.getPosition(), "Invalid escape character syntax");
                }
                if (!RubyYaccLexer.isOctChar(c)) {
                    src.unread(c);
                    break;
                }
                buffer.append(c);
            }
            break;
        case 'x': /* hex constant */
            buffer.append('\\');
            buffer.append(c);
            c = src.read();
            if (!RubyYaccLexer.isHexChar(c)) {
                throw new SyntaxException(PID.INVALID_ESCAPE_SYNTAX, src.getPosition(), "Invalid escape character syntax");
            }
            buffer.append(c);
            c = src.read();
            if (RubyYaccLexer.isHexChar(c)) {
                buffer.append(c);
            } else {
                src.unread(c);
            }
            break;
        case 'M':
            if ((c = src.read()) != '-') {
                throw new SyntaxException(PID.INVALID_ESCAPE_SYNTAX, src.getPosition(), "Invalid escape character syntax");
            }
            buffer.append(new byte[] { '\\', 'M', '-' });
            escaped(src, buffer);
            break;
        case 'C':
            if ((c = src.read()) != '-') {
                throw new SyntaxException(PID.INVALID_ESCAPE_SYNTAX, src.getPosition(), "Invalid escape character syntax");
            }
            buffer.append(new byte[] { '\\', 'C', '-' });
            escaped(src, buffer);
            break;
        case 'c':
            buffer.append(new byte[] { '\\', 'c' });
            escaped(src, buffer);
            break;
        case RubyYaccLexer.EOF:
            throw new SyntaxException(PID.INVALID_ESCAPE_SYNTAX, src.getPosition(), "Invalid escape character syntax");
        default:
            if (c != '\\' || c != end) {
                buffer.append('\\');
            }
            buffer.append(c);
        }
    }
    
    // BEGIN NETBEANS MODIFICATIONS
    public boolean isSubstituting() {
        return (flags & RubyYaccLexer.STR_FUNC_EXPAND) != 0;
    }

    public Object getMutableState() {
        return new MutableTermState(processingEmbedded, nest);
    }

    public void setMutableState(Object o) {
        MutableTermState state = (MutableTermState)o;
        if (state != null) {
            this.processingEmbedded = state.processingEmbedded;
            this.nest = state.nest;
        }
    }
    
    public void splitEmbeddedTokens() {
        if (processingEmbedded == IGNORE_EMBEDDED) {
            processingEmbedded = LOOKING_FOR_EMBEDDED;
        }
    }

    private class MutableTermState {
        private MutableTermState(int embeddedCode, int nest) {
            this.processingEmbedded = embeddedCode;
            this.nest = nest;
        }
        
        public boolean equals(Object obj) {
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final MutableTermState other = (MutableTermState) obj;

            if (this.nest != other.nest)
                return false;
            if (this.processingEmbedded != other.processingEmbedded)
                return false;
            return true;
        }

        public int hashCode() {
            int hash = 7;

            hash = 83 * hash + this.nest;
            hash = 83 * hash + this.processingEmbedded;
            return hash;
        }
        
        public String toString() {
            return "StringTermState[nest=" + nest + ",embed=" + processingEmbedded + "]";
        }
        
        private int nest;
        private int processingEmbedded;
    }
    
    // Equals - primarily for unit testing (incremental lexing tests
    // where we do full-file-lexing and compare state to incremental lexing)
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final StringTerm other = (StringTerm) obj;

        if (this.flags != other.flags)
            return false;
        if (this.end != other.end)
            return false;
        if (this.processingEmbedded != other.processingEmbedded)
            return false;
        if (this.begin != other.begin)
            return false;
        if (this.nest != other.nest)
            return false;
        return true;
    }
    
    private static String toFuncString(int flags) {
        StringBuilder sb = new StringBuilder();
        if ((flags & RubyYaccLexer.STR_FUNC_ESCAPE) != 0) {
            sb.append("escape|");
        }
        if ((flags & RubyYaccLexer.STR_FUNC_EXPAND) != 0) {
            sb.append("expand|");
        }
        if ((flags & RubyYaccLexer.STR_FUNC_REGEXP) != 0) {
            sb.append("regexp|");
        }
        if ((flags & RubyYaccLexer.STR_FUNC_QWORDS) != 0) {
            sb.append("qwords|");
        }
        if ((flags & RubyYaccLexer.STR_FUNC_SYMBOL) != 0) {
            sb.append("symbol|");
        }
        if ((flags & RubyYaccLexer.STR_FUNC_INDENT) != 0) {
            sb.append("indent|");
        }
        
        String s = sb.toString();
        
        if (s.endsWith("|")) {
            s = s.substring(0, s.length()-1);
        } else if (s.length() == 0) {
            s = "-";
        }

        return s;
    }

    public String toString() {
        return "StringTerm[flags=" + toFuncString(flags) + ",end=" + end + ",begin=" + (int)begin + ",nest=" + nest + ",embed=" + processingEmbedded + "]";
    }

    public int hashCode() {
        int hash = 7;

        hash = 13 * hash + this.flags;
        hash = 13 * hash + this.end;
        hash = 13 * hash + this.begin;
        hash = 13 * hash + this.nest;
        hash = 13 * hash + this.processingEmbedded;
        return hash;
    }
    
    public static class UnterminatedStringException extends SyntaxException {
        public UnterminatedStringException(ISourcePosition pos, String message) {
            // TODO - get rid of this class now that I have an id? I can switch on normal SyntaxExceptions!
            super(PID.STRING_HITS_EOF, pos, message);
        }
    }
    // END NETBEANS MODIFICATIONS
}
