package org.netbeans.modules.ruby.elements;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.jrubyparser.ast.ClassVarAsgnNode;

import org.jrubyparser.ast.ClassVarDeclNode;
import org.jrubyparser.ast.ClassVarNode;
import org.jrubyparser.ast.Node;
import org.jrubyparser.ast.INameNode;
import org.netbeans.modules.csl.api.ElementKind;
import org.netbeans.modules.csl.api.Modifier;
import org.netbeans.modules.csl.spi.ParserResult;

public class AstFieldElement extends AstElement {

    public AstFieldElement(ParserResult info, Node node) {
        super(info, node);
    }

    @Override
    public String getName() {
        if (name == null) {
            // InstVarNode, ClassDeclVarNode, ConstNode, etc.
            if (node instanceof INameNode) name = ((INameNode)node).getName();
            if (name == null) name = node.toString();

            // Chop off "@" and "@@"
            if (name.startsWith("@@")) {
                name = name.substring(2);
            } else if (name.startsWith("@")) {
                name = name.substring(1);
            }
        }

        return name;
    }

    @Override
    public Set<Modifier> getModifiers() {
        if (modifiers == null) {
            // TODO - find access level!
            if (node instanceof ClassVarNode || node instanceof ClassVarDeclNode || node instanceof ClassVarAsgnNode) {
                modifiers = EnumSet.of(Modifier.STATIC);
            } else {
                // instance variables are always private
                modifiers = Collections.singleton(Modifier.PRIVATE);
            }
        }

        return modifiers;
    }

    @Override
    public ElementKind getKind() {
        return ElementKind.FIELD;
    }
}
