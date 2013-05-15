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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.jrubyparser.ast.AliasNode;
import org.jrubyparser.ast.ArgsNode;
import org.jrubyparser.ast.DAsgnNode;
import org.jrubyparser.ast.DVarNode;
import org.jrubyparser.ast.ListNode;
import org.jrubyparser.ast.LocalAsgnNode;
import org.jrubyparser.ast.LocalVarNode;
import org.jrubyparser.ast.MethodDefNode;
import org.jrubyparser.ast.Node;
import org.jrubyparser.ast.NodeType;
import org.jrubyparser.ast.INameNode;
import org.jrubyparser.ast.IterNode;
import org.netbeans.modules.csl.api.ColoringAttributes;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.api.SemanticAnalyzer;
import org.netbeans.modules.parsing.spi.Parser.Result;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.netbeans.modules.ruby.lexer.LexUtilities;

/**
 * Walk through the JRuby AST and note interesting things
 * @todo Use the org.jrubyparser.ast.visitor.NodeVisitor interface
 * @todo Do mixins and includes trip up my unused private method detection code?
 * @todo Treat toplevel methods as private?
 * @todo Show unused highlighting for unused class variables:
 *    private_class_method
 *   See section 7.8 in http://www.rubycentral.com/faq/rubyfaq-7.html
 * @todo Handle java fully packaged names by not bolding "java" and "javax" method
 *   calls in Java projects
 * @todo I can do faster tree walking with a quick integer set of node types I'm
 *   interested in, or more specifically a set of node types I know I can prune:
 *   ArgNodes etc. 
 * @todo Stash unused variables in a list I can reference from a quickfix!
 * @author Tor Norbye
 */
public class RubySemanticAnalyzer extends SemanticAnalyzer {    
    private boolean cancelled;
    private Map<OffsetRange, Set<ColoringAttributes>> semanticHighlights;
    private static final Set<String> JAVA_PREFIXES = new HashSet<String>();

    /**
     * Method names for which we should skip highlightning. See #124701 - this
     * would probably need a more general solution (Operators as method names
     * are already handled somewhere, but I really can't find where..)
     */
    private static final Set<String> SKIP_HIGHLIGHTNING = new HashSet<String>();

    static {
        JAVA_PREFIXES.add("java"); // NOI18N
        JAVA_PREFIXES.add("javax"); // NOI18N
        JAVA_PREFIXES.add("org"); // NOI18N
        JAVA_PREFIXES.add("com"); // NOI18N

        // what else than "[]"?
        SKIP_HIGHLIGHTNING.add("[]");
    }

    public RubySemanticAnalyzer() {
    }

    @Override
    public Map<OffsetRange, Set<ColoringAttributes>> getHighlights() {
        return semanticHighlights;
    }

    protected final synchronized boolean isCancelled() {
        return cancelled;
    }

    protected final synchronized void resume() {
        cancelled = false;
    }

    @Override
    public final synchronized void cancel() {
        cancelled = true;
    }

    @Override
    public int getPriority() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Class<? extends Scheduler> getSchedulerClass() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void run(Result info, SchedulerEvent event) {
        resume();

        if (isCancelled()) return;

        RubyParseResult rpr = AstUtilities.getParseResult(info);
        if (rpr == null) return;

        Node root = rpr.getRootNode();
        if (root == null) return;

        Map<OffsetRange, Set<ColoringAttributes>> highlights = new HashMap<OffsetRange, Set<ColoringAttributes>>(100);

        AstPath path = new AstPath();
        path.descend(root);
        annotate(root, highlights, path, null, false);
        path.ascend();

        if (isCancelled()) return;

        if (highlights.size() > 0) {
            Map<OffsetRange, Set<ColoringAttributes>> translated = new HashMap<OffsetRange,Set<ColoringAttributes>>(2*highlights.size());
            for (Map.Entry<OffsetRange,Set<ColoringAttributes>> entry : highlights.entrySet()) {
                OffsetRange range = LexUtilities.getLexerOffsets(info, entry.getKey());
                    
                if (range != OffsetRange.NONE) translated.put(range, entry.getValue());
            }
                
            this.semanticHighlights = translated;
        } else {
            this.semanticHighlights = null;
        }
    }

