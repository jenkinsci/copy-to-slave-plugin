package org.hisilicon.plugins.copytoslave.fromzookeeper;
  
import java.io.IOException;  
import java.util.List; 
import org.apache.zookeeper.CreateMode;  
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;  
import org.apache.zookeeper.Watcher;  
import org.apache.zookeeper.ZooKeeper;  
import org.apache.zookeeper.ZooDefs.Ids;  
import static org.apache.zookeeper.ZooKeeper.States.CLOSED;
import static org.apache.zookeeper.ZooKeeper.States.CONNECTED;
import org.apache.zookeeper.data.Stat;
  
/**
 *
 * @author y00349282
 */ 
public class ZookeeperClient {  
      
    private ZooKeeper zookeeperClient;  
    
    private static final int SESSION_TIMEOUT = 2000; 
          
    private final Watcher watcher =  new Watcher() {   
        public void process(WatchedEvent event) {  
        }  
    };        
    
    /** 
     * connect the zookeeper 
     * @param zookeeperAddress 
     * @throws IOException 
     * <br>------------------------------<br> 
     */  
    public void connect(String zookeeperAddress) throws IOException {  
        zookeeperClient  = new ZooKeeper(zookeeperAddress, SESSION_TIMEOUT, watcher);  
    }  
          
    /** 
     * close the session   
     * @throws java.lang.InterruptedException
     * <br>------------------------------<br>
     */   
    public void close() throws InterruptedException {  
        zookeeperClient.close();   
    }  
       
    /** 
     * createEphemeralNode
     * @param nodePath 
     * @param nodeContent 
     * @throws org.apache.zookeeper.KeeperException
     * @throws java.lang.InterruptedException
     * <br>------------------------------<br> 
     */  
    public void createEphemeralNode(String nodePath,String nodeContent) throws KeeperException, InterruptedException {
        zookeeperClient.create(nodePath, nodeContent.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);  
    }   
      
    /** 
     * getNodeData 
     * @param nodePath 
     * @return the content fo node 
     * @throws org.apache.zookeeper.KeeperException
     * @throws java.lang.InterruptedException
     * <br>------------------------------<br> 
     */  
    public String getNodeData(String nodePath) throws KeeperException, InterruptedException {  
        String result = null;  
        byte[] bytes = zookeeperClient.getData(nodePath, null, null); 
        if(bytes != null){
            result = new String(bytes);  
        }
        return result;
    }                  
    
    /** 
     * getAllChildNode 
     * @param nodePath
     * @return all the child node on the nodePath
     * @throws org.apache.zookeeper.KeeperException
     * @throws java.lang.InterruptedException
     * <br>------------------------------<br> 
     */   
    public List<String> getAllChildNode(String nodePath) throws KeeperException, InterruptedException {               
            List<String> list = zookeeperClient.getChildren(nodePath, true);  
            return list;
    }  
    
    /** 
     * nodeIsExists 
     * @param nodePath 
     * @return 
     * @throws org.apache.zookeeper.KeeperException
     * @throws java.lang.InterruptedException
     * <br>------------------------------<br> 
     */  
    public boolean nodeIsExists(String nodePath) throws KeeperException, InterruptedException {  
        Stat stat = zookeeperClient.exists(nodePath, false);     
        return stat!=null;
    }    
    
    /** 
     * getSessionId
     * @return 
     * <br>------------------------------<br> 
     */  
    public long getSessionId(){
        return zookeeperClient.getSessionId();
    }
    
    /** 
     * isSessionClosed
     * @return 
     * <br>------------------------------<br> 
     */ 
    public boolean isSessionClosed(){
        if(zookeeperClient.getState()==CLOSED){
            return true;
        }else{
            return false;
        }
    }
    
    /** 
     * isSessionConnected
     * @return 
     * <br>------------------------------<br> 
     */ 
    public boolean isSessionConnected(){
        if(zookeeperClient.getState()==CONNECTED){
            return true;
        }else{
            return false;
        }
    }
    
}