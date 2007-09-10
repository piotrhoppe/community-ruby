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
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2007 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.modules.ruby.railsprojects.server;

import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;
import javax.swing.AbstractAction;
import org.netbeans.modules.ruby.rubyproject.Util;
import org.netbeans.modules.ruby.rubyproject.api.RubyExecution;
import org.netbeans.modules.ruby.rubyproject.execution.DirectoryFileLocator;
import org.netbeans.api.ruby.platform.RubyInstallation;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.modules.ruby.rubyproject.execution.ExecutionDescriptor;
import org.netbeans.modules.ruby.rubyproject.execution.OutputRecognizer;
import org.openide.DialogDisplayer;
import org.openide.awt.HtmlBrowser;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileUtil;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.modules.ruby.railsprojects.RailsProject;
import org.netbeans.modules.ruby.railsprojects.ui.customizer.RailsProjectProperties;
import org.openide.ErrorManager;
import org.openide.NotifyDescriptor;

/**
 * Support for the builtin Ruby on Rails web server: WEBrick, Mongrel, Lighttpd
 *
 * This is really primitive at this point; I should talk to the people who
 * write Java web server plugins and take some pointers. Perhaps it can
 * even implement some of their APIs such that logging, runtime nodes etc.
 * all begin to work.
 * 
 * @todo When launching under JRuby, also pass in -Djruby.thread.pooling=true to the VM
 * @todo Rewrite the webrick error message which says to press Ctrl-C to cancel the process;
 *   tell the user to use the Stop button in the margin instead (somebody on nbusers asked about this)
 * 
 * @author Tor Norbye, Pavel Buzek
 */
public final class RailsServer {
    
    enum ServerType { MONGREL, LIGHTTPD, WEBRICK; }

    enum ServerStatus { NOT_STARTED, STARTING, RUNNING; }

    /** Set of currently active - in use; ports. */
    private static final Set<Integer> IN_USE_PORTS = new HashSet<Integer>();;

    private ServerStatus status = ServerStatus.NOT_STARTED;
    private ServerType serverType;
    
    /** True if server failed to start due to port conflict. */
    private boolean portConflict;
    
    /** User chosen port */
    private int originalPort;
    
    /** Actual port in use (trying other ports for ones not in use) */
    private int port = -1;
    
    private RailsProject project;
    private RubyExecution execution;
    private File dir;
    private boolean debug;
    private boolean switchToDebugMode;
    
    private Semaphore debugSemaphore;

    public RailsServer(RailsProject project) {
        this.project = project;
        dir = FileUtil.toFile(project.getProjectDirectory());
    }
    
    public synchronized void setDebug(boolean debug) {
        if (status == ServerStatus.RUNNING && !this.debug && debug) {
            switchToDebugMode = true;
        }
        this.debug = debug;
    }
    
