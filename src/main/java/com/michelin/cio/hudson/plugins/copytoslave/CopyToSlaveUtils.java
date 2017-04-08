/*
 * The MIT License
 *
 * Copyright (c) 2010-2011, Manufacture Française des Pneumatiques Michelin, Romain Seguy
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
import hudson.model.TopLevelItem;
import jenkins.model.Jenkins;
import java.io.File;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public class CopyToSlaveUtils {

    public static FilePath getProjectWorkspaceOnMaster(AbstractBuild build, PrintStream logger) {
        return getProjectWorkspaceOnMaster(build, build.getProject(), logger);
    }

    public static FilePath getProjectWorkspaceOnMaster(AbstractBuild build, AbstractProject project, PrintStream logger) {
        FilePath projectWorkspaceOnMaster;

        // free-style projects
        if(project instanceof FreeStyleProject) {
            FreeStyleProject freeStyleProject = (FreeStyleProject) project;

            // do we use a custom workspace?
            if(freeStyleProject.getCustomWorkspace() != null && freeStyleProject.getCustomWorkspace().length() > 0) {
                projectWorkspaceOnMaster = new FilePath(new File(freeStyleProject.getCustomWorkspace()));
            }
            else {
                projectWorkspaceOnMaster = Jenkins.getInstance().getWorkspaceFor(freeStyleProject);
            }
        }
        else {
            // Quote separator, as String.split() takes a regex and
            // File.separator isn't a valid regex character on Windows
            final String separator = Pattern.quote(File.separator);
            String pathOnMaster = Jenkins.getInstance().getWorkspaceFor((TopLevelItem)project.getRootProject()).getRemote();
            String parts[] = build.getWorkspace().getRemote().
                    split("workspace" + separator + project.getRootProject().getName());
            if (parts.length > 1) {
                // This happens for the non free style projects (like multi configuration projects, etc)
                // So we'll just add the extra part of the path to the workspace
                pathOnMaster += parts[1];
            }
            projectWorkspaceOnMaster = new FilePath(new File(pathOnMaster));
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

    private final static Logger LOGGER = Logger.getLogger(CopyToSlaveUtils.class.getName());

}
