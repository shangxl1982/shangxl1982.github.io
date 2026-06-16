# Columnar Storage

## Apache Parquet
- Row group, column chunk, page structure
- Dremel encoding (repetition & definition levels)
- Encoding: dictionary, RLE, delta, plain
- Predicate pushdown & statistics filtering
- Page index & column index

## ORC Format
- Stripe, stream structure
- Indexes: light-weight min/max indexes
- Bloom filters at stripe level

## Columnar vs Row-based
- Scan vs point lookup trade-offs
- Compression ratio comparison
- Vectorized processing advantages

## Advanced Topics
- Nested data handling in columnar formats
- Late materialization
- Zone maps & data skipping
