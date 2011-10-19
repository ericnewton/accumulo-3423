/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.accumulo.core.Constants;
import org.apache.accumulo.core.client.impl.HdfsZooInstance;
import org.apache.accumulo.core.conf.AccumuloConfiguration;
import org.apache.accumulo.core.trace.DistributedTrace;
import org.apache.accumulo.core.util.CachedConfiguration;
import org.apache.accumulo.core.util.Version;
import org.apache.accumulo.core.zookeeper.ZooUtil;
import org.apache.accumulo.core.zookeeper.ZooUtil.NodeExistsPolicy;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.log4j.Logger;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.xml.DOMConfigurator;

public class Accumulo {
    
    private static final Logger log = Logger.getLogger(Accumulo.class);
    private static Integer dataVersion = null;
    
    public static synchronized int getAccumuloPersistentVersion() {
        if (dataVersion != null) return dataVersion;
        
        Configuration conf = CachedConfiguration.getInstance();
        try {
            FileSystem fs = FileSystem.get(conf);
            
            FileStatus[] files = fs.listStatus(Constants.getDataVersionLocation());
            if (files == null || files.length == 0) {
                dataVersion = -1; // assume it is 0.5 or earlier
            } else {
                dataVersion = Integer.parseInt(files[0].getPath().getName());
            }
            return dataVersion;
        } catch (IOException e) {
            throw new RuntimeException("Unable to read accumulo version: an error occurred.", e);
        }
        
    }
    
    public static void enableTracing(String address, String application) {
        try {
            DistributedTrace.enable(HdfsZooInstance.getInstance(), application, address);
        } catch (Exception ex) {
            log.error("creating remote sink for trace spans", ex);
        }
    }
    
    public static void init(String application) throws UnknownHostException {
        
        System.setProperty("org.apache.accumulo.core.application", application);
        
        // setup logging for the tablet server
        if (System.getenv("ACCUMULO_HOME") == null) {
            System.err.println("ACCUMULO_HOME not set.... exiting...");
            throw new RuntimeException("ACCUMULO_HOME not set");
        }
        
        // Setup logging.
        System.setProperty("org.apache.accumulo.core.dir.home", System.getenv("ACCUMULO_HOME"));
        
        if (System.getenv("ACCUMULO_LOG_DIR") != null) System.setProperty("org.apache.accumulo.core.dir.log", System.getenv("ACCUMULO_LOG_DIR"));
        else System.setProperty("org.apache.accumulo.core.dir.log", System.getenv("ACCUMULO_HOME") + "/logs/");
        
        String localhost = InetAddress.getLocalHost().getHostName();
        System.setProperty("org.apache.accumulo.core.ip.localhost.hostname", localhost);
        
        if (System.getenv("ACCUMULO_LOG_HOST") != null) System.setProperty("org.apache.accumulo.core.host.log", System.getenv("ACCUMULO_LOG_HOST"));
        else System.setProperty("org.apache.accumulo.core.host.log", localhost);
        
        // Use a specific log config, if it exists
        String logConfig = String.format("%s/conf/%s_logger.xml", System.getenv("ACCUMULO_HOME"), application);
        if (!new File(logConfig).exists()) {
            // otherwise, use the generic config
            logConfig = String.format("%s/conf/generic_logger.xml", System.getenv("ACCUMULO_HOME"));
        }
        // Turn off messages about not being able to reach the remote logger... we protect against that.
        LogLog.setQuietMode(true);
        
        // Configure logging
        DOMConfigurator.configure(logConfig);
        
        log.info("Instance " + HdfsZooInstance.getInstance().getInstanceID());
        log.info("Data Version " + Accumulo.getAccumuloPersistentVersion());
        
        int dataVersion = Accumulo.getAccumuloPersistentVersion();
        Version codeVersion = new Version(Constants.VERSION);
        if (dataVersion != Constants.DATA_VERSION) {
            throw new RuntimeException("This version of accumulo (" + codeVersion + ") is not compatible with files stored using data version " + dataVersion);
        }
        
        checkZooKeeperStructure();
        
        TreeMap<String,String> sortedProps = new TreeMap<String,String>();
        for (Entry<String,String> entry : AccumuloConfiguration.getSystemConfiguration())
            sortedProps.put(entry.getKey(), entry.getValue());
        
        for (Entry<String,String> entry : sortedProps.entrySet())
            log.info(entry.getKey() + " = " + entry.getValue());
    }
    
    private static void checkZooKeeperStructure() {
        // Locations created in the 1.3 Initialize that are missing in 1.2
        // @TODO can be removed in 1.4
        String instanceId = HdfsZooInstance.getInstance().getInstanceID();
        try {
            String directories[] = {Constants.ZCONFIG, Constants.ZTRACERS, Constants.ZDOOMEDSERVERS};
            for (String dir : directories)
                if (!ZooUtil.exists(dir)) ZooUtil.putPersistentData(ZooUtil.getRoot(instanceId) + dir, new byte[] {}, NodeExistsPolicy.SKIP);
        } catch (Exception ex) {
            log.error("Unable to create config location in zookeeper");
        }
    }
    
    public static InetAddress getLocalAddress(String[] args) throws UnknownHostException {
        InetAddress result = InetAddress.getLocalHost();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("-a") || args[i].equals("--address")) {
                result = InetAddress.getByName(args[i + 1]);
                log.debug("Local address is: " + args[i + 1] + " (" + result.toString() + ")");
                break;
            }
        }
        return result;
    }
}
