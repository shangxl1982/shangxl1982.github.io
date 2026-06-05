package org.hyperkv.lsmplus.tools;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.bplustree.PageCapacityConfig;
import org.hyperkv.lsmplus.core.KVStore;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class DemoDataGenerator {

    private static final int TOTAL_KEYS = 50000;
    private static final int VERSIONS = 1;

    static void main(String[] args) throws Exception {
        File dataDir;
        
        if (args.length > 0) {
            dataDir = new File(args[0]);
        } else {
            dataDir = new File("demo-kvstore");
        }
        
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        System.out.println("================================================================================");
        System.out.println("HyperKVStore Demo Data Generator");
        System.out.println("================================================================================");
        System.out.println();
        System.out.println("Data Directory: " + dataDir.getAbsolutePath());
        System.out.println("Total Keys per Version: " + TOTAL_KEYS);
        System.out.println("Target Versions: " + VERSIONS);
        System.out.println();

        for (int version = 1; version <= VERSIONS; version++) {
            System.out.println("--- Version " + version + " ---");
            
            KVStore store = new KVStore(dataDir, null, null, 2*1024*1024, 2, PageCapacityConfig.sizeBased(4096,8192));
            store.start();
            
            System.out.println("Inserting " + TOTAL_KEYS + " key-values...");
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < TOTAL_KEYS; i++) {
                String keyStr = "key-" + version + "-" + UUID.randomUUID();
                String valueStr = "value-" + version + "-" + String.format("%05d", i) + "-" + "x".repeat(500);
                
                IndexKey key = IndexKey.orderedBytes(keyStr.getBytes());
                IndexValue value = IndexValue.normal(valueStr.getBytes());
                
                store.put(key, value);
                
                if ((i + 1) % 2000 == 0) {
                    System.out.println("  Inserted " + (i + 1) + " entries...");
                }
            }
            
            long insertTime = System.currentTimeMillis() - startTime;
            System.out.println("Insert completed in " + insertTime + "ms");

            List<IndexKey> keys = store.getMemoryTableManager().getActiveTable().getData().entrySet().stream().map(entry -> entry.getKey()).collect(Collectors.toList());
            System.out.println("keys : " + keys);
            System.out.println("Sealing active table...");
            store.sealActiveTable();
            
            System.out.println("Dumping to disk...");
            startTime = System.currentTimeMillis();
            store.dump();
            long dumpTime = System.currentTimeMillis() - startTime;
            System.out.println("Dump completed in " + dumpTime + "ms");
            
            System.out.println("Closing store...");
            store.shutdown();
            
            System.out.println("Version " + version + " completed");
            System.out.println();
        }

        System.out.println("================================================================================");
        System.out.println("Data Generation Complete");
        System.out.println("================================================================================");
        System.out.println();
        System.out.println("Generated " + VERSIONS + " versions with " + TOTAL_KEYS + " keys each");
        System.out.println("Total keys: " + (VERSIONS * TOTAL_KEYS));
        System.out.println();
        System.out.println("Running diagnostic tool...");
        System.out.println();

        DiagnosticTool.main(new String[]{"tree-meta", dataDir.getAbsolutePath()});

        System.out.println();
        System.out.println("Data directory: " + dataDir.getAbsolutePath());
        System.out.println("You can run: java -jar tools.jar all " + dataDir.getAbsolutePath());
    }
}
