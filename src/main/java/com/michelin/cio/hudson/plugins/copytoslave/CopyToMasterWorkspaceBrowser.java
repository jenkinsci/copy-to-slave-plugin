package com.michelin.cio.hudson.plugins.copytoslave;

import hudson.Extension;
import hudson.FilePath;
//import hudson.model.Hudson;
//import hudson.model.Hudson.MasterComputer;
//import hudson.model.*;

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
import hudson.model.Job;
import hudson.model.WorkspaceBrowser;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.model.*;

import jenkins.model.Jenkins;
import org.apache.commons.lang.ObjectUtils;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;


/**
 * Inspired from https://github.com/jenkinsci/mesos-plugin/blob/master/src/main/java/org/jenkinsci/plugins/mesos/MesosWorkspaceBrowser.java
 */
@Extension
public class CopyToMasterWorkspaceBrowser extends WorkspaceBrowser{

    private static final Logger LOGGER = Logger.getLogger(CopyToMasterWorkspaceBrowser.class.getName());

    @Override
    public FilePath getWorkspace(Job job) {
        LOGGER.info("Nodes went offline. Hence fetching workspace through master");
        if (job instanceof AbstractProject) {
            Jenkins jenkinsInstance = Hudson.getInstance();
            if(jenkinsInstance != null) {
                FilePath filePath = jenkinsInstance.getWorkspaceFor((TopLevelItem) job);
                if (filePath != null) {
                    String workspacePath = filePath.toString();
                    LOGGER.fine("Workspace Path: " + workspacePath);
                    File workspace = new File(workspacePath);
                    LOGGER.fine("Workspace exists ? " + workspace.exists());
                    if (workspace.exists()) {
                        return new FilePath(workspace);
                    }
                }
            }
        }
        return null;
    }
}