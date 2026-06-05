/**
 * Provides API models and protobuf definitions for the LSM Plus key-value store.
 * <p>
 * This package contains the core data models used across all LSM Plus components:
 * </p>
 * 
 * <h2>Core Models</h2>
 * <ul>
 *   <li>{@link org.hyperkv.lsmplus.api.model.IndexKey} - Sortable key representation</li>
 *   <li>{@link org.hyperkv.lsmplus.api.model.IndexValue} - Value representation with tombstone support</li>
 * </ul>
 * 
 * <h2>Protobuf Definitions</h2>
 * <p>
 * All protobuf definitions are located in {@code src/main/proto/} and include:
 * </p>
 * <ul>
 *   <li>{@code common.proto} - Common enumeration types (KeyType, ValueType, OperationType, etc.)</li>
 *   <li>{@code keyvalue.proto} - Key and value message definitions</li>
 *   <li>{@code journal.proto} - Journal entry and replay point definitions</li>
 *   <li>{@code page.proto} - B+Tree page definitions</li>
 *   <li>{@code metadata.proto} - Chunk, backup, and tree metadata definitions</li>
 * </ul>
 * 
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Type-safe key and value models with validation</li>
 *   <li>Efficient protobuf serialization (30-50% smaller than JSON)</li>
 *   <li>Support for ordered byte comparison and custom key formats</li>
 *   <li>Tombstone markers for deletions in LSM tree</li>
 *   <li>Backward compatibility across versions</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * <p>
 * All model classes in this package are immutable and thread-safe.
 * </p>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create key-value pair
 * IndexKey key = IndexKey.orderedBytes("user:12345".getBytes());
 * IndexValue value = IndexValue.normal("data".getBytes());
 * 
 * // Serialize to protobuf
 * KeyProto keyProto = key.toProto();
 * ValueProto valueProto = value.toProto();
 * 
 * // Deserialize from protobuf
 * IndexKey restoredKey = IndexKey.fromProto(keyProto);
 * IndexValue restoredValue = IndexValue.fromProto(valueProto);
 * }</pre>
 * 
 * @see org.hyperkv.lsmplus.api.model.IndexKey
 * @see org.hyperkv.lsmplus.api.model.IndexValue
 */
package org.hyperkv.lsmplus.api.model;
