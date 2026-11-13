package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;
import com.github.forax.ula4j.rt.intrinsic.ComparisonIntrinsics;
import com.github.forax.ula4j.rt.intrinsic.HashingIntrinsics;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/// Mutable, insertion-ordered Python-dictionary storage.
///
/// This class deliberately does not implement `java.util.Map`: Java equality and hashing
/// are not Python equality and hashing. Python-facing operations live in `DictIntrinsics`.
/// The caller supplies the already-computed Python hash and the Python key-equivalence predicate.
///
/// The hash table uses open addressing with perturbation probing. Entries are kept separately
/// in insertion order. A table slot contains zero for empty, [#DELETED] for a tombstone, or
/// the entry index plus one. A deleted entry is marked by a `null` key; Python values and
/// keys themselves can never be Java `null`.
///
/// Not thread-safe. Like the other mutable runtime containers, synchronization is the caller's
/// responsibility.
public final class PyDict<K,V> implements Map<K,V> {
  private static final int EMPTY = 0;
  private static final int DELETED = -1;
  private static final int MIN_TABLE_CAPACITY = 8;
  private static final int MAX_TABLE_CAPACITY = 1 << 30;
  private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

  // Insertion-order storage. A null key denotes a deleted entry.
  private K[] keys;
  private V[] values;
  private long[] hashes;
  private int entryCount;       // live + deleted positions, in insertion order
  private int size;             // number of live entries

  // Open-addressing index: 0 = empty, -1 = deleted, otherwise entry index + 1.
  private int[] table;
  private int occupied;         // live table slots + tombstones

  // Changes only when the key set changes; replacing an existing value does not change it.
  private long keysVersion;

  @SuppressWarnings("unchecked")
  public PyDict() {
    keys = (K[]) new Object[MIN_TABLE_CAPACITY];
    values = (V[]) new Object[MIN_TABLE_CAPACITY];
    hashes = new long[MIN_TABLE_CAPACITY];
    table = new int[MIN_TABLE_CAPACITY];
    super();
  }

  /// Number of live key/value pairs.
  public int size() {
    return size;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  /// Version of the key set. Insertions, deletions and non-empty clears change it; value
  /// replacement does not. Intended for dictionary iterators.
  public long layoutVersion() {
    return keysVersion;
  }

  /// Looks up a key, returning `null` when absent. Since stored values cannot be Java null,
  /// this distinguishes absence from every Python value, including `None`.
  public V get(Object key) {
    Objects.requireNonNull(key);
    var hash = HashingIntrinsics.$hash$(key);
    var slot = findSlot(key, hash);
    var entry = table[slot] - 1;
    return entry < 0 ? null : values[entry];
  }

  public boolean containsKey(Object key) {
    Objects.requireNonNull(key);
    var hash = HashingIntrinsics.$hash$(key);
    return table[findSlot(key, hash)] > 0;
  }

  /// Inserts or replaces a key. Returns the previous value, or `null` when this was a new
  /// key. Replacing an equal key keeps the originally inserted key object and its position.
  public V put(K key, V value) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);
    var hash = HashingIntrinsics.$hash$(key);
    var slot = findSlot(key, hash);
    var slotValue = table[slot];
    if (slotValue > 0) {
      var entry = slotValue - 1;
      var previous = values[entry];
      values[entry] = value;
      return previous;
    }

    ensureTableCapacityForInsert();
    // Recompute after a possible rehash.
    slot = findSlot(key, hash);
    slotValue = table[slot];
    ensureEntryCapacity(entryCount + 1);

