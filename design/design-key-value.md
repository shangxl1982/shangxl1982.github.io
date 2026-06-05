# Key-Value 存储格式设计

## 1. 概述

本文档设计 Key-Value 存储格式，支持多种 key 类型和灵活的 value 结构，为 B+Tree 和 MemoryTable 提供统一的键值表达。

### 1.1 核心设计原则

- **Key 类型**：支持 ORDERED_BYTES（直接排序）和 CUSTOM（自定义排序）两种类型
- **Value 类型**：支持 NORMAL（正常值）和 TOMBSTONE（删除标记）两种类型
- **Tombstone 处理**：Tombstone 仅存在于 MemoryTable 中，不会写入 B+Tree 的 Page

```
┌─────────────────────────────────────────────────────┐
│              Key-Value 存储架构                      │
├─────────────────────────────────────────────────────┤
│                                                      │
│  MemoryTable (内存)                                 │
│  ┌───────────────────────────────────────────────┐  │
│  │  TreeMap<IndexKey, IndexValue>                │  │
│  │  - IndexValue 可能是 NORMAL 或 TOMBSTONE      │  │
│  │  - Tombstone 标记删除，不会写入 Page          │  │
│  └───────────────────────────────────────────────┘  │
│                         │                            │
│                         │ Dump                       │
│                         ▼                            │
│  B+Tree Page (持久化)                               │
│  ┌───────────────────────────────────────────────┐  │
│  │  <IndexKey, IndexValue> 对                    │  │
│  │  - 仅包含 NORMAL 类型的 IndexValue            │  │
│  │  - Tombstone 在 Dump 时物理删除               │  │
│  └───────────────────────────────────────────────┘  │
│                                                      │
└─────────────────────────────────────────────────────┘
```

## 2. Key 设计

### 2.1 Key 类型

| 类型 | 值 | 描述 |
|------|-----|------|
| ORDERED_BYTES | 0 | 有序字节，可直接用于排序 |
| CUSTOM | 1 | 自定义格式，使用 Protobuf 编码 |

### 2.2 Key 存储格式

Key 的磁盘存储格式统一使用 `KeyProto` Protobuf 消息序列化（详见 [序列化协议设计](design-serialization.md) 2.1 节）。KeyType 枚举区分 ORDERED_BYTES(0) 和 CUSTOM(1)。

```
KeyProto 消息：
┌─────────────────────────────────────────────────────┐
│  key_type: KeyType                                 │
│  - ORDERED_BYTES (0): 直接可排序的字节序列         │
│  - CUSTOM (1): 自定义格式                          │
├─────────────────────────────────────────────────────┤
│  key_data: bytes                                   │
│  - ORDERED_BYTES: 可直接用于字节序比较             │
│  - CUSTOM: 需要通过自定义比较器解析后排序          │
└─────────────────────────────────────────────────────┘
```

**字段说明**：
- **key_type**：KeyType 枚举，ORDERED_BYTES(0) 或 CUSTOM(1)
- **key_data**：bytes，key 的实际数据

### 2.3 排序规则

- **ORDERED_BYTES**：直接使用字节序进行排序
- **CUSTOM**：需要先解析为结构化数据，再根据自定义比较器排序

## 3. Value 设计

### 3.1 Value 类型

| 类型 | 值 | 描述 |
|------|-----|------|
| NORMAL | 0 | 正常值，包含实际数据 |
| TOMBSTONE | 1 | 删除标记，仅存在于 MemoryTable |

**重要说明**：
- **Tombstone 仅存在于 MemoryTable**：用于标记已删除的 key
- **Tombstone 不会写入 Page**：Dump 时，Tombstone 对应的 key 会从 B+Tree 物理删除
- **Page 中只有 NORMAL 类型**：B+Tree 的 Page 不存储删除标记

### 3.2 Value 存储格式

Value 的磁盘存储格式统一使用 `ValueProto` Protobuf 消息序列化（详见 [序列化协议设计](design-serialization.md) 2.1 节）。ValueType 枚举区分 NORMAL(0) 和 TOMBSTONE(1)。Tombstone 类型的 value_data 为空。

```
ValueProto 消息：
┌─────────────────────────────────────────────────────┐
│  value_type: ValueType                              │
│  - NORMAL (0): 正常值                               │
│  - TOMBSTONE (1): 删除标记                          │
├─────────────────────────────────────────────────────┤
│  value_data: bytes                                  │
│  - NORMAL: 实际数据                                  │
│  - TOMBSTONE: 空                                    │
└─────────────────────────────────────────────────────┘
```

