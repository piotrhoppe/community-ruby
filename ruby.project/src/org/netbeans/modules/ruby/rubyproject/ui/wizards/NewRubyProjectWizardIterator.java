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

package org.netbeans.modules.ruby.rubyproject.ui.wizards;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.modules.ruby.rubyproject.RubyProjectGenerator;
import org.netbeans.modules.ruby.rubyproject.ui.FoldersListSettings;
import org.netbeans.modules.ruby.spi.project.support.rake.RakeProjectHelper;
import org.netbeans.spi.project.ui.support.ProjectChooser;
import org.openide.ErrorManager;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;

/**
 * Wizard to create a new Ruby project.
 */
public class NewRubyProjectWizardIterator implements WizardDescriptor.ProgressInstantiatingIterator {

    static final int TYPE_APP = 0;
    //static final int TYPE_LIB = 1;
    static final int TYPE_EXT = 2;
    
    static final String PROP_NAME_INDEX = "nameIndex";      //NOI18N

//    private static final String MANIFEST_FILE = "manifest.mf"; // NOI18N

    private static final long serialVersionUID = 1L;
    
    private int type;
    
    /** Create a new wizard iterator. */
    public NewRubyProjectWizardIterator() {
        this(TYPE_APP);
    }
    
    public NewRubyProjectWizardIterator(int type) {
        this.type = type;
    }
        
//    public static NewRubyProjectWizardIterator library() {
//        return new NewRubyProjectWizardIterator( TYPE_LIB );
//    }
    
    public static NewRubyProjectWizardIterator existing () {
        return new NewRubyProjectWizardIterator( TYPE_EXT );
    }

    private WizardDescriptor.Panel[] createPanels () {
        return this.type == TYPE_EXT ?
            new WizardDescriptor.Panel[] {
                new PanelConfigureProject( this.type ),
                new PanelSourceFolders.Panel()
            } 
            :new WizardDescriptor.Panel[] {
                new PanelConfigureProject( this.type )
            };
    }
    
    private String[] createSteps() {
        return this.type == TYPE_EXT ?
            new String[] {                
                NbBundle.getMessage(NewRubyProjectWizardIterator.class,"LAB_ConfigureProject"), 
                NbBundle.getMessage(NewRubyProjectWizardIterator.class,"LAB_ConfigureSourceRoots"),
            }
            :new String[] {
                NbBundle.getMessage(NewRubyProjectWizardIterator.class,"LAB_ConfigureProject"), 
            };
    }
    
    
    public Set/*<FileObject>*/ instantiate () throws IOException {
        assert false : "Cannot call this method if implements WizardDescriptor.ProgressInstantiatingIterator.";
        return null;
    }
        
    public Set/*<FileObject>*/ instantiate (ProgressHandle handle) throws IOException {
        handle.start (4);
        //handle.progress (NbBundle.getMessage (NewRubyProjectWizardIterator.class, "LBL_NewRubyProjectWizardIterator_WizardProgress_ReadingProperties"));
        Set resultSet = new HashSet ();
        File dirF = (File)wiz.getProperty("projdir");        //NOI18N
        if (dirF != null) {
            dirF = FileUtil.normalizeFile(dirF);
        }
        String name = (String)wiz.getProperty("name");        //NOI18N
        String mainClass = (String)wiz.getProperty("mainClass");        //NOI18N
        handle.progress (NbBundle.getMessage (NewRubyProjectWizardIterator.class, "LBL_NewRubyProjectWizardIterator_WizardProgress_CreatingProject"), 1);
        if (this.type == TYPE_EXT) {
            File[] sourceFolders = (File[])wiz.getProperty("sourceRoot");        //NOI18N
            File[] testFolders = (File[])wiz.getProperty("testRoot");            //NOI18N
            RubyProjectGenerator.createProject(dirF, name, sourceFolders, testFolders, null );
            handle.progress (2);
            for (int i=0; i<sourceFolders.length; i++) {
                FileObject srcFo = FileUtil.toFileObject(sourceFolders[i]);
                if (srcFo != null) {
                    resultSet.add (srcFo);
                }
            }
        }
        else {
            RakeProjectHelper h = RubyProjectGenerator.createProject(dirF, name, mainClass, null);
            handle.progress (2);
            // BEGIN SEMPLICE MODIFICATIONS
            // Update since format is customized
            //mainClass = (String)wiz.getProperty("mainClass");
            // END SEMPLICE MODIFICATIONS
            
            if (mainClass != null && mainClass.length () > 0) {
                try {
                    //String sourceRoot = "lib"; //(String)j2seProperties.get (RubyProjectProperties.SRC_DIR);
                    FileObject sourcesRoot = h.getProjectDirectory ().getFileObject ("lib");        //NOI18N
                    FileObject mainClassFo = getMainClassFO (sourcesRoot, mainClass);
                    assert mainClassFo != null : "sourcesRoot: " + sourcesRoot + ", mainClass: " + mainClass;        //NOI18N
                    // Returning FileObject of main class, will be called its preferred action
                    resultSet.add (mainClassFo);
                } catch (Exception x) {
                    ErrorManager.getDefault().notify(x);
                }
            }
            // if ( type == TYPE_LIB ) {
                // resultSet.add( h.getProjectDirectory ().getFileObject ("lib") );        //NOI18N 
                // resultSet.add( h.getProjectDirectory() ); // Only expand the project directory
            // }
        }
        FileObject dir = FileUtil.toFileObject(dirF);
//        if (type == TYPE_APP || type == TYPE_EXT) {
//            createManifest(dir);
//        }
        handle.progress (3);

        // Returning FileObject of project diretory. 
        // Project will be open and set as main
        Integer index = (Integer) wiz.getProperty(PROP_NAME_INDEX);
        switch (this.type) {
            case TYPE_APP:
                FoldersListSettings.getDefault().setNewApplicationCount(index.intValue());
                break;
//            case TYPE_LIB:
//                FoldersListSettings.getDefault().setNewLibraryCount(index.intValue());
//                break;
            case TYPE_EXT:
                FoldersListSettings.getDefault().setNewProjectCount(index.intValue());
                break;
        }        
        resultSet.add (dir);
        handle.progress (NbBundle.getMessage (NewRubyProjectWizardIterator.class, "LBL_NewRubyProjectWizardIterator_WizardProgress_PreparingToOpen"), 4);
        dirF = (dirF != null) ? dirF.getParentFile() : null;
        if (dirF != null && dirF.exists()) {
            ProjectChooser.setProjectsFolder (dirF);    
        }
                        
        return resultSet;
    }
    
        
    private transient int index;
    private transient WizardDescriptor.Panel[] panels;
    private transient WizardDescriptor wiz;
    