    var entry = entryCount++;
    keys[entry] = key;
    values[entry] = value;
    hashes[entry] = hash;
    table[slot] = entry + 1;
    if (slotValue == EMPTY) {
      occupied++;
    }
    size++;
    keysVersion++;
    return null;
  }

  /// Removes a key, returning its previous value, or `null` when absent. Reinserting a
  /// removed key appends it at the end of insertion order.
  public V remove(Object key) {
    Objects.requireNonNull(key);
    var hash = HashingIntrinsics.$hash$(key);
    var slot = findSlot(key, hash);
    var slotValue = table[slot];
    if (slotValue <= 0) {
      return null;
    }

    var entry = slotValue - 1;
    var previous = values[entry];
    table[slot] = DELETED;
    keys[entry] = null;
    values[entry] = null;
    hashes[entry] = 0L;
    size--;
    keysVersion++;

    compactIfSparse();
    return previous;
  }

  /// Removes every entry and releases oversized backing arrays.
  @SuppressWarnings("unchecked")
  public void clear() {
    if (size == 0 && entryCount == 0) {
      return;
    }
    keys = (K[]) new Object[MIN_TABLE_CAPACITY];
    values = (V[]) new Object[MIN_TABLE_CAPACITY];
    hashes = new long[MIN_TABLE_CAPACITY];
    table = new int[MIN_TABLE_CAPACITY];
    entryCount = 0;
    size = 0;
    occupied = 0;
    keysVersion++;
  }

  // -- Insertion-order access for dictionary equality, repr and iterators.
  // The index is an entry position, not the ordinal among live entries.

  /// Number of insertion-order positions, including positions deleted since the last compaction.
  public int entryCount() {
    return entryCount;
  }

  /** True when the insertion-order position currently holds a live entry. */
  public boolean isLive(int entryIndex) {
    Objects.checkIndex(entryIndex, entryCount);
    return keys[entryIndex] != null;
  }

  /// Returns a live key at an insertion-order position, or null for a deleted position.
  public K keyAt(int entryIndex) {
    Objects.checkIndex(entryIndex, entryCount);
    return keys[entryIndex];
  }

  /// Returns a live value at an insertion-order position, or null for a deleted position.
  public V valueAt(int entryIndex) {
    Objects.checkIndex(entryIndex, entryCount);
    return values[entryIndex];
  }

  // -- Java API

  @Override
  public Set<Entry<K, V>> entrySet() {
    return new AbstractSet<>() {
      @Override
      public int size() {
        return size;
      }

      @Override
      public boolean contains(Object o) {
        Objects.requireNonNull(o);
        if (!(o instanceof Entry<?,?> entry)) {
          return false;
        }
        var value = get(entry.getKey());
        return value != null && ComparisonIntrinsics.$sameOrEq$(value, entry.getValue());
      }

      @Override
      public boolean remove(Object o) {
        Objects.requireNonNull(o);
        if (!(o instanceof Entry<?,?> entry)) {
          return false;
        }
        return PyDict.this.remove(entry.getKey(), entry.getValue());
      }

      @Override
      public Iterator<Entry<K, V>> iterator() {
        var keysVersion = PyDict.this.keysVersion;
        return new Iterator<>() {
          private int index = nextIndex(0);

          private int nextIndex(int index) {
            for(;index < entryCount; index++) {
              if (keys[index] != null) {
                return index;
              }
            }
            return index;  // == entryCount
          }

          @Override
          public boolean hasNext() {
            return index < entryCount;
          }

          @Override
          public Entry<K, V> next() {
            if (!hasNext()) {
              throw new NoSuchElementException();
            }
            if (keysVersion != PyDict.this.keysVersion) {
              throw new ConcurrentModificationException();
            }
            var key = keys[index];
            var value = values[index];
            final class Entry implements Map.Entry<K, V> {
              @Override
              public K getKey() {
                return key;
              }
              @Override
              public V getValue() {
                return value;
              }
              @Override
              public V setValue(V value) {
                throw new UnsupportedOperationException();
              }

              @Override
              public int hashCode() {
                return Long.hashCode(
                    HashingIntrinsics.$hash$(key) ^ HashingIntrinsics.$hash$(value));
              }

              @Override
              public boolean equals(Object obj) {
                return obj instanceof Map.Entry<?,?> entry &&
                    ComparisonIntrinsics.$sameOrEq$(key, entry.getKey()) &&
                    ComparisonIntrinsics.$sameOrEq$(value, entry.getValue());
              }
            }
            index =  nextIndex(index + 1);
            return new Entry();
          }
        };
      }
    };
  }


  @Override
  public V getOrDefault(Object key, V defaultValue) {
    Objects.requireNonNull(key);
    var hash = HashingIntrinsics.$hash$(key);
    var slot = findSlot(key, hash);
    var entry = table[slot] - 1;
    return entry < 0 ? defaultValue : values[entry];
  }

  @Override
  public boolean containsValue(Object value) {
    Objects.requireNonNull(value);
    for(var i = 0; i < entryCount; i++) {
      var element = values[i];
      if (element != null && ComparisonIntrinsics.$sameOrEq$(element, value)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean remove(Object key, Object value) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);
    var hash = HashingIntrinsics.$hash$(key);
    var slot = findSlot(key, hash);
    var slotValue = table[slot];
    int entry;
    if (slotValue <= 0 || !ComparisonIntrinsics.$sameOrEq$(values[entry = slotValue - 1], value)) {
      return false;
    }

    table[slot] = DELETED;
    keys[entry] = null;
    values[entry] = null;
    hashes[entry] = 0L;
    size--;
    keysVersion++;

    compactIfSparse();
    return true;
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(oldValue);
    Objects.requireNonNull(newValue);
    var hash = HashingIntrinsics.$hash$(key);
    var slot = findSlot(key, hash);
    var slotValue = table[slot];
    int entry ;
    if (slotValue <= 0 || !ComparisonIntrinsics.$sameOrEq$(values[entry = slotValue - 1], oldValue)) {
      return false;
    }
    values[entry] = newValue;
    return true;
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    Objects.requireNonNull(map);
    map.forEach(this::put);
  }

  @Override
  public Set<K> keySet() {
    return new AbstractSet<>() {
      @Override
      public int size() {
        return size;
      }

      @Override
      public boolean contains(Object o) {
        Objects.requireNonNull(o);
        return containsKey(o);
      }

      @Override
      public boolean remove(Object o) {
        Objects.requireNonNull(o);
        return PyDict.this.remove(o) != null;
      }

      @Override
      public Iterator<K> iterator() {
        var keysVersion = PyDict.this.keysVersion;
        return new Iterator<>() {
          private int index = nextIndex(0);

          private int nextIndex(int index) {
            for(;index < entryCount; index++) {
              if (keys[index] != null) {
                return index;
              }
            }
            return index;  // == entryCount
          }

          @Override
          public boolean hasNext() {
            return index < entryCount;
          }

          @Override
          public K next() {
            if (!hasNext()) {
              throw new NoSuchElementException();
            }
            if (keysVersion != PyDict.this.keysVersion) {
              throw new ConcurrentModificationException();
            }
            var key =  keys[index];
            index =  nextIndex(index + 1);
            return key;
          }
        };
      }
    };
  }

  @Override
  public Collection<V> values() {
    return new AbstractCollection<>() {
      @Override
      public int size() {
        return size;
      }

      @Override
      public boolean contains(Object o) {
        Objects.requireNonNull(o);
        return containsValue(o);
      }

      @Override
      public boolean remove(Object o) {
        Objects.requireNonNull(o);
        for(var i = 0; i < entryCount; i++) {
          var value = values[i];
          if (value != null && ComparisonIntrinsics.$sameOrEq$(value, o)) {
            table[i] = DELETED;
            keys[i] = null;
            values[i] = null;
            hashes[i] = 0L;
            size--;
            keysVersion++;

            compactIfSparse();
            return true;
          }
        }
        return false;
      }

      @Override
      public Iterator<V> iterator() {
        var keysVersion = PyDict.this.keysVersion;
        return new Iterator<>() {
          private int index = nextIndex(0);

          private int nextIndex(int index) {
            for(;index < entryCount; index++) {
              if (values[index] != null) {
                return index;
              }
            }
            return index;  // == entryCount
          }

          @Override
          public boolean hasNext() {
            return index < entryCount;
          }

          @Override
          public V next() {
            if (!hasNext()) {
              throw new NoSuchElementException();
            }
            if (keysVersion != PyDict.this.keysVersion) {
              throw new ConcurrentModificationException();
            }
            var value =  values[index];
            index = nextIndex(index + 1);
            return value;
          }
        };
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    Objects.requireNonNull(o);
    return ComparisonIntrinsics.$eq$(this, o);
  }

  @Override
  public int hashCode() {
    throw unhashable();
  }

  private static PyTypeError unhashable() {
    return new PyTypeError("unhashable type: 'dict'");
  }

  // -- Hash-table implementation

  /// Finds either the slot containing an equal key or the first available slot. At least one empty
  /// slot is maintained by the load-factor policy, so probing always terminates.
  private int findSlot(Object key, long hash) {
    var mask = table.length - 1;
    var index = ((int) hash) & mask;
    var perturb = hash;
    var firstDeleted = -1;

    while (true) {
      var slot = table[index];
      if (slot == EMPTY) {
        return firstDeleted >= 0 ? firstDeleted : index;
      }
      if (slot == DELETED) {
        if (firstDeleted < 0) {
          firstDeleted = index;
        }
      } else {
        var entry = slot - 1;
        if (hashes[entry] == hash && (ComparisonIntrinsics.$sameOrEq$(keys[entry], key))) {
          return index;
        }
      }

      // CPython-style perturbation probing. Unsigned shift is important for negative hashes.
      index = (int) ((index * 5L + 1L + perturb) & mask);
      perturb >>>= 5;
    }
  }

  private void ensureTableCapacityForInsert() {
    if (occupied + 1 <= maxFill(table.length)) {
      return;
    }

    // If live entries themselves exceed the threshold, grow.
    // Otherwise, remove tombstones by rehashing at the same capacity.
    if (size + 1 > maxFill(table.length)) {
      if (table.length == MAX_TABLE_CAPACITY) {
        throw tooLarge();
      }
      rehash(table.length << 1);
    } else {
      rehash(table.length);
    }
  }

  private void ensureEntryCapacity(int minimumCapacity) {
    if (minimumCapacity <= keys.length) {
      return;
    }
    if (minimumCapacity > MAX_ARRAY_SIZE) {
      throw tooLarge();
    }
    var grown = keys.length + (keys.length >>> 1) + 1L;
    var newCapacity = (int) Math.min(MAX_ARRAY_SIZE, Math.max(minimumCapacity, grown));
    keys = Arrays.copyOf(keys, newCapacity);
    values = Arrays.copyOf(values, newCapacity);
    hashes = Arrays.copyOf(hashes, newCapacity);
  }

  private void compactIfSparse() {
    var deleted = entryCount - size;
    if (deleted >= 8 && deleted > size) {
      compactEntries();
    }
  }

  /// Removes dead insertion-order positions and rebuilds the index without changing live order.
  private void compactEntries() {
    var capacity = Math.max(MIN_TABLE_CAPACITY, size);
    @SuppressWarnings("unchecked")
    var compactKeys = (K[]) new Object[capacity];
    @SuppressWarnings("unchecked")
    var compactValues = (V[]) new Object[capacity];
    var compactHashes = new long[capacity];
    var newEntryCount = 0;

    for (var i = 0; i < entryCount; i++) {
      var key = keys[i];
      if (key == null) {
        continue;
      }
      compactKeys[newEntryCount] = key;
      compactValues[newEntryCount] = values[i];
      compactHashes[newEntryCount] = hashes[i];
      newEntryCount++;
    }

    keys = compactKeys;
    values = compactValues;
    hashes = compactHashes;
    entryCount = newEntryCount;
    rehash(tableCapacityFor(size));
  }

  /// Rebuilds the hash index from the live insertion-order entries.
  private void rehash(int newCapacity) {
    var newTable = new int[newCapacity];
    for (var entry = 0; entry < entryCount; entry++) {
      if (keys[entry] == null) {
        continue;
      }
      insertExisting(newTable, entry, hashes[entry]);
    }
    table = newTable;
    occupied = size;
  }

  private static void insertExisting(int[] target, int entry, long hash) {
    var mask = target.length - 1;
    var index = ((int) hash) & mask;
    var perturb = hash;
    while (target[index] != EMPTY) {
      index = (int) ((index * 5L + 1L + perturb) & mask);
      perturb >>>= 5;
    }
    target[index] = entry + 1;
  }

  private static int tableCapacityFor(int expectedSize) {
    var capacity = MIN_TABLE_CAPACITY;
    while (expectedSize > maxFill(capacity)) {
      if (capacity == MAX_TABLE_CAPACITY) {
        throw tooLarge();
      }
      capacity <<= 1;
    }
    return capacity;
  }

  private static int maxFill(int capacity) {
    return (int) (((long) capacity * 2L) / 3L);
  }

  private static UnimplementedFeatureError tooLarge() {
    return new UnimplementedFeatureError("dictionary larger than the maximum Java array size");
  }
}
