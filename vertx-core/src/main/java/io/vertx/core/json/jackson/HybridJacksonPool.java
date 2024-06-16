package io.vertx.core.json.jackson;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.JsonRecyclerPools;
import com.fasterxml.jackson.core.util.RecyclerPool;

/**
 * This is a custom implementation of the Jackson's {@link RecyclerPool} intended to work equally well with both
 * platform and virtual threads. This pool works regardless of the version of the JVM in use and internally uses
 * 2 distinct pools one for platform threads (which is exactly the same {@link ThreadLocal} based one provided
 * by Jackson out of the box) and the other designed for being virtual threads friendly. It switches between
 * the 2 only depending on the nature of thread (virtual or not) requiring the acquisition of a pooled resource,
 * obtained via {@link MethodHandle} to guarantee compatibility also with old JVM versions. The pool also guarantees
 * that the pooled resource is always released to the same internal pool from where it has been acquired, regardless
 * if the releasing thread is different from the one that originally made the acquisition.
 * <p>
 * The virtual thread friendly inner pool is implemented with N striped linked lists using a simple lock free
 * algorithm based on CAS. The striping is performed shuffling the id of the thread requiring to acquire a pooled
 * resource with a xorshift based computation. The resulting of this computation is also stored in the pooled resource,
 * bringing the twofold advantage of always releasing the resource in the same bucket from where it has been taken
 * regardless if the releasing thread is different from the one that did the acquisition and avoiding the need of
 * recalculating the position of that bucket also during the release. The heads of the linked lists are hold in an
 * {@link AtomicReferenceArray} where each head has a distance of 16 positions from the adjacent ones to prevent
 * the false sharing problem.
 */
public class HybridJacksonPool implements RecyclerPool<BufferRecycler> {

  private static final HybridJacksonPool INSTANCE = new HybridJacksonPool();

  private static final Predicate<Thread> isVirtual = VirtualPredicate.findIsVirtualPredicate();

  private final RecyclerPool<BufferRecycler> nativePool = JsonRecyclerPools.threadLocalPool();

  private static class VirtualPoolHolder {
    // Lazy on-demand initialization
    private static final StripedLockFreePool virtualPool = new StripedLockFreePool(Runtime.getRuntime().availableProcessors());
  }

  private HybridJacksonPool() {
    // prevent external instantiation
  }

  public static HybridJacksonPool getInstance() {
    return INSTANCE;
  }

  @Override
  public BufferRecycler acquirePooled() {
    return isVirtual.test(Thread.currentThread()) ?
      VirtualPoolHolder.virtualPool.acquirePooled() :
      nativePool.acquirePooled();
  }

  @Override
  public BufferRecycler acquireAndLinkPooled() {
    // when using the ThreadLocal based pool it is not necessary to register the BufferRecycler on the pool
    return isVirtual.test(Thread.currentThread()) ?
      VirtualPoolHolder.virtualPool.acquireAndLinkPooled() :
      nativePool.acquirePooled();
  }

  @Override
  public void releasePooled(BufferRecycler bufferRecycler) {
    if (bufferRecycler instanceof VThreadBufferRecycler) {
      // if it is a PooledBufferRecycler it has been acquired by a virtual thread, so it has to be release to the same pool
      VirtualPoolHolder.virtualPool.releasePooled(bufferRecycler);
    }
    // the native thread pool is based on ThreadLocal, so it doesn't have anything to do on release
  }

  public static class StripedLockFreePool implements RecyclerPool<BufferRecycler> {

    private static final int CACHE_LINE_SHIFT = 4;

    private static final int CACHE_LINE_PADDING = 1 << CACHE_LINE_SHIFT;

    private final XorShiftThreadProbe threadProbe;

    private final AtomicReferenceArray<Node> topStacks;

    private final int stripesCount;

    public StripedLockFreePool(int stripesCount) {
      if (stripesCount <= 0) {
        throw new IllegalArgumentException("Expecting a stripesCount that is larger than 0");
      }

      this.stripesCount = stripesCount;
      int size = roundToPowerOfTwo(stripesCount);
      this.topStacks = new AtomicReferenceArray<>(size * CACHE_LINE_PADDING);

      int mask = (size - 1) << CACHE_LINE_SHIFT;
      this.threadProbe = new XorShiftThreadProbe(mask);
    }

