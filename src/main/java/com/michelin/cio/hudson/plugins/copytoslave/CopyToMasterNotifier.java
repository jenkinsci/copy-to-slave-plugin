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
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Hudson.MasterComputer;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.logging.Logger;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Romain Seguy (http://davadoc.deviantart.com)
 */
public class CopyToMasterNotifier extends Notifier {

    private final String includes;
    private final String excludes;

    @DataBoundConstructor
    public CopyToMasterNotifier(String includes, String excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if(Computer.currentComputer() instanceof SlaveComputer) {
            FilePath projectWorkspaceOnMaster = CopyToSlaveUtils.getProjectWorkspaceOnMaster(build);
            FilePath projectWorkspaceOnSlave = build.getProject().getWorkspace();

            LOGGER.finest("Copying '" + getIncludes()
                    + "', excluding '" + getExcludes()
                    + "' from " + projectWorkspaceOnSlave.toURI() + "' on " + Computer.currentComputer().getNode()
                    + "to '" + projectWorkspaceOnMaster.toURI() + " on the master.");
            projectWorkspaceOnSlave.copyRecursiveTo(getIncludes(), getExcludes(), projectWorkspaceOnMaster);
        }
        else if(Computer.currentComputer() instanceof MasterComputer) {
            LOGGER.finest("The build is taking place on the master node, no copy back to the master will take place.");
        }

        return true;
    }

    public String getIncludes() {
        return includes;
    }

    public String getExcludes() {
        return excludes;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            super(CopyToMasterNotifier.class);
        }

        @Override
        public String getDisplayName() {
            return new Localizable(ResourceBundleHolder.get(CopyToMasterNotifier.class), "DisplayName").toString();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> item) {
            return true;
        }

        /**
         * Validates {@link CopyToSlaveBuildWrapper#includes}
         */
        public FormValidation doCheckIncludes(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            return CopyToSlaveBuildWrapper.DescriptorImpl.checkFile(project, value);
        }

        /**
         * Validates {@link CopyToSlaveBuildWrapper#excludes}.
         */
        public FormValidation doCheckExcludes(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            return CopyToSlaveBuildWrapper.DescriptorImpl.checkFile(project, value);
        }

    }

    private static final Logger LOGGER = Logger.getLogger(CopyToMasterNotifier.class.getName());

}