    private void ensureRunning() {
        synchronized (RailsServer.this) {
            if (status == ServerStatus.STARTING) {
                return;
            } else if (status == ServerStatus.RUNNING) {
                if (switchToDebugMode) {
                    assert debugSemaphore == null : "startSemaphor supposed to be null";
                    debugSemaphore = new Semaphore(0);
                    switchToDebugMode = false;
                } else if (serverType == ServerType.MONGREL) {
                    // isPortInUse doesn't work for Mongrel
                    return;
                } else if (isPortInUse(port)) {
                    // Simply assume it is still the same server running
                    return;
                }
            }
        }
        if (debugSemaphore != null) {
            try {
                execution.kill();
                debugSemaphore.acquire();
                debugSemaphore = null;
            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        // Server was not started or was killed externally
        Runnable finishedAction =
            new Runnable() {
                public void run() {
                    synchronized (RailsServer.this) {
                        status = ServerStatus.NOT_STARTED;
                        IN_USE_PORTS.remove(port);
                        if (portConflict) {
                            // Failed to start due to port conflict - notify user.
                            notifyPortConflict();
                        }
                        if (debugSemaphore != null) {
                            debugSemaphore.release();
                        } else {
                            debug = false;
                        }
                    }
                }
            };

        // Start the server
        synchronized (RailsServer.this) {
            status = ServerStatus.STARTING;
        }

        portConflict = false;
        String portString = project.evaluator().getProperty(RailsProjectProperties.RAILS_PORT);
        port = 0;
        if (portString != null) {
            port = Integer.parseInt(portString);
        }
        if (port == 0) {
            port = 3000;
        }
        originalPort = port;

        while(isPortInUse(port)) {
            port++;
        }
        String projectName = project.getLookup().lookup(ProjectInformation.class).getDisplayName();
        String classPath = project.evaluator().getProperty(RailsProjectProperties.JAVAC_CLASSPATH);
        serverType = getServerType();
        String displayName = getServerTabName(serverType, projectName, port);
        String serverPath = "script" + File.separator + "server"; // NOI18N
        ExecutionDescriptor desc = new ExecutionDescriptor(displayName, dir, serverPath);
        desc.additionalArgs("--port", Integer.toString(port)); // NOI18N
        desc.postBuild(finishedAction);
        desc.classPath(classPath);
        desc.addStandardRecognizers();
        desc.addOutputRecognizer(new RailsServerRecognizer(getStartedMessage(serverType)));
        desc.frontWindow(false);
        desc.debug(debug);
        desc.fastDebugRequired(debug);
        desc.fileLocator(new DirectoryFileLocator(FileUtil.toFileObject(dir)));
        //desc.showProgress(false); // http://ruby.netbeans.org/issues/show_bug.cgi?id=109261
        desc.showSuspended(true);
        String charsetName = project.evaluator().getProperty(RailsProjectProperties.SOURCE_ENCODING);
        IN_USE_PORTS.add(port);
        execution = new RubyExecution(desc, charsetName);
        execution.run();
    }
    
    private static String getServerTabName(ServerType serverType, String projectName, int port) {
        switch (serverType) {
            case MONGREL: return NbBundle.getMessage(RailsServer.class, "MongrelTab", projectName, Integer.toString(port));
            case LIGHTTPD: return NbBundle.getMessage(RailsServer.class, "LighttpdTab", projectName, Integer.toString(port));
            case WEBRICK: 
            default:
                return NbBundle.getMessage(RailsServer.class, "WEBrickTab", projectName, Integer.toString(port));
        }
    }
    
    private static String getStartedMessage(ServerType serverType) {
        switch (serverType) {
            case MONGREL: return "** Mongrel available at "; // NOI18N
            //case LIGHTTPD: return "=> Rails application starting on ";
            case WEBRICK: 
            default:
                return "=> Rails application started on "; // NOI18N
        }
        
    }

    static String getServerName() {
        switch (getServerType()) {
            case MONGREL: return NbBundle.getMessage(RailsServer.class, "Mongrel");
            case LIGHTTPD: return NbBundle.getMessage(RailsServer.class, "Lighttpd");
            case WEBRICK: 
            default:
                return NbBundle.getMessage(RailsServer.class, "WEBrick");
        }
    }
    
    /** Figure out which server we're using */
    private static ServerType getServerType() {
        RubyInstallation install = RubyInstallation.getInstance();
        
        if (install.getVersion("mongrel") != null) { // NOI18N
            return ServerType.MONGREL;
        } else if (install.getVersion("lighttpd") != null) { // NOI18N
            return ServerType.LIGHTTPD;
        } else {
            return ServerType.WEBRICK;
        }
    }
    
    private void notifyPortConflict() {
        String message = NbBundle.getMessage(RailsServer.class, "Conflict", Integer.toString(originalPort));
        NotifyDescriptor nd =
            new NotifyDescriptor.Message(message, 
            NotifyDescriptor.Message.ERROR_MESSAGE);
        DialogDisplayer.getDefault().notify(nd);
    }

    /** Starts the server if not running and shows url.
     * @param relativeUrl the resulting url will be for example: http://localhost:3001/{relativeUrl}
     */
    public void showUrl(final String relativeUrl) {
        synchronized (RailsServer.this) {
            if (!switchToDebugMode && status == ServerStatus.RUNNING && isPortInUse(port)) {
                try {
                    URL url = new URL("http://localhost:" + port + "/" + relativeUrl); // NOI18N
                    HtmlBrowser.URLDisplayer.getDefault().showURL(url);
                } catch (MalformedURLException ex) {
                    ErrorManager.getDefault().notify(ex);
                }
                return;
            }
        }
        ensureRunning();

        String displayName = NbBundle.getMessage(RailsServer.class, "ServerStartup");
        final ProgressHandle handle =
            ProgressHandleFactory.createHandle(displayName,new Cancellable() {
                    public boolean cancel() {
                        return true;
                    }
                },
                new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                        // XXX ?
                    }
                });

        handle.start();
        handle.switchToIndeterminate();

        RequestProcessor.getDefault().post(new Runnable() {
                public void run() {
                    try {
                        // Try connecting repeatedly, up to 20 seconds, then bail
                        for (int i = 0; i < 20; i++) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ie) {
                                // Don't worry about it
                            }

                            synchronized (RailsServer.this) {
                                if (status == ServerStatus.RUNNING) {
                                    try {
                                        URL url = new URL("http://localhost:" + port + "/" + relativeUrl); // NOI18N
                                        HtmlBrowser.URLDisplayer.getDefault().showURL(url);
                                    } catch (MalformedURLException ex) {
                                        ErrorManager.getDefault().notify(ex);
                                    }

                                    return;
                                }

                                if (status == ServerStatus.NOT_STARTED) {
                                    // Server startup somehow failed...
                                    break;
                                }
                            }

                            /* My attempts to do URLConnections didn't pan out.... so just do a simple
                             * listener based scheme instead based on parsing build output with the
                             * OutputRecognizer
                                URLConnection connection = url.openConnection();
                                connection.setConnectTimeout(1000); // 1 second

                                if (connection instanceof HttpURLConnection) {
                                    HttpURLConnection c = (HttpURLConnection)connection;
                                    c.setRequestMethod("POST");
                                    c.setFollowRedirects(true);

                                    // Try connecting repeatedly, up to 20 seconds, then bail
                                    synchronized (WebrickServer.this) {
                                        if (status == ServerStatus.NOT_STARTED) {
                                            // Server startup somehow failed...
                                            break;
                                        }
                                    }

                                    try {
                                        c.connect();
                                        StatusDisplayer.getDefault()
                                                       .setStatusText("Connect attempt #" + i +
                                            " status was " + c.getResponseCode() + " : " +
                                            c.getResponseMessage() + " : " +
                                            c.getHeaderFields().toString());

                                        if (c.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                            synchronized (WebrickServer.this) {
                                                status = ServerStatus.RUNNING;
                                            }

                                            HtmlBrowser.URLDisplayer.getDefault().showURL(url);

                                            return;
                                        }

                                        // Disconnect?
                                        //c.disconnect();
                                        try {
                                            Thread.currentThread().sleep(1000);
                                        } catch (InterruptedException ie) {
                                            ; // Don't worry about it
                                        }
                                    } catch (ConnectException ce) {
                                        // wait 1 second and try again
                                        try {
                                            Thread.currentThread().sleep(1000);
                                        } catch (InterruptedException ie) {
                                            ; // Don't worry about it
                                        }
                                    }
                                }
                            */
                        }

                        StatusDisplayer.getDefault()
                                       .setStatusText(NbBundle.getMessage(RailsServer.class,
                                "NoServerFound", "http://localhost:" + port + "/" + relativeUrl));

                        //} catch (IOException ioe) {
                        //    ErrorManager.getDefault().notify(ioe);
                    } finally {
                        handle.finish();
                    }
                }
            });
    }

    /** Return true if there is an HTTP response from the port on localhost.
     * Based on tomcatint\tomcat5\src\org.netbeans.modules.tomcat5.util.Utils.java.
     */
    public static boolean isPortInUse(int port) {
        if (IN_USE_PORTS.contains(port)) {
            return true;
        }
        int timeout = 3000;
        Socket socket = new Socket();
        try {
            try {
                socket.connect(new InetSocketAddress("localhost", port), timeout); // NOI18N
                socket.setSoTimeout(timeout);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    try {
                        // request
                        out.println("GET /\n"); // NOI18N

                        // response
                        String text = in.readLine();
                        if (text == null || !text.startsWith("<!DOCTYPE")) { // NOI18N
                            return false; // not an http response
                        }
                        return true;
                    } finally {
                        in.close();
                    }
                } finally {
                    out.close();
                }
            } finally {
                socket.close();
            }
        } catch (IOException ioe) {
            return false;
        }
    }
    
    private class RailsServerRecognizer extends OutputRecognizer {
        private String startedMessage;
        
        RailsServerRecognizer(String startedMessage) {
            this.startedMessage = startedMessage;
        }

        @Override
        public ActionText processLine(String outputLine) {
            String line = outputLine;
            
            if (Util.containsAnsiColors(outputLine)) {
                line = Util.stripAnsiColors(outputLine);
            }

            // This is ugly, but my attempts to use URLConnection on the URL repeatedly
            // and check for connection.getResponseCode()==HttpURLConnection.HTTP_OK didn't
            // work - try that again later
            if (outputLine.startsWith(startedMessage)) { // NOI18N

                synchronized (RailsServer.this) {
                    status = ServerStatus.RUNNING;
                }
            } else if (outputLine.contains("in `new': Address in use (Errno::EADDRINUSE)")) { // NOI18N
                portConflict = true;
            }

            if (!line.equals(outputLine)) {
                return new ActionText(new String[] { line }, null, null, null);
            }

            return null;
        }
    }

}
