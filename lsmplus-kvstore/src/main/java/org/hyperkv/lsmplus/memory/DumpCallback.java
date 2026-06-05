package org.hyperkv.lsmplus.memory;

@FunctionalInterface
public interface DumpCallback {
    void onTableSealed(int sealedTableCount);
}
