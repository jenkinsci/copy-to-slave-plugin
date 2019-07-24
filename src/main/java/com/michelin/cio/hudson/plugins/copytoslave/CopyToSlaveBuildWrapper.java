/*
 * The MIT License
 *
 * Copyright (c) 2009-2012, Manufacture Française des Pneumatiques Michelin, Romain Seguy
 * Copyright (c) 2019, Wave Computing, Inc. John McGehee
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.Hudson.MasterComputer;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Romain Seguy (http://openromain.blogspot.com)
 * @author John McGehee (http://johnnado.com)
 */
public class CopyToSlaveBuildWrapper extends BuildWrapper {

    public final static String RELATIVE_TO_HOME = "home";
    public final static String RELATIVE_TO_SOMEWHERE_ELSE = "somewhereElse";
    public final static String RELATIVE_TO_USERCONTENT = "userContent";
    public final static String RELATIVE_TO_WORKSPACE = "workspace";

    private final String includes;
    private final String excludes;
    private final boolean flatten;  // HUDSON-8220
    private final boolean includeAntExcludes; // HUDSON-8274 (partially)
    @Deprecated
    private final boolean hudsonHomeRelative; // HUDSON-7021 (as of 2011/03/01, replaced by relativeTo
                                              // and kept for backward compatibility)
    private final String relativeTo;

    @DataBoundConstructor
    public CopyToSlaveBuildWrapper(String includes, String excludes, boolean flatten, boolean includeAntExcludes, String relativeTo, boolean hudsonHomeRelative) {
        this.includes = includes;
        this.excludes = excludes;
        this.flatten = flatten;
        this.includeAntExcludes = includeAntExcludes;
        if(hudsonHomeRelative) { // backward compatibility
            this.relativeTo = RELATIVE_TO_HOME;
        }
        else if(StringUtils.isBlank(relativeTo)) {
            this.relativeTo = RELATIVE_TO_USERCONTENT;
        }
        else {
            this.relativeTo = relativeTo;
        }
        this.hudsonHomeRelative = false; // force hudsonHomeRelative to false to not use it anymore
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @SuppressFBWarnings(value="NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
            justification="TODO: Fix this after qualifying the new plugin")
    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        EnvVars env = build.getEnvironment(listener);
        env.overrideAll(build.getBuildVariables());

        if(StringUtils.isBlank(getIncludes())) {
            listener.fatalError(
                    "[copy-to-slave] No includes have been defined: It is mandatory to define them.");
            return null;
        }

        if(Computer.currentComputer() instanceof MasterComputer && RELATIVE_TO_WORKSPACE.equals(relativeTo)) {
            listener.getLogger().println(
                    "[copy-to-slave] Trying to copy files from the workspace on the master to the same workspace on the same master: No copy will take place.");
        }
        else {
            FilePath rootFilePathOnMaster;

            if(RELATIVE_TO_WORKSPACE.equals(relativeTo)) {
                rootFilePathOnMaster = CopyToSlaveUtils.getProjectWorkspaceOnMaster(build, listener.getLogger());
            }
            else if(getDescriptor().isSomewhereElseEnabled() && RELATIVE_TO_SOMEWHERE_ELSE.equals(relativeTo)) {
                rootFilePathOnMaster = new FilePath(
                        Hudson.getInstance().getChannel(),
                        env.expand(getDescriptor().getSomewhereElsePath()));
            }
            else if(getDescriptor().isRelativeToHomeEnabled() && RELATIVE_TO_HOME.equals(relativeTo)) { // JENKINS-12281
                rootFilePathOnMaster = Hudson.getInstance().getRootPath();
            }
            else {
                rootFilePathOnMaster = Hudson.getInstance().getRootPath().child("userContent");
            }

            FilePath projectWorkspaceOnSlave = build.getWorkspace();

            String includes = env.expand(getIncludes());
            String excludes = env.expand(getExcludes());

            listener.getLogger().printf("[copy-to-slave] Copying '%s', excluding %s, from '%s' on the master to '%s' on '%s'.%n",
                    includes, StringUtils.isBlank(excludes) ? "nothing" : '\'' + excludes + '\'', rootFilePathOnMaster.toURI(),
                    projectWorkspaceOnSlave.toURI(), Computer.currentComputer().getNode().getDisplayName());

            // JENKINS-7999 is fixed, so now use Jenkins FilePath instead of the
            // custom MyFilePath included in this plugin
            rootFilePathOnMaster.copyRecursiveTo(includes, excludes,
                    projectWorkspaceOnSlave);
        }

        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                // we need to return true so that the build can go on
                return true;
            }
        };
    }

    public String getIncludes() {
        return includes;
    }

    public String getExcludes() {
        return excludes;
    }

    public String getRelativeTo() {
        if(hudsonHomeRelative) { // backward compatibility
            return RELATIVE_TO_HOME;
        }
        if(StringUtils.isBlank(relativeTo)) {
            return RELATIVE_TO_USERCONTENT;
        }
        return relativeTo;
    }

    public boolean isIncludeAntExcludes() {
        return includeAntExcludes;
    }

    public boolean isFlatten() {
        return flatten;
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        private boolean relativeToHomeEnabled; // JENKINS-12281
        private boolean somewhereElseEnabled;
        private String somewhereElsePath;

        public DescriptorImpl() {
            super(CopyToSlaveBuildWrapper.class);
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            try {
                relativeToHomeEnabled = req.getSubmittedForm().getBoolean("relativeToHomeEnabled");

                somewhereElseEnabled = req.getSubmittedForm().getBoolean("somewhereElseEnabled");
                
                somewhereElsePath = req.getSubmittedForm().getString("somewhereElsePath");
                if(StringUtils.isBlank(somewhereElsePath)) {
                    somewhereElsePath = null;
                    somewhereElseEnabled = false;
                }

                save();

                return true;
            }
            catch (ServletException e) {
                return false;
            }
        }

        @Override
        public String getDisplayName() {
            return new Localizable(ResourceBundleHolder.get(CopyToSlaveBuildWrapper.class), "DisplayName").toString();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        public String getSomewhereElsePath() {
            return somewhereElsePath;
        }

        public boolean isRelativeToHomeEnabled() {
            return relativeToHomeEnabled;
        }

        public boolean isSomewhereElseEnabled() {
            return somewhereElseEnabled;
        }

    }

}
