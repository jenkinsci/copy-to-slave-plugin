package org.hisilicon.plugins.copytoslave.fromzookeeper;

/**
 *
 * @author y00349282
 */
import hudson.FilePath;
import hudson.model.BuildListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.zookeeper.KeeperException;

public class CopyFile {

    /**
     * copy the files from zookeeper to the workspace where job is building on the slave
     *
     * @param zookeeperAddress 
     * @param zookeeperBasePath 
     * @param includes the content entered in includes textbox on the job configuration page
     * @param projectWorkspaceOnSlave 
     * @param isFlatten 
     * @param listener 
     * @return
     */
    public static boolean zkFilesCopyToSlave(String zookeeperAddress, String zookeeperBasePath, String includes, FilePath projectWorkspaceOnSlave, boolean isFlatten, BuildListener listener) {

        //preprocess the zookeeperBasePath entered on system setting page:consider the special suitcase that only the "/" was entered
        zookeeperBasePath = preprocessZookeeperBasePath(zookeeperBasePath);
        
        ZookeeperClient zookeeperClient = new ZookeeperClient();
        try {
            zookeeperClient.connect(zookeeperAddress);
            //according to the case,execute the operation of copying files from zookeeper to slave
            //eg./A/B或/A/B*
            if (isMultiFiles(includes) == null) {               
                if (!needWildcardMatch(includes)) {
                    copyOperation(zookeeperClient,zookeeperBasePath, includes, projectWorkspaceOnSlave, isFlatten, listener);
                } else {        
                    String matchedIncludes = preprocessIncludes(zookeeperClient, zookeeperBasePath, includes, listener);//通配符匹配,输出被匹配完的路径
                    String includesFiles[] = matchedIncludes.split(",");
                    for (String includesFile : includesFiles) {
                        copyOperation(zookeeperClient,zookeeperBasePath, includesFile, projectWorkspaceOnSlave, isFlatten, listener);
                    }
                }
            } else {//eg./A/B,/A/B/C       
                if (!needWildcardMatch(includes)) {
                    String includesFiles[] = includes.split(",");
                    for (String includesFile : includesFiles) {
                        copyOperation(zookeeperClient,zookeeperBasePath, includesFile, projectWorkspaceOnSlave, isFlatten, listener);
                    }
                } else {     
                    String includesFiles[] = includes.split(",");
                    for (String includesFile : includesFiles) {
                        String matchedIncludesFile = preprocessIncludes(zookeeperClient, zookeeperBasePath, includesFile, listener);
                        String matchedIncludesFileItems[] = matchedIncludesFile.split(",");
                        for (String includesFilesItem : matchedIncludesFileItems) {
                            copyOperation(zookeeperClient,zookeeperBasePath, includesFilesItem, projectWorkspaceOnSlave, isFlatten, listener);
                        }
                    }
                }
            }      
            return true;
        } catch (IOException ex) {
            listener.fatalError("[copy-to-slave] " + ex.getMessage());
            return false;
        } catch (InterruptedException ex) {
            listener.fatalError("[copy-to-slave] " + ex.getMessage());
            return false;
        } catch (KeeperException ex) {
            listener.fatalError("[copy-to-slave] " + ex.getMessage());
            return false;
        }finally{ 
            //check whether the zookeeper session is closed or no,if it was not closed,try to close it.
            if(zookeeperClient.isSessionConnected()){
                listener.getLogger().println("[Zookeeper Session Open] -> " + zookeeperClient.getSessionId());
                try {
                    zookeeperClient.close();
                } catch (InterruptedException ex) {
                    listener.fatalError("[copy-to-slave] " + ex.getMessage()+" During closing the zookeeper session!");
                }
                listener.getLogger().println("[Zookeeper Session Closed] -> " + zookeeperClient.getSessionId());
            }
        }
    }

