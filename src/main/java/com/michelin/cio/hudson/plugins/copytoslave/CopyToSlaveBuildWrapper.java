/*
 * The MIT License
 *
 * Copyright (c) 2009-2012, Manufacture Française des Pneumatiques Michelin, Romain Seguy
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
import hudson.model.Build;
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
import org.hisilicon.plugins.copytoslave.fromzookeeper.CopyFile;
import org.hisilicon.plugins.copytoslave.fromzookeeper.InputVerify;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public class CopyToSlaveBuildWrapper extends BuildWrapper {

    public final static String RELATIVE_TO_HOME = "home";
    public final static String RELATIVE_TO_SOMEWHERE_ELSE = "somewhereElse";
    public final static String RELATIVE_TO_USERCONTENT = "userContent";
    public final static String RELATIVE_TO_WORKSPACE = "workspace";
    public final static String RELATIVE_TO_ZOOKEEPER = "zookeeper";

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
        // Hisilicon Extend
        // If the zookeeperEnabled has been selected,then copy files from zookeeper to slave
        else if(getDescriptor().isZookeeperEnabled() && RELATIVE_TO_ZOOKEEPER.equals(relativeTo)) {            
            //the path of workspace on slave
            FilePath projectWorkspaceOnSlave = build.getWorkspace();
            //the base path of source files on zookeeper
            String zookeeperBasePath = getDescriptor().getZookeeperPath();
            //the address of zookeeper
            String zookeeprAddress=getDescriptor().getZookeeperAddress();
            
            //1、preprocess the arguments of input
            if(!InputVerify.isLegalZkPath(zookeeperBasePath)){
                listener.fatalError("[copy-to-slave] Please check zookeeperBasePath you enter on the set page of system ："+zookeeperBasePath);
                return null;
            }
            if(!InputVerify.isLegalZkAddress(zookeeprAddress)){
                listener.getLogger().println("[copy-to-slave] Please check zookeeprAddress you enter on the set page of system ："+zookeeprAddress);
                return null;
            }
            
            //2、the opration of copying the files
            listener.getLogger().printf("[copy-to-slave] Copying '%s', excluding %s, from '%s' on the zookeeper to '%s' on '%s'.\n",
             includes, StringUtils.isBlank(excludes) ? "nothing" : '\'' + excludes + '\'', env.expand(zookeeperBasePath),
             projectWorkspaceOnSlave.getRemote(), Computer.currentComputer().getNode().getDisplayName());
            
            boolean flag =  CopyFile.zkFilesCopyToSlave(zookeeprAddress,env.expand(zookeeperBasePath),includes,projectWorkspaceOnSlave,isFlatten(),listener);
            if(flag){
                listener.getLogger().println("[copy-to-slave] "+"All files have been copied Successfully!");
            }
            else{
                listener.getLogger().println("[copy-to-slave] "+"Some files failed to be copied!");
                return null;//if some files failed to be copied,it was taken as failure of this build 
            }              
        }
        else {
            FilePath  rootFilePathOnMaster;

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
            
            listener.getLogger().printf("[copy-to-slave] Copying '%s', excluding %s, from '%s' on the master to '%s' on '%s'.\n",
                    includes, StringUtils.isBlank(excludes) ? "nothing" : '\'' + excludes + '\'', rootFilePathOnMaster.toURI(),
                    projectWorkspaceOnSlave.toURI(), Computer.currentComputer().getNode().getDisplayName());
            // HUDSON-7999
            MyFilePath.copyRecursiveTo(
                    rootFilePathOnMaster,
                    includes,
                    excludes,
                    isFlatten(), isIncludeAntExcludes(), projectWorkspaceOnSlave);     

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
        
        private boolean zookeeperEnabled;// Hisilicon Extension
        private String zookeeperAddress;// Hisilicon Extension zookeeper地址
        private String zookeeperPath;// Hisilicon Extension zookeeper上节点路径

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
                
                //get the status of the "zookeeperEnabled" radio button
                zookeeperEnabled = req.getSubmittedForm().getBoolean("zookeeperEnabled");
                
                //get the content of the "zookeeperAddress" and "zookeeperPath" textbox
                zookeeperAddress = req.getSubmittedForm().getString("zookeeperAddress");
                zookeeperPath = req.getSubmittedForm().getString("zookeeperPath");
 
                if(StringUtils.isBlank(zookeeperPath)|StringUtils.isBlank(zookeeperAddress)) {
                    zookeeperAddress = null;
                    zookeeperPath = null;
                    zookeeperEnabled = false;
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
        
        public boolean isZookeeperEnabled() {
            return zookeeperEnabled;
        }

        public String getZookeeperAddress() {
        	return zookeeperAddress;
        }
        
        public String getZookeeperPath() {
        	return zookeeperPath;
        }
        
    }

}