**字段说明**：
- **value_type**：ValueType 枚举，NORMAL(0) 或 TOMBSTONE(1)
- **value_data**：bytes，NORMAL 时为实际数据，TOMBSTONE 时为空

**设计说明**：
- 使用 Protobuf 编码 value 数据，统一处理数据和元数据
- 调用方负责 Protobuf 消息的定义和解析
- 支持通过 Protobuf 消息结构存储多版本或其他注解信息
- Tombstone 类型不存储实际数据，仅作为删除标记

## 4. 内存数据结构

### 4.1 IndexKey

内存中表示 key 的统一接口，支持不同类型的 key。

**核心特性**：
- 支持 ORDERED_BYTES 和 CUSTOM 两种类型
- 实现 Comparable 接口，支持排序
- 提供类型判断、字节获取等方法

**伪代码**：
```
IndexKey 接口 {
    方法：获取 key 类型
    方法：获取字节数据
    方法：与其他 key 比较
    方法：计算哈希值
    方法：判断相等性
}

OrderedBytesKey 实现 {
    成员：字节数据
    比较方法：直接比较字节序列
}

CustomKey 实现 {
    成员：字节数据、自定义比较器
    比较方法：使用自定义比较器
}
```

### 4.2 IndexValue

内存中表示 value 的统一接口，支持 NORMAL 和 TOMBSTONE 两种类型。

**核心特性**：
- 封装 value 类型（NORMAL 或 TOMBSTONE）
- 封装 Protobuf 编码的 value 数据（NORMAL 类型）
- 提供类型判断方法

**伪代码**：
```
IndexValue 接口 {
    方法：获取 value 类型 (NORMAL 或 TOMBSTONE)
    方法：判断是否为 Tombstone
    方法：获取 Protobuf 编码的字节数据 (NORMAL 类型)
    方法：计算总大小
}

NormalIndexValue 实现 {
    成员：Protobuf 编码的字节数据
    方法：返回 NORMAL 类型
    方法：返回 false (不是 Tombstone)
    方法：返回 Protobuf 编码的字节数据
    方法：计算总大小（类型 + 数据长度 + 数据）
}

TombstoneIndexValue 实现 {
    成员：无数据
    方法：返回 TOMBSTONE 类型
    方法：返回 true (是 Tombstone)
    方法：返回 null (无数据)
    方法：计算总大小（仅类型字段）
}
```

**工厂方法**：
```
IndexValue.createNormal(data)  // 创建正常值
IndexValue.createTombstone()   // 创建删除标记
```

### 4.3 IndexPair

内存中表示键值对的统一接口，支持两种类型：
- `<IndexKey, IndexValue>`：用于叶页
- `<IndexKey, SegmentLocation>`：用于索引页

**伪代码**：
```
IndexPair 接口 {
    方法：获取 key
    方法：判断是否为叶页条目
    方法：判断是否为索引页条目
}

LeafPair 实现 {
    成员：IndexKey, IndexValue
    方法：返回 key
    方法：返回 value
    方法：返回 true（是叶页条目）
    方法：返回 false（不是索引页条目）
}

IndexPairImpl 实现 {
    成员：IndexKey, SegmentLocation
    方法：返回 key
    方法：返回子页位置
    方法：返回 false（不是叶页条目）
    方法：返回 true（是索引页条目）
}
```

## 5. 序列化与反序列化

### 5.1 Key 序列化

**流程**：使用 Protobuf 序列化 `KeyProto` 消息。
1. 构造 KeyProto 实例（设置 key_type 和 key_data）
2. 调用 KeyProto.toByteArray() 序列化
3. 返回序列化后的字节数组

### 5.2 Key 反序列化

**流程**：
1. 调用 KeyProto.parseFrom(bytes) 反序列化
2. 根据 key_type 创建对应的 IndexKey 实例

### 5.3 Value 序列化

**流程**：使用 Protobuf 序列化 `ValueProto` 消息。
1. 构造 ValueProto 实例（设置 value_type 和 value_data）
2. TOMBSTONE 类型：value_data 为空
3. NORMAL 类型：value_data 为实际数据
4. 调用 ValueProto.toByteArray() 序列化

### 5.4 Value 反序列化

