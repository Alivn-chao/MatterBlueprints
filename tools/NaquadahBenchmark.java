package com.gtnhblueprints.machine;
import java.math.BigInteger;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
/** Standalone calculation-only benchmark; excludes coordinator, native machine/world and wireless ledger costs. */
public class NaquadahBenchmark {
  static volatile BigInteger sink;
  static final BigInteger ENERGY=BigInteger.TEN.pow(30).add(BigInteger.valueOf(713));
  static void run(int batches) {
    BigInteger total=BigInteger.ZERO;
    HostedNaquadahCycle[] cycles=new HostedNaquadahCycle[32];
    for(int b=0;b<batches;b++) {
      for(int i=0;i<32;i++) { cycles[i]=new HostedNaquadahCycle(1,1,1); cycles[i].energy=ENERGY; cycles[i].configure(true); }
      for(int t=0;t<1200;t++) for(HostedNaquadahCycle c:cycles) {
        BigInteger out=c.nextOutput(0); c.accept(out); total=total.add(out);
      }
    }
    sink=total;
  }
  public static void main(String[] args) {
    com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    bean.setThreadAllocatedMemoryEnabled(true);
    for(int i=0;i<4;i++)run(8);
    long[] times=new long[7], bytes=new long[7];
    long thread=Thread.currentThread().getId();
    for(int i=0;i<7;i++) {
      long a=bean.getThreadAllocatedBytes(thread),s=System.nanoTime(); run(32);
      times[i]=System.nanoTime()-s; bytes[i]=bean.getThreadAllocatedBytes(thread)-a;
    }
    Arrays.sort(times);Arrays.sort(bytes);
    System.out.println("32 cycles x 1200 ticks x 32 batches per sample; median of 7, Java "+System.getProperty("java.version"));
    System.out.printf(java.util.Locale.ROOT,"median_ms=%.3f allocated_MB=%.3f checksum=%s%n",times[3]/1e6,bytes[3]/1e6,sink);
  }
}
