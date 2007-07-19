/*
 * StructureAnalyzerTest.java
 * JUnit based test
 *
 * Created on July 14, 2007, 4:40 PM
 */

package org.netbeans.modules.ruby;

import java.util.Collections;
import java.util.List;
import org.netbeans.api.gsf.CompilationInfo;
import java.util.ArrayList;
import java.util.Comparator;
import javax.swing.text.Document;
import org.netbeans.api.gsf.CompilationInfo;
import org.netbeans.api.gsf.ElementKind;
import org.netbeans.api.gsf.HtmlFormatter;
import org.netbeans.api.gsf.StructureItem;

/**
 *
 * @author Tor Norbye
 */
public class StructureAnalyzerTest extends RubyTestBase {
    
    public StructureAnalyzerTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
    
    private void annotate(int indent, StringBuilder sb, Document document, List<? extends StructureItem> structure) {
        for (StructureItem element : structure) {
            for (int i = 0; i < indent; i++) {
                sb.append("  ");
            }
            sb.append(element.getName());
            sb.append(":");
            sb.append(element.getKind());
            sb.append(":");
            sb.append(element.getModifiers());
            sb.append(":");
            sb.append(element.getHtml());
            sb.append(":");
            sb.append("\n");
            List<? extends StructureItem> children = element.getNestedItems();
            if (children != null && children.size() > 0) {
                List<? extends StructureItem> c = new ArrayList<StructureItem>(children);
                // Sort children to make tests more stable
                Collections.sort(c, new Comparator<StructureItem>() {
                    public int compare(StructureItem s1, StructureItem s2) {
                        return s1.getName().compareTo(s2.getName());
                    }
                    
                });
                
                annotate(indent+1, sb, document, c);
            }
        }
    }

    private String annotate(Document document, List<? extends StructureItem> structure) {
        StringBuilder sb = new StringBuilder();
        annotate(0, sb, document, structure);
        
        return sb.toString();
    }
    
    
    private void checkStructure(String relFilePath) throws Exception {
        CompilationInfo info = getInfo(relFilePath);
        StructureAnalyzer analyzer = new StructureAnalyzer();
        HtmlFormatter formatter = new HtmlFormatter() {
            private StringBuilder sb = new StringBuilder();
            
            public void reset() {
                sb.setLength(0);
            }

            public void appendHtml(String html) {
                sb.append(html);
            }

            public void appendText(String text) {
                // TODO escaped
                sb.append("ESCAPED{");
                sb.append(text);
                sb.append("}");
            }

            public void name(ElementKind kind, boolean start) {
                if (start) {
                    sb.append(kind);
                }
            }

            public void parameters(boolean start) {
                if (start) {
                    sb.append("PARAMETERS{");
                } else {
                    sb.append("}");
                }
            }

            public void type(boolean start) {
                if (start) {
                    sb.append("TYPE{");
                } else {
                    sb.append("}");
                }
            }

            public void deprecated(boolean start) {
                if (start) {
                    sb.append("DEPRECATED{");
                } else {
                    sb.append("}");
                }
            }

            public String getText() {
                return sb.toString();
            }
            
        };
        List<? extends StructureItem> structure = analyzer.scan(info, formatter);
        
        String annotatedSource = annotate(info.getDocument(), structure);

        assertDescriptionMatches(relFilePath, annotatedSource, false, ".structure");
    }
    
    public void testAnalysis() throws Exception {
        checkStructure("testfiles/postgresql_adapter.rb");
    }

    public void testAnalysis2() throws Exception {
        checkStructure("testfiles/ape.rb");
    }

    public void testAnalysis3() throws Exception {
        checkStructure("testfiles/date.rb");
    }

    public void testAnalysis4() throws Exception {
        checkStructure("testfiles/resolv.rb");
    }

    public void testUnused() throws Exception {
        checkStructure("testfiles/unused.rb");
    }

}