    /**
     * the operation of copying data from zookeeper to slave
     *
     * @param zookeeperClient 
     * @param zookeeperBasePath 
     * @param includes the content entered in includes textbox on the job configuration page
     * @param projectWorkspaceOnSlave 
     * @param isFlatten 
     * @param listener 
     * @throws org.apache.zookeeper.KeeperException
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    public static void copyOperation(ZookeeperClient zookeeperClient, String zookeeperBasePath, String includes, FilePath projectWorkspaceOnSlave, boolean isFlatten, BuildListener listener) throws KeeperException, IOException, InterruptedException {
        //to slave
        String targetFilePath = getTargetFilePath(includes, projectWorkspaceOnSlave, isFlatten);
        //from zookeeper
        String sourceFilePath = getSourceFilePath(zookeeperBasePath, includes);
        byte fileBytesContent[] = zookeeperClient.getNodeData(sourceFilePath).getBytes();
        bytesToFile(targetFilePath, fileBytesContent, includes, projectWorkspaceOnSlave, listener);
    }

    /**
     * copy the data to files on slave 
     *
     * @param targetFilePath 
     * @param fileBytesContent 
     * @param includes the content entered in includes textbox on the job configuration page
     * @param projectWorkspaceOnSlave 
     * @param listener 
     * @return if the files were copied successfully,it return true,or else return false
     * @throws java.io.FileNotFoundException
     * @throws java.io.IOException
     */
    public static boolean bytesToFile(String targetFilePath, byte fileBytesContent[], String includes, FilePath projectWorkspaceOnSlave, BuildListener listener) throws IOException {
        try {
            FilePath fileOnSlavePath = new FilePath(projectWorkspaceOnSlave.getChannel(), targetFilePath);
            fileOnSlavePath.touch(System.currentTimeMillis());
            fileOnSlavePath.copyFrom(new ByteArrayInputStream(fileBytesContent));
            return true;
        } catch (IOException ex) {
            listener.fatalError("[copy-to-slave] " + ex.getMessage());
            listener.fatalError("[copy-to-slave] " + includes + " failed to be copied");
            return false;
        } catch (InterruptedException ex) {
            listener.fatalError("[copy-to-slave] " + ex.getMessage());
            listener.fatalError("[copy-to-slave] " + includes + " failed to be copied");
            return false;
        }
    }

    /**
     * needWildcardMatch
     *
     * @param includes the content entered in includes textbox on the job configuration page
     * @return if it needs to execute wildcard matching,it returns true,or else returns false
     */
    public static boolean needWildcardMatch(String includes) {
        if (includes.contains("*") | includes.contains("?")) {
            return true;
        }
        return false;
    }

    /**
     * get the base directory fo includes
     *
     * @param eachInclude 
     * @return the base directory fo includes
     */
    public static String getBaseDir(String eachInclude) {
        if (eachInclude.lastIndexOf("/") > 0) {
            StringBuilder baseDir = new StringBuilder();
            if (eachInclude.startsWith("/")) {
                eachInclude = eachInclude.substring(1);
            }
            String[] dirOfIncludes = eachInclude.split("/");
            int dirTotalLevel = dirOfIncludes.length;
            int dirTempLevel = 0;
            while (dirTempLevel < (dirTotalLevel - 1)) {           
                baseDir.append("/").append(dirOfIncludes[dirTempLevel]);
                dirTempLevel++;
            }
            return baseDir.toString();
        } else {
            return "";
        }
    }

    /**
     * determine whether the input contains multifile or not
     *
     * @param includes the content entered in includes textbox on the job configuration page
     * @return if the input contains multifile,it return a array of file's path in the includes textbox, or else it returns null
     */
    public static String[] isMultiFiles(String includes) {
        if (includes.contains(",")) {
            String[] str = includes.split(",");
            return str;
        } else {
            return null;
        }
    }

    /**
     * get the target file path on slave 
     *
     * @param includes the content entered in includes textbox on the job configuration page
     * @param projectWorkspaceOnSlave 
     * @param flatten 
     * @return the target file path on slave 
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    public static String getTargetFilePath(String includes, FilePath projectWorkspaceOnSlave, boolean flatten) throws IOException, InterruptedException {
        String fileFinalPath = projectWorkspaceOnSlave.getRemote();
        String filePath = projectWorkspaceOnSlave.getRemote();
        if (!flatten) {
            //if there exists no directory of the target files,it has to be created，or else,just copy the files
            if (includes.lastIndexOf("/") > 0) {
                StringBuilder finalDirectory = new StringBuilder(filePath);
                String[] dirOfIncludes = includes.split("/");
                int dirTotalLevel = dirOfIncludes.length;
                int dirTempLevel = 0;
                while (dirTempLevel < (dirTotalLevel - 1)) {
                    finalDirectory.append("/").append(dirOfIncludes[dirTempLevel]);
                    dirTempLevel++;
                }
                //create the directory
                FilePath fileDirectory = new FilePath(projectWorkspaceOnSlave.getChannel(), finalDirectory.toString());
                fileDirectory.mkdirs();
                fileFinalPath = finalDirectory.append("/").append(dirOfIncludes[dirOfIncludes.length - 1]).toString();
            } else {
                //in order to keep user-friendly,no matter whether the path in includes starts with "/",it will be right
                if (includes.startsWith("/")) {
                    fileFinalPath = fileFinalPath + includes;
                } else {
                    fileFinalPath = fileFinalPath + "/" + includes;
                }
            }
        } else {         
            if (includes.indexOf("/") >= 0) {
                String[] dirOfIncludes = includes.split("/");
                fileFinalPath = filePath + "/" + dirOfIncludes[dirOfIncludes.length - 1];
            } else {
                fileFinalPath = fileFinalPath + "/" + includes;
            }
        }
        return fileFinalPath;
    }

    /**
     * get the source file path on zookeeper
     *
     * @param zookeeperBasePath the zookeeperBasePath entered on system setting page
     * @param includes          the path about job entered in includes textbox on the job configuration page
     * @return 
     */
    public static String getSourceFilePath(String zookeeperBasePath, String includes) {
        return includes.startsWith("/") ? (zookeeperBasePath + includes) : (zookeeperBasePath + "/" + includes);
    }

