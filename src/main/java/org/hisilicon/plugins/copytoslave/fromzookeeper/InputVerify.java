package org.hisilicon.plugins.copytoslave.fromzookeeper;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author y00349282
 */
public class InputVerify {
    
    public static boolean isIp(String ipAddress){ //0.0.0.0 or 255.255.255.255 cannot to been filtered   
        String ipPattern = "((([1-9]?|1\\d)\\d|2([0-4]\\d|5[0-5]))\\.){3}(([1-9]?|1\\d)\\d|2([0-4]\\d|5[0-5]))";
               
        Pattern pattern = Pattern.compile(ipPattern);   
        Matcher matcher = pattern.matcher(ipAddress);   

        return matcher.matches();   
    }  
    
    public static boolean isPort(String portNumber){//port number 0~65535
        String portPattern = "([0-9]|[1-9]\\d|[1-9]\\d{2}|[1-9]\\d{3}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])";
        Pattern pattern = Pattern.compile(portPattern);   
        Matcher matcher = pattern.matcher(portNumber);   
        return matcher.matches();   
    }
    
    public static boolean isLegalZkAddress(String zookeeperAddress){ 
        if(zookeeperAddress==null){
            return false;
        }
        String [] zkIpPort = zookeeperAddress.split(",");
        for(String zkIpPortItem:zkIpPort){
            String[] IpPort = zkIpPortItem.split(":");
            if(IpPort.length!=2){//not IP:PORT format
                return false;
            }
            if(!isIp(IpPort[0])|!isPort(IpPort[1])){
                return false;
            }
        }
        return true;
    }
    
    public static boolean isLegalZkPath(String zookeeperBasePath){ //eg."/" or /A/B
        if(zookeeperBasePath.equals("/")){
            return true;
        }
        String zkPathPattern = "(\\/\\w+)+";            
        Pattern pattern = Pattern.compile(zkPathPattern);   
        Matcher matcher = pattern.matcher(zookeeperBasePath);   
        return matcher.matches();   
    }  
    
    public static boolean isLegalIncludesZkPath(String includesPath){ //eg./A/B or A/B
        String zkPathPattern = "(\\/\\w+)+";
        Pattern pattern = Pattern.compile(zkPathPattern); 
        if(includesPath.contains("/")){
            int index = includesPath.indexOf("/");
            Matcher matcher = pattern.matcher(includesPath.substring(index));   
            return matcher.matches();  
        }
        return true;            
    } 
}
