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

package org.netbeans.modules.ruby;

import javax.swing.JTextArea;
import javax.swing.text.Caret;
import org.netbeans.modules.gsf.api.Completable.QueryType;
import org.netbeans.api.ruby.platform.RubyPlatform;
import org.netbeans.api.ruby.platform.RubyPlatformManager;
import org.netbeans.api.ruby.platform.TestUtil;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.gsf.Language;
import org.netbeans.modules.gsf.LanguageRegistry;
import org.netbeans.modules.gsf.api.Completable;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tor Norbye
 */
public class CodeCompleterTest extends RubyTestBase {
    
    public CodeCompleterTest(String testName) {
        super(testName);
    }

    @Override
    protected Completable getCodeCompleter() {
        return new CodeCompleter();
    }
    
    public void checkCompletion(String file, String caretLine) throws Exception {
        checkCompletion(file, caretLine, false);
    }
    
    @Override
    public void checkCompletion(String file, String caretLine, boolean includeModifiers) throws Exception {
        System.setProperty("netbeans.user", getWorkDirPath());
        FileObject jrubyHome = TestUtil.getXTestJRubyHomeFO();
        assertNotNull(jrubyHome);
        FileObject preindexed = jrubyHome.getParent().getFileObject("preindexed");
        RubyIndexer.setPreindexedDb(preindexed);
        initializeRegistry();
        // Force classpath initialization
        RubyPlatform platform = RubyPlatformManager.getDefaultPlatform();
        platform.getGemManager().getNonGemLoadPath();
        Language language = LanguageRegistry.getInstance().getLanguageByMimeType(RubyMimeResolver.RUBY_MIME_TYPE);
        org.netbeans.modules.gsfret.source.usages.ClassIndexManager.get(language).getBootIndices();

        RubyIndex.setClusterUrl("file:/bogus"); // No translation
        
        super.checkCompletion(file, caretLine, includeModifiers);
    }
    
    @Override
    public void checkComputeMethodCall(String file, String caretLine, String fqn, String param, boolean expectSuccess) throws Exception {
        System.setProperty("netbeans.user", getWorkDirPath());
        FileObject jrubyHome = TestUtil.getXTestJRubyHomeFO();
        assertNotNull(jrubyHome);
        FileObject preindexed = jrubyHome.getParent().getFileObject("preindexed");
        RubyIndexer.setPreindexedDb(preindexed);
        initializeRegistry();
        // Force classpath initialization
        RubyPlatform platform = RubyPlatformManager.getDefaultPlatform();
        platform.getGemManager().getNonGemLoadPath();
        Language language = LanguageRegistry.getInstance().getLanguageByMimeType(RubyMimeResolver.RUBY_MIME_TYPE);
        org.netbeans.modules.gsfret.source.usages.ClassIndexManager.get(language).getBootIndices();

        RubyIndex.setClusterUrl("file:/bogus"); // No translation
        
        super.checkComputeMethodCall(file, caretLine, fqn, param, expectSuccess);
    }

    public void testPrefix1() throws Exception {
        checkPrefix("testfiles/cc-prefix1.rb");
    }
    
    public void testPrefix2() throws Exception {
        checkPrefix("testfiles/cc-prefix2.rb");
    }

    public void testPrefix3() throws Exception {
        checkPrefix("testfiles/cc-prefix3.rb");
    }

    public void testPrefix4() throws Exception {
        checkPrefix("testfiles/cc-prefix4.rb");
    }

    public void testPrefix5() throws Exception {
        checkPrefix("testfiles/cc-prefix5.rb");
    }

    public void testPrefix6() throws Exception {
        checkPrefix("testfiles/cc-prefix6.rb");
    }

    public void testPrefix7() throws Exception {
        checkPrefix("testfiles/cc-prefix7.rb");
    }

    public void testPrefix8() throws Exception {
        checkPrefix("testfiles/cc-prefix8.rb");
    }
    
    
    private void assertAutoQuery(QueryType queryType, String source, String typedText) {
        CodeCompleter completer = new CodeCompleter();
        int caretPos = source.indexOf('^');
        source = source.substring(0, caretPos) + source.substring(caretPos+1);
        
        BaseDocument doc = getDocument(source);
        JTextArea ta = new JTextArea(doc);
        Caret caret = ta.getCaret();
        caret.setDot(caretPos);
        
        QueryType qt = completer.getAutoQuery(ta, typedText);
        assertEquals(queryType, qt);
    }

    public void testAutoQuery1() throws Exception {
        assertAutoQuery(QueryType.NONE, "foo^", "o");
        assertAutoQuery(QueryType.NONE, "foo^", " ");
        assertAutoQuery(QueryType.NONE, "foo^", "c");
        assertAutoQuery(QueryType.NONE, "foo^", "d");
        assertAutoQuery(QueryType.NONE, "foo^", ";");
        assertAutoQuery(QueryType.NONE, "foo^", "f");
        assertAutoQuery(QueryType.NONE, "Foo:^", ":");
        assertAutoQuery(QueryType.NONE, "Foo^ ", ":");
        assertAutoQuery(QueryType.NONE, "Foo^bar", ":");
        assertAutoQuery(QueryType.NONE, "Foo:^bar", ":");
    }

    public void testAutoQuery2() throws Exception {
        assertAutoQuery(QueryType.STOP, "foo^", "[");
        assertAutoQuery(QueryType.STOP, "foo^", "(");
        assertAutoQuery(QueryType.STOP, "foo^", "{");
        assertAutoQuery(QueryType.STOP, "foo^", "\n");
    }

