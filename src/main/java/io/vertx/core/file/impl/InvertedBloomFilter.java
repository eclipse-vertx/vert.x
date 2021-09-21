package io.vertx.core.file.impl;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class InvertedBloomFilter<T> {

  private final int sizeMask;
  private final AtomicReferenceArray<T> array;
  private static final int MAX_SIZE = 1 << 30;


  /**
   * Constructs a InvertedBloomFilter with an underlying array of the given size, rounded up to the next
   * power of two.
   *
   * This rounding occurs because the hashing is much faster on an array the size of a power of two.
   *
   * @param size The size of the underlying array.
   */
  public InvertedBloomFilter(int size) {
    if (size <= 0) {
      throw new IllegalArgumentException("array size must be greater than zero, was " + size);
    }
    if (size > MAX_SIZE) {
      throw new IllegalArgumentException(
        "array size may not be larger than 2**31-1, but will be rounded to larger. was " + size);
    }

    // compute the next highest power of 2 of 32-bit
    size--;
    size |= size >> 1;
    size |= size >> 2;
    size |= size >> 4;
    size |= size >> 8;
    size |= size >> 16;
    size++;

    this.sizeMask = size - 1;
    this.array = new AtomicReferenceArray<>(size);
  }

  public T add(T ref) {
    int code = Objects.hashCode(ref);  // not the greatest hashing function
    int index = Math.abs(code) & sizeMask;
    return array.getAndSet(index, ref);
  }

  public boolean test(T ref) {
    int code = Objects.hashCode(ref);  // not the greatest hashing function
    int index = Math.abs(code) & sizeMask;
    T oldRef = array.get(index);
    if (oldRef != null) {
      return ref.equals(oldRef);
    }
    return false;
  }
}
