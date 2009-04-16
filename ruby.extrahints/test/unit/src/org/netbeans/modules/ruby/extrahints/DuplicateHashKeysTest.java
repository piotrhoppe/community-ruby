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

package org.netbeans.modules.ruby.extrahints;

import java.util.HashSet;
import java.util.Set;
import org.netbeans.modules.ruby.hints.HintTestBase;
import org.netbeans.modules.ruby.hints.infrastructure.RubyAstRule;

/**
 *
 * @author Tor Norbye
 */
public class DuplicateHashKeysTest extends HintTestBase {

    public DuplicateHashKeysTest(String testName) {
        super(testName);
    }

    private RubyAstRule createRule() {
        return new DuplicateHashKeys();
    }

    public void testRegistered() throws Exception {
        ensureRegistered(createRule());
    }

    public void testHint1() throws Exception {
        checkHints(this, createRule(), "testfiles/duplicatekeys.rb", null);
    }

    public void testHint2() throws Exception {
        checkHints(this, createRule(), "testfiles/duplicatekeys2.rb", null);
    }

    public void testNoHints1() throws Exception {
        checkHints(this, createRule(), "testfiles/superfib.rb", null);
    }

    public void testNoHints2() throws Exception {
        checkHints(this, createRule(), "testfiles/element.rb", null);
    }

        public void testNoPositives() throws Exception {
            try {
            parseErrorsOk = true;
            Set<String> exceptions = new HashSet<String>();

            // Known exceptions
            exceptions.add("asset_tag_helper_test.rb");

            assertNoJRubyMatches(createRule(), exceptions);

        } finally {
            parseErrorsOk = false;
        }
    }
}