    /** Find unused local and dynamic variables */
    @SuppressWarnings("fallthrough")
    private void annotate(Node node, Map<OffsetRange,Set<ColoringAttributes>> highlights, AstPath path,
        List<String> parameters, boolean isParameter) {
        switch (node.getNodeType()) {
        case ARGSNODE: {
            isParameter = true;
            break;
        }
        case LOCALASGNNODE: {
            LocalAsgnNode lasgn = (LocalAsgnNode)node;
            Node method = AstUtilities.findLocalScope(node, path);

            if (!isUsedInMethod(method, lasgn.getName(), isParameter)) {
                highlights.put(AstUtilities.offsetRangeFor(lasgn.getLeftHandSidePosition()), ColoringAttributes.UNUSED_SET);
            } else if (parameters != null && parameters.contains(lasgn.getName())) {
                highlights.put(AstUtilities.getNameRange(node), ColoringAttributes.PARAMETER_SET);
            }
            break;
        }
        case DASGNNODE: {
            DAsgnNode dasgn = (DAsgnNode)node;
            Node method = AstUtilities.findLocalScope(node, path);

            if (!isUsedInMethod(method, dasgn.getName(), false)) {
                highlights.put(AstUtilities.offsetRangeFor(dasgn.getLeftHandSidePosition()), ColoringAttributes.UNUSED_SET);
            }
            
            break;
        }
            
        case DEFNNODE: case DEFSNODE: {
            MethodDefNode def = (MethodDefNode) node;
            // FIXME: Replacing parameters here and in iter this seems wrong (should make local list and add to parameters so nested with iters won't replace it
//            parameters = def.getArgs().getNormativeParameterNameList(true);
            List<String> defParameters = def.getArgs().getNormativeParameterNameList(true);

            if (!defParameters.isEmpty()) {
                List<String> unused = new ArrayList<String>();
                for (String parameter : defParameters) {
                    if (!def.isParameterUsed(parameter)) unused.add(parameter);
                }

                if (!unused.isEmpty()) {
                    annotateUnusedParameters(def.getArgs(), highlights, unused);
                    defParameters.removeAll(unused);
                }

                if (!defParameters.isEmpty()) {
                    annotateParameters(def.getArgs(), highlights, defParameters);
                    if (parameters == null) {
                        parameters = defParameters;
                    } else {
                        parameters.addAll(defParameters);
                    }
                }

            }
            if (!SKIP_HIGHLIGHTNING.contains(def.getName())) highlightMethodName(def, highlights);
            
// FIXME: What is interface            if (def.getBody() != null) node = def.getBody();
            break;
        }
        case ITERNODE: {
            IterNode iter = (IterNode) node;
            
            if (iter.getVar() instanceof ArgsNode) {
                ArgsNode args = (ArgsNode) iter.getVar();
//                parameters = args.getNormativeParameterNameList(true);
                List <String> iterParameters = args.getNormativeParameterNameList(true);
                
                if (!iterParameters.isEmpty()) {
                    List<String> unused = new ArrayList<String>();
                    for (String parameter : iterParameters) {
                        // FIXME: normative parameter list is returning null on completing abbrev/all? (block completion) on a string
                        if (parameters != null && !iter.isParameterUsed(parameter)) unused.add(parameter);
                    }

                    if (!unused.isEmpty()) {
                        annotateUnusedParameters(args, highlights, unused);
                        iterParameters.removeAll(unused);
                    }

                    if (!iterParameters.isEmpty()) {
                        annotateParameters(args, highlights, iterParameters);
                        if (parameters == null) {
                            parameters = iterParameters;
                        } else {
                            parameters.addAll(iterParameters);
                        }
                    }
                }
                
// FIXME: HMMM what is interface                if (iter.getBody() != null) node = iter.getBody();
            }
            break;
        }
            
        case LOCALVARNODE: {
            if (parameters != null) {
                if (parameters.contains(((LocalVarNode)node).getName())) {
                    OffsetRange range = AstUtilities.getRange(node);
                    highlights.put(range, ColoringAttributes.PARAMETER_SET);
                }
            }
            break;
        }
        
        case VCALLNODE:
            // FALLTHROUGH!
            if (JAVA_PREFIXES.contains(((INameNode)node).getName())) {
                // Skip highlighting "org" in "org.foo.Bar" etc.
                break;
            }
        //case CALLNODE: CallNode seems overly aggressive - it will show all operators for example
        case FCALLNODE: 
            // see #124701
            if (!SKIP_HIGHLIGHTNING.contains(AstUtilities.getName(node))) {
                highlights.put(AstUtilities.getCallRange(node), ColoringAttributes.METHOD_SET);
            }
            break;
        }

        for (Node child : node.childNodes()) {
            if (child.isInvisible()) continue;

            path.descend(child);
            annotate(child, highlights, path, parameters, isParameter);
            path.ascend();
        }
    }

