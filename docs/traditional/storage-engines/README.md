# Storage Engines

## KV Store
- RocksDB, LevelDB, Badger
- WiscKey: separating keys from values
- Bloom filters & point lookups

## LSM-Tree Design & Compaction
- Leveled vs Tiered vs Size-Tiered compaction
- Write amplification, read amplification, space amplification
- Compaction strategies: Level, Universal, FIFO
- Merge iterators & seeking

## Data Placement & Layout
- Data locality & cache efficiency
- Partitioning: range, hash, consistent hashing
- Data skew handling
- Tiered storage: hot/warm/cold

## B+ Tree vs LSM-Tree
- Read/write performance characteristics
- SSD optimization considerations
- Hybrid approaches

## WAL (Write-Ahead Logging)
- Crash recovery mechanisms
- Group commit optimization
