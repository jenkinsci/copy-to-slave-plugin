/*
 * The MIT License
 *
 * Copyright (c) 2009-2011, Manufacture Française des Pneumatiques Michelin, Romain Seguy
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

import hudson.EnvVars;
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
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import java.io.File;
import java.io.IOException;
import org.apache.commons.lang.StringUtils;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public class CopyToMasterNotifier extends Notifier {

    private final String includes;
    private final String excludes;
    private final boolean overrideDestinationFolder;
    private final String destinationFolder;
    private final boolean runAfterResultFinalised;

    @DataBoundConstructor
    public CopyToMasterNotifier(String includes, String excludes, boolean overrideDestinationFolder, String destinationFolder, boolean runAfterResultFinalised) {
        this.includes = includes;
        this.excludes = excludes;
        this.overrideDestinationFolder = overrideDestinationFolder;
        this.destinationFolder = destinationFolder;
        this.runAfterResultFinalised = runAfterResultFinalised;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return getRunAfterResultFinalised();
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        EnvVars env = build.getEnvironment(listener);
        env.overrideAll(build.getBuildVariables());

        if(Computer.currentComputer() instanceof SlaveComputer) {
            FilePath destinationFilePath;
            if(isOverrideDestinationFolder() && StringUtils.isNotBlank(getDestinationFolder())) {
                destinationFilePath = new FilePath(new File(env.expand(getDestinationFolder())));
            }
            else {
                destinationFilePath = CopyToSlaveUtils.getProjectWorkspaceOnMaster(build, listener.getLogger());
            }

            FilePath projectWorkspaceOnSlave = build.getProject().getWorkspace();

            String includes = env.expand(getIncludes());
            String excludes = env.expand(getExcludes());

            listener.getLogger().printf("[copy-to-slave] Copying '%s', excluding %s, from '%s' on '%s' to '%s' on the master.\n",
                    includes, StringUtils.isBlank(excludes) ? "nothing" : '\'' + excludes + '\'', projectWorkspaceOnSlave.toURI(),
                    Computer.currentComputer().getNode(), destinationFilePath.toURI());

            projectWorkspaceOnSlave.copyRecursiveTo(includes, excludes, destinationFilePath);
        }
        else if(Computer.currentComputer() instanceof MasterComputer) {
            listener.getLogger().println(
                    "[copy-to-slave] The build is taking place on the master node, no copy back to the master will take place.");
        }

        return true;
    }

    public String getIncludes() {
        return includes;
    }

    public String getExcludes() {
        return excludes;
    }

    public String getDestinationFolder() {
        return destinationFolder;
    }

    public boolean isOverrideDestinationFolder() {
        return overrideDestinationFolder;
    }
    
    public boolean getRunAfterResultFinalised() {
        return runAfterResultFinalised;
    }
    
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
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

    }

}
