/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
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
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
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
package org.netbeans.modules.ruby.hints;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import javax.swing.text.BadLocationException;
import org.jrubyparser.ast.IfNode;
import org.jrubyparser.ast.ImplicitNilNode;
import org.jrubyparser.ast.Node;
import org.jrubyparser.ast.NodeType;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.csl.api.EditList;
import org.netbeans.modules.csl.api.Hint;
import org.netbeans.modules.csl.api.HintFix;
import org.netbeans.modules.csl.api.HintSeverity;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.api.PreviewableFix;
import org.netbeans.modules.csl.api.RuleContext;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.ruby.AstUtilities;
import org.netbeans.modules.ruby.RubyUtils;
import org.netbeans.modules.ruby.hints.infrastructure.RubyAstRule;
import org.netbeans.modules.ruby.hints.infrastructure.RubyRuleContext;
import org.netbeans.modules.ruby.lexer.LexUtilities;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

/**
 * Convert conditionals of the form "if foo; bar; end" to "bar if foo".
 * Inspired by the excellent blog entry
 *   http://langexplr.blogspot.com/2007/11/creating-netbeans-ruby-hints-with-scala_24.html
 * by Luis Diego Fallas.
 * 
 * @author Tor Norbye
 */
public class ConvertConditionals extends RubyAstRule {

    @Override
    public Set<NodeType> getKinds() {
        return Collections.singleton(NodeType.IFNODE);
    }
    
    private boolean isConvertible(RubyRuleContext context, IfNode ifNode) {
        // Can happen for this code: 'if (); end' (typically while editing)
        if (ifNode.getCondition() instanceof ImplicitNilNode) return false;

        Node body = ifNode.getThenBody();
        Node elseNode = ifNode.getElseBody();

        // Can't convert if-then-else conditionals or empty ones
        if (body != null && elseNode != null || body == null && elseNode == null) return false;

        // Can't convert if !x/elseif blocks
        if (ifNode.getElseBody() != null && ifNode.getElseBody().getNodeType() == NodeType.IFNODE) return false;
        
        int start = ifNode.getPosition().getStartOffset();
        // Can't convert blocks with multiple statements or already a statement modifier?
        if (!RubyHints.isNull(body) && (body.getNodeType() == NodeType.BLOCKNODE || body.getPosition().getStartOffset() <= start)) {
            return false;
        } else if (!RubyHints.isNull(elseNode) && (elseNode.getNodeType() == NodeType.BLOCKNODE || elseNode.getPosition().getStartOffset() <= start)) {
            return false;
        }
        
        
        try {
            int keywordOffset = ConvertIfToUnless.findKeywordOffset(context, ifNode);
            if (keywordOffset == -1 || keywordOffset > context.doc.getLength() - 1) return false;

            char k = context.doc.getText(keywordOffset, 1).charAt(0);
            if (k != 'i' && k != 'u') return false; // Probably ternary operator, ?:
        } catch (BadLocationException ble) {
            Exceptions.printStackTrace(ble);
        }
        
        return true;
    }

    @Override
    public void run(RubyRuleContext context, List<Hint> result) {
        Node node = context.node;
        ParserResult info = context.parserResult;
        BaseDocument doc = context.doc;
        
        if (!isConvertible(context, (IfNode) node)) return;

        // If statement that is not already a statement modifier
        OffsetRange range = AstUtilities.getRange(node);

        if (RubyUtils.isRhtmlDocument(doc) || RubyUtils.isYamlDocument(doc)) {
            // Make sure that we're in a single contiguous Ruby section; if not, this won't work
            range = LexUtilities.getLexerOffsets(info, range);
            if (range == OffsetRange.NONE) return;

            try {
                doc.readLock();
                TokenSequence ts = TokenHierarchy.get(doc).tokenSequence();
                ts.move(range.getStart());
                if (!ts.moveNext() && !ts.movePrevious()) return;

                if (ts.offset()+ts.token().length() < range.getEnd()) return;
            } finally {
                doc.readUnlock();
            }
        }
        
        
        ConvertToModifier fix = new ConvertToModifier(context, (IfNode) node);
        if (fix.getEditList() == null) return;

        List<HintFix> fixes = Collections.<HintFix>singletonList(fix);

        String displayName = NbBundle.getMessage(ConvertConditionals.class, "ConvertConditionals");
        Hint desc = new Hint(this, displayName, RubyUtils.getFileObject(info), range, fixes, 500);
        result.add(desc);
    }

    @Override
    public String getId() {
        return "ConvertConditionals"; // NOI18N
    }

    @Override
    public String getDescription() {
        return NbBundle.getMessage(ConvertConditionals.class, "ConvertConditionalsDesc");
    }

    @Override
    public boolean getDefaultEnabled() {
        return true;
    }

    @Override
    public JComponent getCustomizer(Preferences node) {
        return null;
    }

    @Override
    public boolean appliesTo(RuleContext context) {
        return true;
    }

    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(ConvertConditionals.class, "ConvertConditionals");
    }

    @Override
    public boolean showInTasklist() {
        return false;
    }

    @Override
    public HintSeverity getDefaultSeverity() {
        return HintSeverity.CURRENT_LINE_WARNING;
    }
    
    private class ConvertToModifier implements PreviewableFix {
        private final RubyRuleContext context;
        private IfNode ifNode;

        public ConvertToModifier(RubyRuleContext context, IfNode ifNode) {
            this.context = context;
            this.ifNode = ifNode;
        }

        @Override
        public String getDescription() {
            return NbBundle.getMessage(ConvertConditionals.class, "ConvertConditionalsFix");
        }

        @Override
        public void implement() throws Exception {
            EditList edits = getEditList();
            if (edits != null) edits.apply();
        }
        
        @Override
        public EditList getEditList() {
            try {
                BaseDocument doc = context.doc;

                Node bodyNode = ifNode.getThenBody();
                boolean isIf = bodyNode != null;
                if (!isIf) bodyNode = ifNode.getElseBody();

                ParserResult info = context.parserResult;
                OffsetRange bodyRange = AstUtilities.getRange(bodyNode);
                bodyRange = LexUtilities.getLexerOffsets(info, bodyRange);
                if (bodyRange == OffsetRange.NONE) return null;

                String body = doc.getText(bodyRange.getStart(), bodyRange.getLength()).trim();
                if (body.endsWith(";")) body = body.substring(0, body.length()-1);

                OffsetRange range = LexUtilities.getLexerOffsets(info, AstUtilities.getRange(ifNode.getCondition()));
                if (range == OffsetRange.NONE) return null;
                
                String s = body + " " + (isIf ? "if" : "unless") + " " + doc.getText(range.getStart(), range.getLength()); // NOI18N

                OffsetRange ifRange = AstUtilities.getRange(ifNode);
                ifRange = LexUtilities.getLexerOffsets(info, ifRange);
                if (ifRange == OffsetRange.NONE) return null;

                return new EditList(doc).replace(ifRange.getStart(), ifRange.getLength(), s, false, 0);
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
                return null;
            }
        }

        @Override
        public boolean isSafe() {
            return true;
        }

        @Override
        public boolean isInteractive() {
            return false;
        }

        @Override
        public boolean canPreview() {
            return true;
        }
    }
}
