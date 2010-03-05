/*
 * The MIT License
 *
 * Copyright (c) 2010, Manufacture Française des Pneumatiques Michelin, Romain Seguy
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
import java.io.File;

/**
 * @author Romain Seguy (http://davadoc.deviantart.com)
 */
public class CopyToSlaveUtils {

    public static FilePath getProjectWorkspaceOnMaster(AbstractBuild build) {
        return getProjectWorkspaceOnMaster(build.getProject());
    }

    public static FilePath getProjectWorkspaceOnMaster(AbstractProject project) {
        // free-style projects
        if(project instanceof FreeStyleProject) {
            FreeStyleProject freeStyleProject = (FreeStyleProject) project;

            // do we use a custom workspace?
            if(freeStyleProject.getCustomWorkspace() != null && freeStyleProject.getCustomWorkspace().length() > 0) {
                return new FilePath(new File(freeStyleProject.getCustomWorkspace()));
            }
            else {
                return new FilePath(new File(freeStyleProject.getRootDir(), "workspace"));
            }
        }

        return new FilePath(new File(project.getRootDir(), "workspace"));
    }

}