**流程**：
1. 调用 ValueProto.parseFrom(bytes) 反序列化
2. 根据 value_type 创建 NormalIndexValue 或 TombstoneIndexValue 实例

## 6. 与其他模块的交互

### 6.1 与 Page 模块的交互

- **叶页**：存储 `<IndexKey, IndexValue>` 对，**仅包含 NORMAL 类型的 IndexValue**
- **索引页**：存储 `<IndexKey, SegmentLocation>` 对
- **Tombstone 不写入 Page**：Dump 时，Tombstone 对应的 key 从 B+Tree 物理删除

### 6.2 与 B+Tree 模块的交互

- B+Tree 使用 IndexKey 进行节点分裂和合并
- 搜索操作使用 IndexKey 的比较方法
- **B+Tree 不存储 Tombstone**：所有 Page 中的 IndexValue 都是 NORMAL 类型

### 6.3 与 MemoryTable 模块的交互

- MemoryTable 使用 IndexKey 作为 TreeMap 的键
- MemoryTable 使用 IndexValue 作为 TreeMap 的值
- **MemoryTable 存储 Tombstone**：用于标记删除操作
- 支持不同类型 key 的排序

### 6.4 Tombstone 处理流程

Tombstone 的完整处理流程分三个阶段：(1) delete 操作时在 MemoryTable 中写入 TombstoneIndexValue；(2) get 操作时检测到 Tombstone 返回 null；(3) Dump 时遍历 MemoryTable，Tombstone 条目触发 B+Tree 的物理删除，Normal 条目触发插入/更新。Tombstone 不会写入 B+Tree Page。

```
┌─────────────────────────────────────────────────────┐
│              Tombstone 处理流程                      │
├─────────────────────────────────────────────────────┤
│                                                      │
│  1. 删除操作 (delete)                               │
│     │                                                │
│     ▼                                                │
│  ┌───────────────────────────────────────────────┐  │
│  │ MemoryTable.put(key, TombstoneIndexValue)     │  │
│  │ - IndexValue 类型为 TOMBSTONE                 │  │
│  │ - 标记该 key 已删除                           │  │
│  └───────────────────────────────────────────────┘  │
│                                                      │
│  2. 读取操作 (get)                                  │
│     │                                                │
│     ▼                                                │
│  ┌───────────────────────────────────────────────┐  │
│  │ value = MemoryTable.get(key)                  │  │
│  │ if (value.isTombstone())                      │  │
│  │     return null  // 已删除                    │  │
│  └───────────────────────────────────────────────┘  │
│                                                      │
│  3. Dump 操作                                       │
│     │                                                │
│     ▼                                                │
│  ┌───────────────────────────────────────────────┐  │
│  │ for entry in MemoryTable:                     │  │
│  │     if (entry.value.isTombstone())            │  │
│  │         B+Tree.delete(entry.key)  // 物理删除│  │
│  │     else                                       │  │
│  │         B+Tree.insert(entry.key, entry.value) │  │
│  └───────────────────────────────────────────────┘  │
│                                                      │
└─────────────────────────────────────────────────────┘
```

## 7. 性能考虑

### 7.1 内存使用

- IndexKey 和 IndexValue 应尽量轻量
- 对于大 key/value，考虑使用引用而非复制

### 7.2 序列化开销

- 对于频繁访问的数据，可考虑缓存序列化结果
- Protobuf 解析可能成为性能瓶颈，建议在 Custom 类型上使用高效的序列化方案

## 8. 扩展性

- 支持新增 key 类型（如复合键、时间戳键等）
- metadata 字段可用于存储各种应用特定信息
- 可通过自定义比较器支持复杂的排序逻辑

## 9. 示例

### 9.1 Ordered Bytes Key 示例

**使用流程**：
1. 创建字节数据（如 "user123" 的 UTF-8 编码）
2. 构造 OrderedBytesKey
3. 序列化得到字节数组
4. 反序列化还原为 IndexKey

### 9.2 Custom Key 示例

**使用流程**：
1. 定义 Protobuf 消息（如包含 userId 和 timestamp）
2. 构建消息实例并序列化为字节
3. 定义自定义比较器（按 userId 和 timestamp 排序）
4. 构造 CustomKey

### 9.3 Value 示例

**使用流程**：
1. 创建 value 数据（如 "Hello World"）
2. 构建元数据（如版本信息）
3. 构造 IndexValue 实例