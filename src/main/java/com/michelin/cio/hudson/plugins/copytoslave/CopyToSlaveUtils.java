/*
 * The MIT License
 *
 * Copyright (c) 2010, Manufacture Française des Pneumatiques Michelin, Romain Seguy
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Alan Harder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.michelin.cio.hudson.plugins.copytoslave;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public class CopyToSlaveUtils {

    public static FilePath getProjectWorkspaceOnMaster(AbstractBuild build, PrintStream logger) {
        return getProjectWorkspaceOnMaster(build.getProject(), logger);
    }

    public static FilePath getProjectWorkspaceOnMaster(AbstractProject project, PrintStream logger) {
        FilePath projectWorkspaceOnMaster;

        // free-style projects
        if(project instanceof FreeStyleProject) {
            FreeStyleProject freeStyleProject = (FreeStyleProject) project;

            // do we use a custom workspace?
            if(freeStyleProject.getCustomWorkspace() != null && freeStyleProject.getCustomWorkspace().length() > 0) {
                projectWorkspaceOnMaster = new FilePath(new File(freeStyleProject.getCustomWorkspace()));
            }
            else {
                projectWorkspaceOnMaster = new FilePath(new File(freeStyleProject.getRootDir(), "workspace"));
            }
        }
        else {
            projectWorkspaceOnMaster = new FilePath(new File(project.getRootDir(), "workspace"));
        }

        try {
            // create the workspace if it doesn't exist yet
            projectWorkspaceOnMaster.mkdirs();
        }
        catch (Exception e) {
            if(logger != null) {
                logger.println("An exception occured while creating " + projectWorkspaceOnMaster.getName() + ": " + e);
            }
            LOGGER.log(Level.SEVERE, "An exception occured while creating " + projectWorkspaceOnMaster.getName(), e);
        }

        return projectWorkspaceOnMaster;
    }

    /**
     * Workaround for HUDSON-5977.
     *
     * <p>The code of this method comes from the copyartifact plugin by Alan Harder,
     * more preciselyt from {@code hudson.plugins.copyartifact.CopyArtifact.perform()}.
     * Cf. MIT License header.</p>
     */
    public static void hudson5977(FilePath targetDir) throws IOException, InterruptedException {
        // Workaround for HUDSON-5977.. this block can be removed whenever
        // copyartifact plugin raises its minimum Hudson version to whatever
        // release fixes #5977.
        // Make a call to copy a small file, to get all class-loading to happen.
        // When we copy the real stuff there won't be any classloader requests
        // coming the other direction, which due to full-buffer-deadlock problem
        // can cause slave to hang.
        URL base = Hudson.getInstance().getPluginManager()
                         .getPlugin("copy-to-slave").baseResourceURL;
        if (base!=null && "file".equals(base.getProtocol())) {
            FilePath tmp = targetDir.createTempDir("copy-to-slave", ".dir");
            new FilePath(new File(base.getPath())).copyRecursiveTo("HUDSON-5977/**", tmp);
            tmp.deleteRecursive();
        }
        // End workaround
    }

    private final static Logger LOGGER = Logger.getLogger(CopyToSlaveUtils.class.getName());

}
