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
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2007 Sun
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

package org.netbeans.modules.refactoring.ruby;

import java.awt.Color;
import java.io.CharConversionException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.StyleConstants;
import org.jruby.ast.AliasNode;
import org.jruby.ast.Colon2Node;
import org.jruby.ast.IScopingNode;
import org.jruby.ast.Node;
import org.jruby.ast.types.INameNode;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.modules.gsf.api.ElementKind;
import org.netbeans.modules.gsfpath.api.classpath.ClassPath;
import org.netbeans.modules.gsfpath.api.classpath.GlobalPathRegistry;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.napi.gsfret.source.ClasspathInfo;
import org.netbeans.napi.gsfret.source.CompilationInfo;
import org.netbeans.napi.gsfret.source.Source;
import org.netbeans.napi.gsfret.source.SourceUtils;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.gsf.Language;
import org.netbeans.modules.gsf.LanguageRegistry;
import org.netbeans.modules.ruby.AstUtilities;
import org.netbeans.modules.ruby.RubyIndex;
import org.netbeans.modules.ruby.RubyMimeResolver;
import org.netbeans.modules.ruby.RubyUtils;
import org.netbeans.modules.ruby.elements.IndexedMethod;
import org.netbeans.modules.ruby.lexer.RubyTokenId;
import org.netbeans.modules.ruby.rubyproject.RubyProject;
import org.netbeans.modules.gsfpath.spi.classpath.support.ClassPathSupport;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.text.CloneableEditorSupport;
import org.openide.util.Exceptions;
import org.openide.util.Utilities;
import org.openide.xml.XMLUtil;

/**
 * Various utilies related to Ruby refactoring; the generic ones are based
 * on the ones from the Java refactoring module.
 * 
 * @author Jan Becicka
 * @author Tor Norbye
 */
public class RetoucheUtils {
    // XXX Should this be unused now?
    public static Source createSource(ClasspathInfo cpInfo, FileObject fo) {
        if (RubyUtils.isRubyOrRhtmlFile(fo)) {
            return Source.create(cpInfo, fo);
        }
        
        return null;
    }
    
    public static Source getSource(FileObject fo) {
        Source source =  Source.forFileObject(fo);
        
        return source;
    }
    
    public static Source getSource(Document doc) {
        Source source = Source.forDocument(doc);

        return source;
    }
    
