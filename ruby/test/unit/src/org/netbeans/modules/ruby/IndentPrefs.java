/*
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at http://www.netbeans.org/cddl.html
 * or http://www.netbeans.org/cddl.txt.
 *
 * When distributing Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://www.netbeans.org/cddl.txt.
 * If applicable, add the following below the CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Portions Copyrighted 2007 Sun Microsystems, Inc.
 */
package org.netbeans.modules.ruby;

import org.netbeans.api.gsf.FormattingPreferences;

public class IndentPrefs extends FormattingPreferences {

    private int hanging;

    private int indent;

    public IndentPrefs(int indent, int hanging) {
        super();
        this.indent = indent;
        this.hanging = hanging;
    }

    public int getIndentation() {
        return indent;
    }

    public int getHangingIndentation() {
        return hanging;
    }
}