    public void initialize(WizardDescriptor wiz) {
        this.wiz = wiz;
        index = 0;
        panels = createPanels();
        // Make sure list of steps is accurate.
        String[] steps = createSteps();
        for (int i = 0; i < panels.length; i++) {
            Component c = panels[i].getComponent();
            if (steps[i] == null) {
                // Default step name to component name of panel.
                // Mainly useful for getting the name of the target
                // chooser to appear in the list of steps.
                steps[i] = c.getName();
            }
            if (c instanceof JComponent) { // assume Swing components
                JComponent jc = (JComponent)c;
                // Step #.
                jc.putClientProperty("WizardPanel_contentSelectedIndex", new Integer(i)); // NOI18N
                // Step name (actually the whole list for reference).
                jc.putClientProperty("WizardPanel_contentData", steps); // NOI18N
            }
        }
        //set the default values of the sourceRoot and the testRoot properties
        this.wiz.putProperty("sourceRoot", new File[0]);    //NOI18N
        this.wiz.putProperty("testRoot", new File[0]);      //NOI18N
    }

    public void uninitialize(WizardDescriptor wiz) {
        if (this.wiz != null) {
            this.wiz.putProperty("projdir",null);           //NOI18N
            this.wiz.putProperty("name",null);          //NOI18N
            this.wiz.putProperty("mainClass",null);         //NOI18N
            if (this.type == TYPE_EXT) {
                this.wiz.putProperty("sourceRoot",null);    //NOI18N
                this.wiz.putProperty("testRoot",null);      //NOI18N
            }
            this.wiz = null;
            panels = null;
        }
    }
    
    public String name() {
        return MessageFormat.format (NbBundle.getMessage(NewRubyProjectWizardIterator.class,"LAB_IteratorName"),
            new Object[] {new Integer (index + 1), new Integer (panels.length) });                                
    }
    
    public boolean hasNext() {
        return index < panels.length - 1;
    }
    public boolean hasPrevious() {
        return index > 0;
    }
    public void nextPanel() {
        if (!hasNext()) throw new NoSuchElementException();
        index++;
    }
    public void previousPanel() {
        if (!hasPrevious()) throw new NoSuchElementException();
        index--;
    }
    public WizardDescriptor.Panel current () {
        return panels[index];
    }
    
    // If nothing unusual changes in the middle of the wizard, simply:
    public final void addChangeListener(ChangeListener l) {}
    public final void removeChangeListener(ChangeListener l) {}
    
    // helper methods, finds mainclass's FileObject
    private FileObject getMainClassFO (FileObject sourcesRoot, String mainClass) {
        // replace '.' with '/'
//        mainClass = mainClass.replace ('.', '/'); // NOI18N
//        
//        // ignore unvalid mainClass ???
//        
//        return sourcesRoot.getFileObject (mainClass+ ".java"); // NOI18N
        return sourcesRoot.getFileObject(mainClass);        
    }

    static String getPackageName (String displayName) {
        StringBuffer builder = new StringBuffer ();
        boolean firstLetter = true;
        for (int i=0; i< displayName.length(); i++) {
            char c = displayName.charAt(i);            
            if ((!firstLetter && Character.isJavaIdentifierPart (c)) || (firstLetter && Character.isJavaIdentifierStart(c))) {
                firstLetter = false;
                if (Character.isUpperCase(c)) {
                    c = Character.toLowerCase(c);
                }                    
                builder.append(c);
            }            
        }
        return builder.length() == 0 ? NbBundle.getMessage(NewRubyProjectWizardIterator.class,"TXT_DefaultPackageName") : builder.toString();
    }
    
//    /**
//     * Create a new application manifest file with minimal initial contents.
//     * @param dir the directory to create it in
//     * @throws IOException in case of problems
//     */
//    private static void createManifest(final FileObject dir) throws IOException {
//        FileObject manifest = dir.createData(MANIFEST_FILE);
//        FileLock lock = manifest.lock();
//        try {
//            OutputStream os = manifest.getOutputStream(lock);
//            try {
//                PrintWriter pw = new PrintWriter(os);
//                pw.println("Manifest-Version: 1.0"); // NOI18N
//                pw.println("X-COMMENT: Main-Class will be added automatically by build"); // NOI18N
//                pw.println(); // safest to end in \n\n due to JRE parsing bug
//                pw.flush();
//            } finally {
//                os.close();
//            }
//        } finally {
//            lock.releaseLock();
//        }
//    }

}
