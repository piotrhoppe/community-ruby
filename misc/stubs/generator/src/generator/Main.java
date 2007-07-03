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
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package generator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @todo This should be rewritten as a small Ruby program and folded into the rdocscanner project
 * @todo Pull the Ruby versions etc. out from the actual source
 * @todo Improve arglist reduction in rdocscanner
 * @todo Deal with invalid constant RHS contents?
 * @todo Handle the "Document-method" etc. classes/comments?
 * @todo Try to prune out "empty" definitions like just "module Kernel\nend" etc. which I see
 *   in some places, such as ext/bigdecimal.
 *
 * @author Tor Norbye
 */
public class Main {
    /** Creates a new instance of Main */
    public Main(String[] args) throws IOException {
        if ((args.length != 2) && (args.length != 3)) {
            System.err.println("Usage: " + this.getClass().getName() +
                " <output> <rdocdata> <ext-rdocdata> \n" +
                "Processes special RDoc output data and builds up a stub tree\n" +
                "for NetBeans. The RDoc data is produced by the RubyStubsGenerator\n" +
                "modification for rdoc. The ext-rdoc data represents the ext/ directory in C Ruby's sourcebase\n" +
                "which if present will generate separate class additions rather than getting folded into the\n" +
                "normal class definition.\n");
            System.exit(0);
        }

        File output = new File(args[0]);

        if (output.exists()) {
            System.err.println(output.getAbsolutePath() + " already exists");
            System.exit(0);
        }

        File rdoc = new File(args[1]);

        if (!rdoc.exists()) {
            System.err.println(rdoc.getAbsolutePath() + " doesn't exist");
            System.exit(0);
        }

        File extrdoc = null;

        if (args.length > 2) {
            extrdoc = new File(args[2]);

            if (!rdoc.exists()) {
                System.err.println(extrdoc.getAbsolutePath() + " doesn't exist");
                System.exit(0);
            }
        }

        output.mkdirs();

        File[] files = rdoc.listFiles();

        for (File f : files) {
            if (f.isDirectory()) {
                processDir(f, null, output, null, null, false);
            }
        }

        if (extrdoc != null) {
            files = extrdoc.listFiles();

            for (File f : files) {
                if (f.isDirectory()) {
                    processDir(f, null, output, f.getName(), null, true);
                }
            }
        }
    }

    private boolean processDir(File f, BufferedWriter bw, File output, String relativeName,
        String module, boolean isExt) throws IOException {
        boolean wroteSomething = false;
        System.out.println("Processing " + f.getPath());

        if (f.getName().equals("File")) {
            System.out.println("HERE!");
        }
        assert f.isDirectory();

        // This dir either corresponds to a class, or a module, or something else
        File[] children = f.listFiles();

        if (children == null) {
            System.err.println("WARNING: No contents in " + f.getPath());

            return false;
        }

        // Find classes
        String name;

        if (!isExt) {
            name = "stub_" + f.getName().toLowerCase() + ".rb";
        } else {
            assert relativeName != null;
            name = relativeName + ".rb";
        }

        boolean opened = false;
        boolean skip = false;

        for (File c : children) {
            if (c.getName().startsWith("cdesc-")) { // NOI18N

                if (Character.isLowerCase(f.getName().charAt(0))) {
                    skip = true;

                    // Skip classes like "fatal" - we can't generate valid
                    // Ruby for these!!
                } else {
                    // Found the class. Create a file.
                    if (bw == null) {
                        bw = new BufferedWriter(new FileWriter(new File(output, name)));
                        addFileHeader(bw);
                        opened = true;
                    }

                    addContents(bw, c);
                    wroteSomething = true;
                }
            }
        }

        if (!skip && (bw != null)) {
            wroteSomething = true;

            if (module != null) {
                bw.write("module "); // NOI18N
                bw.write(module);
                bw.write("\n");
            }

            // Add all the methods to the class
            for (File c : children) {
                if (c.isFile() && !c.getName().startsWith("cdesc-")) { // NOI18N
                    addContents(bw, c);
                }
            }

            String m = null;

            // I shouldn't include module redefinitions in nested content!
            //            if (module != null) {
            //                m = module + "::" + f.getName();
            //            } else {
            //                m = f.getName();
            //            }

            // Process nested classes
            for (File c : children) {
                if (c.isDirectory()) {
                    String rel = relativeName;

                    if (Character.isLowerCase(c.getName().charAt(0))) {
                        if (relativeName != null) {
                            rel = relativeName + File.separator + c.getName();
                        } else {
                            rel = c.getName();
                        }
                    }

                    processDir(c, bw, output, rel, m, isExt);
                }
            }

            // The files don't contain the closing end statement
            // (such that it would be easy to add in the methods)
            bw.write("\nend\n"); // NOI18N

            if (module != null) {
                bw.write("\nend\n"); // NOI18N
            }
        } else if (!skip) {
            boolean added = false;

            if (isExt) {
                bw = new BufferedWriter(new FileWriter(new File(output, name)));
                addFileHeader(bw);
                opened = true;
            }

            for (File c : children) {
                if (c.isDirectory()) {
                    String rel = relativeName;

                    if (rel == null) {
                        rel = f.getName();
                    }

                    if (Character.isLowerCase(c.getName().charAt(0))) {
                        if (relativeName != null) {
                            rel = relativeName + File.separator + c.getName();
                        } else {
                            rel = c.getName();
                        }
                    }

                    boolean modified = processDir(c, bw, output, rel, module, isExt);

                    if (modified) {
                        added = true;
                    }
                }
            }

            if (isExt && !added) {
                // bw is pretty much empty so nuke it
                bw.close();
                new File(output, name).delete();
            }
        }

        if (opened) {
            bw.close();
        }

        return wroteSomething;
    }

    private void addFileHeader(BufferedWriter bw) throws IOException {
        bw.write("# This is a machine-generated stub file for the NetBeans IDE Ruby Support.\n" +
            "#\n" + "# Many Ruby methods are \"built in\" which means that there is no\n" +
            "# Ruby source code for them. It is convenient for the IDE however to\n" +
            "# have these files for indexing and documentation purposes, so the following\n" +
            "# class definition is generated from the native implementation as a skeleton\n" +
            "# or stub to index.\n" + "#\n" + "# Ruby Version: \"1.8.6\"\n" +
            //"# Ruby Patch Level: 12\n" +
        //"# Ruby Release: 20061225\n" +
        "# Generator version: 1.0\n" + "#\n" + // Create an empty line and empty comment such that the file header
        // isn't interpreter as a class comment for files missing documentation
        "\n" + "#\n");
    }

    private void addContents(BufferedWriter bw, File c)
        throws FileNotFoundException, IOException {
        BufferedReader br = new BufferedReader(new FileReader(c));

        while (true) {
            String line = br.readLine();

            if (line == null) {
                break;
            }

            bw.write(line);
            bw.write("\n");
        }

        br.close();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            new generator.Main(args);
        } catch (IOException ex) {
            Logger.getLogger("global").log(Level.SEVERE, null, ex);
        }
    }
}
