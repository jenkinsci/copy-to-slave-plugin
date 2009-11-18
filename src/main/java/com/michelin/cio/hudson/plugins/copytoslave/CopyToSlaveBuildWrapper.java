/*
 * The MIT License
 *
 * Copyright (c) 2009, Manufacture Française des Pneumatiques Michelin, Romain Seguy
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

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson.MasterComputer;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Romain Seguy
 * @version 1.1
 */
public class CopyToSlaveBuildWrapper extends BuildWrapper {

    private final String includes;
    private final String excludes;

    @DataBoundConstructor
    public CopyToSlaveBuildWrapper(String includes, String excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        if(Computer.currentComputer() instanceof SlaveComputer) {
            // are we really in a FreeStyleProject?
            if(build.getProject() instanceof FreeStyleProject) {
                FilePath projectWorkspaceOnMaster;
                FreeStyleProject project = (FreeStyleProject) build.getProject();

                // do we use a custom workspace?
                if(project.getCustomWorkspace() != null && project.getCustomWorkspace().length() > 0) {
                    projectWorkspaceOnMaster = new FilePath(new File(project.getCustomWorkspace()));
                }
                // no, we don't use a custom workspace: we then need to address
                // the workspace in an hard-coded way
                else {
                    projectWorkspaceOnMaster = new FilePath(new File(build.getProject().getRootDir(), "workspace"));
                }

                FilePath projectWorkspaceOnSlave = build.getProject().getWorkspace();

                LOGGER.finest("Copying '" + getIncludes()
                        + "', excluding '" + getExcludes()
                        + "' from " + projectWorkspaceOnMaster.toURI() + " on the master "
                        + "to '" + projectWorkspaceOnSlave.toURI() + "' on " + Computer.currentComputer().getNode());
                projectWorkspaceOnMaster.copyRecursiveTo(getIncludes(), getExcludes(), projectWorkspaceOnSlave);
            }
            else {
                // normally we should NEVER get here
                LOGGER.warning(this.getClass().getName() + " has been invoked on a non-free style project: Skipping it.");
            }
        }
        else if(Computer.currentComputer() instanceof MasterComputer) {
            LOGGER.finest("The build is taking place on the master node, no copy to a slave node will take place.");
        }

        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                // we need to return true so that the build can go on
                return true;
            }
        };
    }

    @Override
    public Environment setUp(Build build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        return setUp(build, launcher, listener);
    }

    public String getIncludes() {
        return includes;
    }

    public String getExcludes() {
        return excludes;
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        public DescriptorImpl() {
            super(CopyToSlaveBuildWrapper.class);
        }

        private FormValidation checkFile(FreeStyleProject project, String value) throws IOException {
            FilePath projectWorkspaceOnMaster;

            // do we use a custom workspace?
            if(project.getCustomWorkspace() != null && project.getCustomWorkspace().length() > 0) {
                projectWorkspaceOnMaster = new FilePath(new File(project.getCustomWorkspace()));
            }
            else {
                projectWorkspaceOnMaster = new FilePath(new File(project.getRootDir(), "workspace"));
            }

            return FilePath.validateFileMask(projectWorkspaceOnMaster, value);
        }

        /**
         * Validates {@link CopyToSlaveBuildWrapper#includes}
         */
        public FormValidation doCheckIncludes(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            return checkFile((FreeStyleProject) project, value);
        }

        /**
         * Validates {@link CopyToSlaveBuildWrapper#excludes}.
         */
        public FormValidation doCheckExcludes(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            return checkFile((FreeStyleProject) project, value);
        }

        @Override
        public String getDisplayName() {
            // displayed in the project's configuration page
            return ResourceBundleHolder.get(CopyToSlaveBuildWrapper.class).format("DisplayName");
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            // I get an error when compiling if using instanceof ==> that's why
            // I use this not-very-clean stuff below
            if(item.getClass().getName().equals(FreeStyleProject.class.getName())) {
                return true;
            }
            return false;
        }

        @Override
        public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(CopyToSlaveBuildWrapper.class, formData);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CopyToSlaveBuildWrapper.class.getName());

}