    public int size() {
      return stackSizes().sum();
    }

    public int[] stackStats() {
      return stackSizes().toArray();
    }

    private IntStream stackSizes() {
      return IntStream.range(0, stripesCount).map(i -> {
        Node node = topStacks.get(i * CACHE_LINE_PADDING);
        return node == null ? 0 : node.level;
      });
    }

    @Override
    public BufferRecycler acquirePooled() {
      int index = threadProbe.index();

      Node currentHead = topStacks.get(index);
      while (true) {
        if (currentHead == null) {
          return new VThreadBufferRecycler(index);
        }

        if (topStacks.compareAndSet(index, currentHead, currentHead.next)) {
          currentHead.next = null;
          return currentHead.value;
        } else {
          currentHead = topStacks.get(index);
        }
      }
    }

    @Override
    public void releasePooled(BufferRecycler recycler) {
      VThreadBufferRecycler vThreadBufferRecycler = (VThreadBufferRecycler) recycler;
      Node newHead = new Node(vThreadBufferRecycler);

      Node next = topStacks.get(vThreadBufferRecycler.slot);
      while (true) {
        newHead.level = next == null ? 1 : next.level + 1;
        if (topStacks.compareAndSet(vThreadBufferRecycler.slot, next, newHead)) {
          newHead.next = next;
          return;
        } else {
          next = topStacks.get(vThreadBufferRecycler.slot);
        }
      }
    }

    private static class Node {
      final VThreadBufferRecycler value;
      Node next;
      int level = 0;

      Node(VThreadBufferRecycler value) {
        this.value = value;
      }
    }
  }

  private static class VThreadBufferRecycler extends BufferRecycler {
    private final int slot;

    VThreadBufferRecycler(int slot) {
      this.slot = slot;
    }
  }

  private static class VirtualPredicate {
    private static final MethodHandle virtualMh = findVirtualMH();

    private static MethodHandle findVirtualMH() {
      try {
        return MethodHandles.publicLookup().findVirtual(Thread.class, "isVirtual",
          MethodType.methodType(boolean.class));
      } catch (Exception e) {
        return null;
      }
    }

    private static Predicate<Thread> findIsVirtualPredicate() {
      if (virtualMh != null) {
        return new Predicate<Thread>() {
          @Override
          public boolean test(Thread thread) {
            try {
              return (boolean) virtualMh.invokeExact(thread);
            } catch (Throwable e) {
              throw new RuntimeException(e);
            }
          }
        };
      }

      return new Predicate<Thread>() {
        @Override
        public boolean test(Thread thread) {
          return false;
        }
      };
    }
  }

  /**
   * This class is used to hash the thread requiring a pooled resource using a multiplicative
   * Fibonacci hashing implementation. The resulting hash is then used to calculate the
   * index of the bucket in the pool from where the pooled resource has to be retrieved.
   */
  private static class XorShiftThreadProbe {

    private final int mask;

    XorShiftThreadProbe(int mask) {
      this.mask = mask;
    }

    public int index() {
      return probe() & mask;
    }

    private int probe() {
      // Multiplicative Fibonacci hashing implementation
      // 0x9e3779b9 is the integral part of the Golden Ratio's fractional part 0.61803398875… (sqrt(5)-1)/2
      // multiplied by 2^32, which has the best possible scattering properties.
      int probe = (int) ((Thread.currentThread().getId() * 0x9e3779b9) & Integer.MAX_VALUE);
      // xorshift
      probe ^= probe << 13;
      probe ^= probe >>> 17;
      probe ^= probe << 5;
      return probe;
    }
  }

  private static final int MAX_POW2 = 1 << 30;

  private static int roundToPowerOfTwo(final int value) {
    if (value > MAX_POW2) {
      throw new IllegalArgumentException(
        "There is no larger power of 2 int for value:" + value + " since it exceeds 2^31.");
    }
    if (value < 0) {
      throw new IllegalArgumentException("Given value:" + value + ". Expecting value >= 0.");
    }
    final int nextPow2 = 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    return nextPow2;
  }
}