    public void testAutoQuery3() throws Exception {
        assertAutoQuery(QueryType.COMPLETION, "foo.^", ".");
        assertAutoQuery(QueryType.COMPLETION, "Foo::^", ":");
        assertAutoQuery(QueryType.COMPLETION, "foo^ ", ".");
        assertAutoQuery(QueryType.COMPLETION, "foo^bar", ".");
        assertAutoQuery(QueryType.COMPLETION, "Foo::^bar", ":");
    }

    public void testAutoQueryComments() throws Exception {
        assertAutoQuery(QueryType.COMPLETION, "foo^ # bar", ".");
        assertAutoQuery(QueryType.NONE, "#^foo", ".");
        assertAutoQuery(QueryType.NONE, "# foo^", ".");
        assertAutoQuery(QueryType.NONE, "# foo^", ":");
    }

    public void testAutoQueryStrings() throws Exception {
        assertAutoQuery(QueryType.COMPLETION, "foo^ 'foo'", ".");
        assertAutoQuery(QueryType.NONE, "'^foo'", ".");
        assertAutoQuery(QueryType.NONE, "/f^oo/", ".");
        assertAutoQuery(QueryType.NONE, "\"^\"", ".");
        assertAutoQuery(QueryType.NONE, "\" foo^ \"", ":");
    }

    public void testAutoQueryRanges() throws Exception {
        assertAutoQuery(QueryType.NONE, "x..^", ".");
        assertAutoQuery(QueryType.NONE, "x..^5", ".");
    }

    // This test is unstable for some reason
    //public void testCompletion1() throws Exception {
    //    checkCompletion("testfiles/completion/lib/test1.rb", "f.e^");
    //}
    // ditto
    //public void testCompletion2() throws Exception {
    //    // This test doesn't pass yet because we need to index the -current- file
    //    // before resuming
    //    checkCompletion("testfiles/completion/lib/test2.rb", "Result is #{@^myfield} and #@another.");
    //}
    // 
    //public void testCompletion3() throws Exception {
    //    checkCompletion("testfiles/completion/lib/test2.rb", "Result is #{@myfield} and #@a^nother.");
    //}
    
    public void testCompletion4() throws Exception {
        checkCompletion("testfiles/completion/lib/test2.rb", "Hell^o World");
    }
    
    public void testCompletion5() throws Exception {
        checkCompletion("testfiles/completion/lib/test2.rb", "/re^g/");
    }

    public void testCompletion6() throws Exception {
        checkCompletion("testfiles/completion/lib/test2.rb", "class My^Test");
    }
//    
//    // TODO: Test open classes, class inheritance, relative symbols, finding classes, superclasses, def completion, ...

    public void testCall1() throws Exception {
        checkComputeMethodCall("testfiles/calls/call1.rb", "create_table(^firstarg,  :id => true)",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "table_name", true);
    }

    public void testCall2() throws Exception {
        checkComputeMethodCall("testfiles/calls/call1.rb", "create_table(firstarg^,  :id => true)",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "table_name", true);
    }
    public void testCall3() throws Exception {
        checkComputeMethodCall("testfiles/calls/call1.rb", "create_table(firstarg,^  :id => true)",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "options", true);
    }
    public void testCall4() throws Exception {
        checkComputeMethodCall("testfiles/calls/call1.rb", "create_table(firstarg,  ^:id => true)",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "options", true);
    }
    public void testCallSpace1() throws Exception {
        checkComputeMethodCall("testfiles/calls/call1.rb", "create_table firstarg,  ^:id => true",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "options", true);
    }
    public void testCallSpace2() throws Exception {
        checkComputeMethodCall("testfiles/calls/call1.rb", "create_table ^firstarg,  :id => true",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "table_name", true);
    }
    public void testCall5() throws Exception {
        checkComputeMethodCall("testfiles/calls/call2.rb", "create_table(^)",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "table_name", true);
    }
    public void testCall6() throws Exception {
        checkComputeMethodCall("testfiles/calls/call3.rb", "create_table^",
                null, null, false);
    }
    public void testCall7() throws Exception {
        checkComputeMethodCall("testfiles/calls/call3.rb", "create_table ^",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "table_name", true);
    }
    public void testCall8() throws Exception {
        checkComputeMethodCall("testfiles/calls/call4.rb", "create_table foo,^",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "options", true);
    }
    public void testCall9() throws Exception {
        checkComputeMethodCall("testfiles/calls/call4.rb", "create_table foo, ^",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "options", true);
    }
    public void testCall10() throws Exception {
        checkComputeMethodCall("testfiles/calls/call5.rb", " create_table(foo, ^)",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "options", true);
    }
    public void testCall11() throws Exception {
        checkComputeMethodCall("testfiles/calls/call6.rb", " create_table(foo, :key => ^)",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "options", true);
    }

    public void testCall12() throws Exception {
        checkComputeMethodCall("testfiles/calls/call7.rb", " create_table(foo, :key => :^)",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "options", true);
    }

    public void testCall13() throws Exception {
        checkComputeMethodCall("testfiles/calls/call8.rb", " create_table(foo, :key => :a^)",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "options", true);
    }
    public void testCall14() throws Exception {
        checkComputeMethodCall("testfiles/calls/call9.rb", " create_table(foo, :^)",
                "ActiveRecord::SchemaStatements::ClassMethods#create_table", "options", true);
    }

//    public void testCall15() throws Exception {
//        checkComputeMethodCall("testfiles/calls/call10.rb", "File.exists?(^)",
//                "File#exists", "file", true);
//    }

    public void testCall16() throws Exception {
        checkComputeMethodCall("testfiles/calls/call11.rb", " ^#",
                null, null, false);
    }

    public void testCall17() throws Exception {
        checkComputeMethodCall("testfiles/calls/call12.rb", " ^#",
                null, null, false);
    }
    
    // TODO - test more non-fc calls (e.g. x.foo)
    // TODO test with splat args (more args than are in def list)
    // TODO test with long arg lists
}
