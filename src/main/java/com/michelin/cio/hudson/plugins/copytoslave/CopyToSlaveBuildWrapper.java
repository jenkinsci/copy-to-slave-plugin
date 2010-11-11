/*
 * The MIT License
 *
 * Copyright (c) 2009-2010, Manufacture Française des Pneumatiques Michelin, Romain Seguy
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
import hudson.model.Hudson;
import hudson.model.Hudson.MasterComputer;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import java.io.IOException;
import org.apache.commons.lang.StringUtils;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public class CopyToSlaveBuildWrapper extends BuildWrapper {

    private final String includes;
    private final String excludes;
    private final boolean hudsonHomeRelative;  // HUDSON-7021

    @DataBoundConstructor
    public CopyToSlaveBuildWrapper(String includes, String excludes, boolean hudsonHomeRelative) {
        this.includes = includes;
        this.excludes = excludes;
        this.hudsonHomeRelative = hudsonHomeRelative;
    }

    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        if(StringUtils.isBlank(getIncludes())) {
            listener.fatalError(
                    "No includes have been defined for the \"Copy files to slave node before building\" option: It is mandatory to define them.");
            return null;
        }

        if(Computer.currentComputer() instanceof SlaveComputer) {
            FilePath rootFilePathOnMaster;
            if(!isHudsonHomeRelative()) {
                rootFilePathOnMaster = CopyToSlaveUtils.getProjectWorkspaceOnMaster(build, listener.getLogger());
            }
            else {  // HUDSON-7021
                rootFilePathOnMaster = Hudson.getInstance().getRootPath();
            }

            FilePath projectWorkspaceOnSlave = build.getProject().getWorkspace();

            listener.getLogger().printf("Copying '%s', excluding '%s' from '%s' on the master to '%s' on '%s'.\n",
                    getIncludes(), getExcludes(), rootFilePathOnMaster.toURI(),
                    projectWorkspaceOnSlave.toURI(), Computer.currentComputer().getNode());

            CopyToSlaveUtils.hudson5977(projectWorkspaceOnSlave); // HUDSON-6045
            rootFilePathOnMaster.copyRecursiveTo(getIncludes(), getExcludes(), projectWorkspaceOnSlave);
        }
        else if(Computer.currentComputer() instanceof MasterComputer) {
            listener.getLogger().println(
                    "The build is taking place on the master node, no copy to a slave node will take place.");
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

    public boolean isHudsonHomeRelative() {
        return hudsonHomeRelative;
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        public DescriptorImpl() {
            super(CopyToSlaveBuildWrapper.class);
        }

        public static FormValidation checkFile(AbstractProject project, String value, boolean hudsonHomeRelative) throws IOException {
            FilePath rootFilePathOnMaster;
            if(!hudsonHomeRelative) {
                rootFilePathOnMaster = CopyToSlaveUtils.getProjectWorkspaceOnMaster(project, null);
            }
            else {
                rootFilePathOnMaster = Hudson.getInstance().getRootPath();
            }
            return FilePath.validateFileMask(rootFilePathOnMaster, value);
        }

        /**
         * Validates {@link CopyToSlaveBuildWrapper#includes}
         */
        public FormValidation doCheckIncludes(@AncestorInPath AbstractProject project, @QueryParameter String value, @QueryParameter boolean hudsonHomeRelative) throws IOException {
            // hmmm, this method can be used to check if a file exists in the
            // file system, so it should be protected... but how about user
            // validation then? I have to think about that
            return checkFile(project, value, hudsonHomeRelative);
        }

        /**
         * Validates {@link CopyToSlaveBuildWrapper#excludes}.
         */
        public FormValidation doCheckExcludes(@AncestorInPath AbstractProject project, @QueryParameter String value, @QueryParameter boolean hudsonHomeRelative) throws IOException {
            // cf. comment in doCheckIncludes()
            return checkFile(project, value, hudsonHomeRelative);
        }

        @Override
        public String getDisplayName() {
            return new Localizable(ResourceBundleHolder.get(CopyToSlaveBuildWrapper.class), "DisplayName").toString();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

    }

}