    /**
     * search the files recursively(includes the wildcardMatching)
     *
     * @param zookeeperClient 
     * @param zookeeperBasePath 
     * @param baseDirName 
     * @param targetFileName 
     * @param listener
     * @return the matched files list 
     */
    public static List<String> findFiles(ZookeeperClient zookeeperClient, String zookeeperBasePath, String baseDirName, String targetFileName, BuildListener listener) {
        List<String> matchedFileList = new ArrayList<String>();       
        List<String> allFileList;
        try {
            allFileList = zookeeperClient.getAllChildNode(zookeeperBasePath + baseDirName); 
            for (String fileName : allFileList) {
                if (wildcardMatch(targetFileName, fileName)) {                  
                    matchedFileList.add(fileName);
                }
            }
        } catch (KeeperException ex) {
            listener.fatalError("[copy-to-slave] " + ex.getMessage());
        } catch (InterruptedException ex) {
            listener.fatalError("[copy-to-slave] " + ex.getMessage());
        }
        return matchedFileList;
    }

    /**
     * wildcardMatch (*，？)
     *
     * @param pattern the pattern 
     * @param str the string to be matched
     * @return if it matched successfully,it returns true,or else it returns false
     */
    public static boolean wildcardMatch(String pattern, String str) {
        int patternLength = pattern.length();
        int strLength = str.length();
        int strIndex = 0;
        char ch;
        for (int patternIndex = 0; patternIndex < patternLength; patternIndex++) {
            ch = pattern.charAt(patternIndex);
            if (ch == '*') {                     
                while (strIndex < strLength) {
                    if (wildcardMatch(pattern.substring(patternIndex + 1), str.substring(strIndex))) {
                        return true;
                    }
                    strIndex++;
                }
            } else if (ch == '?') {                    
                strIndex++;
                if (strIndex > strLength) {  
                    return false;
                }
            } else {
                if ((strIndex >= strLength) || (ch != str.charAt(strIndex))) {
                    return false;
                }
                strIndex++;
            }
        }
        return (strIndex == strLength);
    }

    /**   
     * preprocess the zookeeperBasePath entered on system setting page:consider the special suitcase that only the "/" was entered
     * 
     * @param zookeeperBasePath 
     * @return 
     */    
     public static String preprocessZookeeperBasePath(String zookeeperBasePath){
         if(zookeeperBasePath.equals("/")){
             return "";
         }else{
             return zookeeperBasePath;
         }
     }
    
    /**
     *  preprocess the path about job entered in includes textbox on the job configuration page
     *
     * @param zookeeperClient 
     * @param zookeeperBasePath 
     * @param includesFile 
     * @param listener
     * @return the complete path about job entered in includes textbox
     */
    public static String preprocessIncludes(ZookeeperClient zookeeperClient, String zookeeperBasePath, String includesFile, BuildListener listener) {
        String baseDirName = getBaseDir(includesFile);
        String targetFileName = getTargetFileName(includesFile);
        if (needWildcardMatch(targetFileName)) {          
            List<String> matchIncludesFileList = findFiles(zookeeperClient, zookeeperBasePath, baseDirName, targetFileName, listener);
            StringBuilder IncludesFileTemp = new StringBuilder();
            for (String matchIncludesFile : matchIncludesFileList) {
                if (baseDirName.isEmpty()) {
                    IncludesFileTemp.append(matchIncludesFile).append(",");
                } else {
                    IncludesFileTemp.append(baseDirName).append("/").append(matchIncludesFile).append(",");
                }
            }
            includesFile = IncludesFileTemp.toString();
        }
        return includesFile;
    }
    
    /**
     * get the file name of file path
     *
     * @param eachInclude the individual item of includes
     * @return the file name of file path
     */
    public static String getTargetFileName(String eachInclude) {
        String[] dirOfEachInclude = eachInclude.split("/");
        return dirOfEachInclude[dirOfEachInclude.length - 1];
    }
}