    private void annotateParameters(ArgsNode an, Map<OffsetRange, Set<ColoringAttributes>> highlights,
            List<String> names, EnumSet<ColoringAttributes> coloringAttribute) {
        // FIXME: Make this a pre/opt/post/rest/block rewrite
        if (an.getRequiredCount() > 0) {
            for (Node arg : an.childNodes()) {
                if (arg instanceof ListNode) { // Many specific types
                    for (Node arg2 : arg.childNodes()) {
                        if ((arg2.getNodeType() == NodeType.ARGUMENTNODE || arg2.getNodeType() == NodeType.LOCALASGNNODE) &&
                                names.contains(((INameNode) arg2).getName())) {
                            highlights.put(AstUtilities.offsetRangeFor(arg2.getPosition()), coloringAttribute);
                        }
                    }
                }
            }
        }

        if (an.getRest() != null && names.contains(an.getRest().getName())) {
            highlights.put(AstUtilities.offsetRangeFor(an.getRest().getPosition()), coloringAttribute);
        }

        if (an.getBlock() != null && names.contains(an.getBlock().getName())) {
            highlights.put(AstUtilities.offsetRangeFor(an.getBlock().getPosition()), coloringAttribute);
        }
    }
    
    private void annotateParameters(ArgsNode node, Map<OffsetRange, Set<ColoringAttributes>> highlights, List<String> names) {
        annotateParameters(node, highlights, names, ColoringAttributes.PARAMETER_SET);
    }    

    private void annotateUnusedParameters(ArgsNode node, Map<OffsetRange, Set<ColoringAttributes>> highlights, List<String> names) {
        annotateParameters(node, highlights, names, ColoringAttributes.UNUSED_SET);
    }

    
    private boolean isUsedInMethod(Node node, String targetName, boolean isParameter) {
        switch (node.getNodeType()) {
            /*case ARGUMENTNODE:*/ case DVARNODE: case LOCALVARNODE:
                return targetName.equals(((INameNode) node).getName());
            case ALIASNODE:
                return targetName.equals(AstUtilities.getNameOrValue(((AliasNode) node).getOldName()));
            case ZSUPERNODE: // Super with no arguments passes arguments to parent so consider the parameters used
                return isParameter;
        }

        for (Node child : node.childNodes()) {
            if (child.isInvisible()) continue;

            // The "outer" foo here is unused - we shouldn't recurse into method bodies when doing unused detection:
            // foo = 1; def bar; foo = 2; print foo; end;
            if (child instanceof MethodDefNode) continue;
            if (isUsedInMethod(child, targetName, isParameter)) return true;
        }

        return false;
    }

    private void highlightMethodName(MethodDefNode node, Map<OffsetRange, Set<ColoringAttributes>> highlights) {
        OffsetRange range = AstUtilities.offsetRangeFor(node.getDecoratedNamePosition());
        
        // Don't block out already annotated private methods
        if (!highlights.containsKey(range)) highlights.put(range, ColoringAttributes.METHOD_SET);
    }
}