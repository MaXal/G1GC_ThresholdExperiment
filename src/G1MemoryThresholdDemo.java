import java.lang.management.*;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class G1MemoryThresholdDemo {
    private static final List<byte[]> strongReferences = new ArrayList<>();
    private static final List<SoftReference<byte[]>> softReferences = new ArrayList<>();
    private static final int ALLOCATION_SIZE = 1024 * 1024; // 1MB
    private static final Random random = new Random(0);

    private static final long MEM_THRESHOLD = 5 /*MB*/ * 1024 * 1024;

    public static void main(String[] args) {
        setupMemoryMonitoring();
        simulateMemoryPressure();
    }

    private static void setupMemoryMonitoring() {
        MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
        NotificationEmitter emitter = (NotificationEmitter) mbean;

        // Set up notification listener
        emitter.addNotificationListener(new NotificationListener() {
            @Override
            public void handleNotification(Notification n, Object handback) {
                boolean memoryThreshold = MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED.equals(n.getType());
                boolean memoryCollectionThreshold = MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED.equals(n.getType());

                if (memoryThreshold || memoryCollectionThreshold) {
                    System.out.println("\nMemory collection threshold exceeded!");
                    System.out.println("Heap Memory Usage: " + mbean.getHeapMemoryUsage());
                    System.out.println("Strong references: " + strongReferences.size() + "MB");
                    System.out.println("Soft references: " + softReferences.size() + "MB");
                    System.out.println("Live soft references: " + countLiveSoftReferences() + "MB");
                    System.out.println("After GC: " + memoryCollectionThreshold);
                }
            }
        }, null, null);

        // Set memory threshold on Old Gen
        for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (bean.getType() == MemoryType.HEAP && bean.isCollectionUsageThresholdSupported() && bean.isUsageThresholdSupported()) {
                long max = bean.getUsage().getMax();
                long threshold = Math.min((long)(max * 0.95f), max - MEM_THRESHOLD);
                if (threshold > 0) {
                    bean.setUsageThreshold(threshold);
                    bean.setCollectionUsageThreshold(threshold);
                }
                System.out.println("Set threshold on " + bean.getName() + ": " + threshold + " bytes");
                System.out.println("Current usage: " + bean.getUsage().getUsed() + " bytes");
            }
        }
    }

    private static long countLiveSoftReferences() {
        List<SoftReference<byte[]>> snapshot;
        synchronized(softReferences) {
            snapshot = new ArrayList<>(softReferences);
        }
        return snapshot.stream()
                .filter(ref -> ref.get() != null)
                .count();
    }

    private static void simulateMemoryPressure() {
        try {
            System.out.println("Starting memory allocation...");
            int allocations = 0;

            while (true) {
                // Randomly choose between creating a strong or soft reference
                if (random.nextBoolean()) {
                    // Create strong reference
                    strongReferences.add(new byte[ALLOCATION_SIZE]);
                } else {
                    // Create soft reference
                    softReferences.add(new SoftReference<>(new byte[ALLOCATION_SIZE]));
                }

                allocations++;

                if (allocations % 100 == 0) {
                    System.out.println("\nAllocation cycle " + allocations);
                    System.out.println("Strong references: " + strongReferences.size() + "MB");
                    System.out.println("Soft references: " + softReferences.size() + "MB");
                    System.out.println("Live soft references: " + countLiveSoftReferences() + "MB");

                    Thread.sleep(500);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("OutOfMemoryError occurred!");
            System.out.println("Final strong references: " + strongReferences.size() + "MB");
            System.out.println("Final soft references: " + softReferences.size() + "MB");
            System.out.println("Final live soft references: " + countLiveSoftReferences() + "MB");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}