    public static BaseDocument getDocument(CompilationInfo info, FileObject fo) {
        BaseDocument doc = null;

        if (info != null) {
            doc = (BaseDocument)info.getDocument();
        }

        if (doc == null) {
            try {
                // Gotta open it first
                DataObject od = DataObject.find(fo);
                EditorCookie ec = od.getCookie(EditorCookie.class);

                if (ec != null) {
                    doc = (BaseDocument)ec.openDocument();
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        return doc;
    }
    
    /** Compute the names (full and simple, e.g. Foo::Bar and Bar) for the given node, if any, and return as 
     * a String[2] = {name,simpleName} */
    public static String[] getNodeNames(Node node) {
        String name = null;
        String simpleName = null;
       
        if (node instanceof Colon2Node) {
            Colon2Node c2n = (Colon2Node)node;
            simpleName = c2n.getName();
            name = AstUtilities.getFqn(c2n);
        } else if (node instanceof AliasNode) {
            name = ((AliasNode)node).getNewName();
        }
        
        if (name == null && node instanceof INameNode) {
            name = ((INameNode)node).getName();
        }
        if (name == null && node instanceof IScopingNode) {
            if (((IScopingNode)node).getCPath() instanceof Colon2Node) {
                Colon2Node c2n = (Colon2Node)((IScopingNode)node).getCPath();
                simpleName = c2n.getName();
                name = AstUtilities.getFqn(c2n);
            } else {
                name = AstUtilities.getClassOrModuleName((IScopingNode)node);
            }
        }
        if (simpleName == null) {
            simpleName = name;
        }
        
        return new String[] { name, simpleName };
    }
    
    public static CloneableEditorSupport findCloneableEditorSupport(CompilationInfo info) {
        DataObject dob = null;
        try {
            dob = DataObject.find(info.getFileObject());
        } catch (DataObjectNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        }
        return RetoucheUtils.findCloneableEditorSupport(dob);
    }
    
    public static CloneableEditorSupport findCloneableEditorSupport(DataObject dob) {
        Object obj = dob.getCookie(org.openide.cookies.OpenCookie.class);
        if (obj instanceof CloneableEditorSupport) {
            return (CloneableEditorSupport)obj;
        }
        obj = dob.getCookie(org.openide.cookies.EditorCookie.class);
        if (obj instanceof CloneableEditorSupport) {
            return (CloneableEditorSupport)obj;
        }
        return null;
    }

    public static String htmlize(String input) {
        try {
            return XMLUtil.toElementContent(input);
        } catch (CharConversionException cce) {
            Exceptions.printStackTrace(cce);
            return input;
        }
    }

    /** Return the most distant method in the hierarchy that is overriding the given method, or null */
    public static IndexedMethod getOverridingMethod(RubyElementCtx element, CompilationInfo info) {
        RubyIndex index = RubyIndex.get(info.getIndex(RubyMimeResolver.RUBY_MIME_TYPE));
        String fqn = AstUtilities.getFqnName(element.getPath());

        return index.getOverridingMethod(fqn, element.getName());
    }

    public static String getHtml(String text) {
        StringBuffer buf = new StringBuffer();
        // TODO - check whether we need ruby highlighting or rhtml highlighting
        TokenHierarchy tokenH = TokenHierarchy.create(text, RubyTokenId.language());
        Lookup lookup = MimeLookup.getLookup(MimePath.get(RubyMimeResolver.RUBY_MIME_TYPE));
        FontColorSettings settings = lookup.lookup(FontColorSettings.class);
        @SuppressWarnings("unchecked")
        TokenSequence<? extends TokenId> tok = tokenH.tokenSequence();
        while (tok.moveNext()) {
            Token<? extends TokenId> token = tok.token();
            String category = token.id().name();
            AttributeSet set = settings.getTokenFontColors(category);
            if (set == null) {
                category = token.id().primaryCategory();
                if (category == null) {
                    category = "whitespace"; //NOI18N
                }
                set = settings.getTokenFontColors(category);                
            }
            String tokenText = htmlize(token.text().toString());
            buf.append(color(tokenText, set));
        }
        return buf.toString();
    }

    private static String color(String string, AttributeSet set) {
        if (set==null)
            return string;
        if (string.trim().length() == 0) {
            return Utilities.replaceString(Utilities.replaceString(string, " ", "&nbsp;"), "\n", "<br>"); //NOI18N
        } 
        StringBuffer buf = new StringBuffer(string);
        if (StyleConstants.isBold(set)) {
            buf.insert(0,"<b>"); //NOI18N
            buf.append("</b>"); //NOI18N
        }
        if (StyleConstants.isItalic(set)) {
            buf.insert(0,"<i>"); //NOI18N
            buf.append("</i>"); //NOI18N
        }
        if (StyleConstants.isStrikeThrough(set)) {
            buf.insert(0,"<s>"); // NOI18N
            buf.append("</s>"); // NOI18N
        }
        buf.insert(0,"<font color=" + getHTMLColor(StyleConstants.getForeground(set)) + ">"); //NOI18N
        buf.append("</font>"); //NOI18N
        return buf.toString();
    }
    
    private static String getHTMLColor(Color c) {
        String colorR = "0" + Integer.toHexString(c.getRed()); //NOI18N
        colorR = colorR.substring(colorR.length() - 2); 
        String colorG = "0" + Integer.toHexString(c.getGreen()); //NOI18N
        colorG = colorG.substring(colorG.length() - 2);
        String colorB = "0" + Integer.toHexString(c.getBlue()); //NOI18N
        colorB = colorB.substring(colorB.length() - 2);
        String html_color = "#" + colorR + colorG + colorB; //NOI18N
        return html_color;
    }

    public static boolean isElementInOpenProject(FileObject f) {
        Project p = FileOwnerQuery.getOwner(f);
        Project[] opened = OpenProjects.getDefault().getOpenProjects();
        for (int i = 0; i<opened.length; i++) {
            if (p==opened[i])
                return true;
        }
        return false;
    }

    public static boolean isFileInOpenProject(FileObject file) {
        assert file != null;
        Project p = FileOwnerQuery.getOwner(file);
        Project[] opened = OpenProjects.getDefault().getOpenProjects();
        for (int i = 0; i<opened.length; i++) {
            if (p==opened[i])
                return true;
        }
        return false;
    }
    
    public static boolean isOnSourceClasspath(FileObject fo) {
        Project p = FileOwnerQuery.getOwner(fo);
        if (p==null) {
            return false;
        }
        Project[] opened = OpenProjects.getDefault().getOpenProjects();
        for (int i = 0; i<opened.length; i++) {
            if (p==opened[i]) {
                SourceGroup[] gr = ProjectUtils.getSources(p).getSourceGroups(RubyProject.SOURCES_TYPE_RUBY);
                for (int j = 0; j < gr.length; j++) {
                    if (fo==gr[j].getRootFolder()) {
                        return true;
                    }
                    if (FileUtil.isParentOf(gr[j].getRootFolder(), fo)) {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    public static boolean isClasspathRoot(FileObject fo) {
        ClassPath cp = ClassPath.getClassPath(fo, ClassPath.SOURCE);
        if (cp != null) {
            FileObject f = cp.findOwnerRoot(fo);
            if (f != null) {
                return fo.equals(f);
            }
        }
        
        return false;
    }
    
    public static boolean isRefactorable(FileObject file) {
        return RubyUtils.isRubyOrRhtmlFile(file) && isFileInOpenProject(file) && isOnSourceClasspath(file);
    }
    
    public static String getPackageName(FileObject folder) {
        assert folder.isFolder() : "argument must be folder";
        return ClassPath.getClassPath(
                folder, ClassPath.SOURCE)
                .getResourceName(folder, '.', false);
    }
    
    public static String getPackageName(URL url) {
        File f = null;
        try {
            f = FileUtil.normalizeFile(new File(url.toURI()));
        } catch (URISyntaxException uRISyntaxException) {
            throw new IllegalArgumentException("Cannot create package name for url " + url);
        }
        String suffix = "";
        
        do {
            FileObject fo = FileUtil.toFileObject(f);
            if (fo != null) {
                if ("".equals(suffix))
                    return getPackageName(fo);
                String prefix = getPackageName(fo);
                return prefix + ("".equals(prefix)?"":".") + suffix;
            }
            if (!"".equals(suffix)) {
                suffix = "." + suffix;
            }
            suffix = URLDecoder.decode(f.getPath().substring(f.getPath().lastIndexOf(File.separatorChar)+1)) + suffix;
            f = f.getParentFile();
        } while (f!=null);
        throw new IllegalArgumentException("Cannot create package name for url " + url);
    }

    /**
     * creates or finds FileObject according to 
     * @param url
     * @return FileObject
     */
    public static FileObject getOrCreateFolder(URL url) throws IOException {
        try {
            FileObject result = URLMapper.findFileObject(url);
            if (result != null)
                return result;
            File f = new File(url.toURI());
            
            result = FileUtil.createFolder(f);
            return result;
        } catch (URISyntaxException ex) {
            throw (IOException) new IOException().initCause(ex);
        }
    }
    
    public static FileObject getClassPathRoot(URL url) throws IOException {
        FileObject result = URLMapper.findFileObject(url);
        File f = FileUtil.normalizeFile(new File(url.getPath()));
        while (result==null) {
            result = FileUtil.toFileObject(f);
            f = f.getParentFile();
        }
        return ClassPath.getClassPath(result, ClassPath.SOURCE).findOwnerRoot(result);
    }
    
    public static ElementKind getElementKind(RubyElementCtx tph) {
        return tph.getKind();
    }
    
    public static ClasspathInfo getClasspathInfoFor(FileObject ... files) {
        assert files.length >0;
        Set<URL> dependentRoots = new HashSet<URL>();
        for (FileObject fo: files) {
            Project p = null;
            if (fo!=null)
                p=FileOwnerQuery.getOwner(fo);
            if (p!=null) {
                URL sourceRoot = URLMapper.findURL(ClassPath.getClassPath(fo, ClassPath.SOURCE).findOwnerRoot(fo), URLMapper.INTERNAL);
                dependentRoots.addAll(SourceUtils.getDependentRoots(sourceRoot));
                for (SourceGroup root:ProjectUtils.getSources(p).getSourceGroups(RubyProject.SOURCES_TYPE_RUBY)) {
                    dependentRoots.add(URLMapper.findURL(root.getRootFolder(), URLMapper.INTERNAL));
                }
            } else {
                for(ClassPath cp: GlobalPathRegistry.getDefault().getPaths(ClassPath.SOURCE)) {
                    for (FileObject root:cp.getRoots()) {
                        dependentRoots.add(URLMapper.findURL(root, URLMapper.INTERNAL));
                    }
                }
            }
        }
        
        ClassPath rcp = ClassPathSupport.createClassPath(dependentRoots.toArray(new URL[dependentRoots.size()]));
        ClassPath nullPath = ClassPathSupport.createClassPath(new FileObject[0]);
        ClassPath boot = files[0]!=null?ClassPath.getClassPath(files[0], ClassPath.BOOT):nullPath;
        ClassPath compile = files[0]!=null?ClassPath.getClassPath(files[0], ClassPath.COMPILE):nullPath;
        ClasspathInfo cpInfo = ClasspathInfo.create(boot, compile, rcp);
        return cpInfo;
    }
    
    public static ClasspathInfo getClasspathInfoFor(RubyElementCtx ctx) {
        return getClasspathInfoFor(ctx.getFileObject());
    }
    
    public static List<FileObject> getRubyFilesInProject(FileObject fileInProject) {
        ClasspathInfo cpInfo = RetoucheUtils.getClasspathInfoFor(fileInProject);
        ClassPath cp = cpInfo.getClassPath(ClasspathInfo.PathKind.SOURCE);
        List<FileObject> list = new ArrayList<FileObject>(100);
        for (ClassPath.Entry entry : cp.entries()) {
            FileObject root = entry.getRoot();
            String name = root.getName();
            // Skip non-refactorable parts in renaming
            if (name.equals("vendor") || name.equals("script")) { // NOI18N
                continue;
            }
            addRubyFiles(list, root);
        }
        
        return list;
    }
    
    private static void addRubyFiles(List<FileObject> list, FileObject f) {
        if (f.isFolder()) {
            for (FileObject child : f.getChildren()) {
                addRubyFiles(list, child);
            }
        } else if (RubyUtils.isRubyOrRhtmlFile(f)) {
            list.add(f);
        }
    }
}
