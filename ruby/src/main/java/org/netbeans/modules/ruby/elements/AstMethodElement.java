package org.netbeans.modules.ruby.elements;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jrubyparser.ast.DefsNode;
import org.jrubyparser.ast.MethodDefNode;
import org.jrubyparser.ast.Node;
import org.netbeans.modules.csl.api.ElementKind;
import org.netbeans.modules.csl.api.Modifier;
import org.netbeans.modules.csl.spi.ParserResult;


public class AstMethodElement extends AstElement implements MethodElement {
    private List<String> parameters;
    private Modifier access = Modifier.PUBLIC;

    public AstMethodElement(ParserResult info, Node node) {
        super(info, node);
    }

    @Override
    public List<String> getParameters() {
        if (parameters == null) parameters = ((MethodDefNode)node).getArgs().getNormativeParameterNameList(false);

        return parameters;
    }

    @Override
    public boolean isDeprecated() {
        // XXX TODO: When wrapping java objects I guess these functions -could- be deprecated, right?
        return false;
    }

    @Override
    public String getName() {
        if (name == null) {
            if (node instanceof MethodDefNode) name = ((MethodDefNode) node).getName();
            if (name == null) name = node.toString(); // FIXME: Can this really happen?
        }

        return name;
    }

    public void setModifiers(Set<Modifier> modifiers) {
        this.modifiers = modifiers;
    }

    @Override
    public Set<Modifier> getModifiers() {
        if (modifiers == null) modifiers = node instanceof DefsNode ? EnumSet.of(Modifier.STATIC, access) : EnumSet.of(access);

        return modifiers;
    }

    public void setAccess(Modifier access) {
        this.access = access;
        modifiers = (modifiers != null && modifiers.contains(Modifier.STATIC)) ? EnumSet.of(Modifier.STATIC, access) : null;
    }

    @Override
    public ElementKind getKind() {
        return ("initialize".equals(getName()) || "new".equals(getName())) ? ElementKind.CONSTRUCTOR : ElementKind.METHOD;
    }

    /**
     * @todo Compute answer
     */
    @Override
    public boolean isTopLevel() {
        return false;
    }

    /**
     * @todo Compute answer
     */
    @Override
    public boolean isInherited() {
        return false;
    }
}
