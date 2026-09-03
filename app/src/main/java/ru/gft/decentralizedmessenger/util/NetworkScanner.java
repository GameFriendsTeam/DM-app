package ru.gft.decentralizedmessenger.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replacement for api.utils.network.NetworkScanner.
 * Finds DecentralizedMessenger servers on the local network by probing a TCP port.
 */
public final class NetworkScanner {
    private final int port;
    private final int timeoutMillis;
    private final int maxThreads;
    private final FoundCallback onFound;

    public interface FoundCallback { void onFound(String ip, int port); }

    public enum Status { SUCCESS, NO_INTERFACES, ERROR }

    public static final class ScanResult {
        public final Status status;
        public final List<String> servers;
        public final List<String> scannedNetworks;
        public final int totalHostsScanned;
        public final double scanTimeSeconds;
        public final String error;

        public ScanResult(Status status, List<String> servers, List<String> scannedNetworks,
                          int totalHostsScanned, double scanTimeSeconds, String error) {
            this.status = status;
            this.servers = servers;
            this.scannedNetworks = scannedNetworks;
            this.totalHostsScanned = totalHostsScanned;
            this.scanTimeSeconds = scanTimeSeconds;
            this.error = error;
        }
    }

    public NetworkScanner(int port, double timeoutSeconds, int maxThreads, FoundCallback onFound) {
        this.port = port;
        this.timeoutMillis = (int) (timeoutSeconds * 1000);
        this.maxThreads = maxThreads;
        this.onFound = onFound;
    }

    public List<String> getAllInterfaces() {
        List<String> nets = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()
                            && addr.isSiteLocalAddress()) {
                        nets.add(toCidr((Inet4Address) addr, ni));
                    }
                }
            }
        } catch (Exception ignored) {}
        return nets;
    }

    private String toCidr(Inet4Address addr, NetworkInterface ni) {
        short prefix = 24;
        try {
            prefix = ni.getInterfaceAddresses().stream()
                    .filter(ia -> ia.getAddress().equals(addr))
                    .map(ia -> ia.getNetworkPrefixLength())
                    .findFirst().orElse((short) 24);
        } catch (Exception ignored) {}
        return hostAddress(addr) + "/" + prefix;
    }

    private String hostAddress(Inet4Address addr) {
        return addr.getHostAddress();
    }

    public ScanResult scanLocalNetworks() {
        long start = System.currentTimeMillis();
        Set<String> uniqueServers = new HashSet<>();
        AtomicInteger scanned = new AtomicInteger(0);
        List<String> interfaces = getAllInterfaces();
        if (interfaces.isEmpty()) {
            return new ScanResult(Status.NO_INTERFACES, Collections.emptyList(),
                    Collections.emptyList(), 0, 0, "No network interfaces found");
        }
        Set<String> uniqueNets = new HashSet<>(interfaces);
        try {
            for (String cidr : uniqueNets) {
                for (String ip : hostsInCidr(cidr)) {
                    if (checkPort(ip)) {
                        uniqueServers.add(ip);
                        if (onFound != null) onFound.onFound(ip, port);
                    }
                    scanned.incrementAndGet();
                }
            }
            List<String> sorted = new ArrayList<>(uniqueServers);
            Collections.sort(sorted);
            return new ScanResult(Status.SUCCESS, sorted, new ArrayList<>(uniqueNets),
                    scanned.get(), (System.currentTimeMillis() - start) / 1000.0, null);
        } catch (Exception e) {
            return new ScanResult(Status.ERROR, new ArrayList<>(uniqueServers),
                    new ArrayList<>(uniqueNets), scanned.get(),
                    (System.currentTimeMillis() - start) / 1000.0, e.getMessage());
        }
    }

    public ScanResult scanIpRange(String startIp, String endIp) {
        long start = System.currentTimeMillis();
        Set<String> uniqueServers = new HashSet<>();
        AtomicInteger scanned = new AtomicInteger(0);
        try {
            long a = ipToLong(startIp);
            long b = ipToLong(endIp);
            if (a > b) { long t = a; a = b; b = t; }
            ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
            for (long i = a; i <= b; i++) queue.add(longToIp(i));
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(maxThreads, 256));
            for (int i = 0; i < maxThreads; i++) {
                pool.submit(() -> {
                    String ip;
                    while ((ip = queue.poll()) != null) {
                        if (checkPort(ip)) {
                            uniqueServers.add(ip);
                            if (onFound != null) onFound.onFound(ip, port);
                        }
                        scanned.incrementAndGet();
                    }
                });
            }
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.MINUTES);
            List<String> sorted = new ArrayList<>(uniqueServers);
            Collections.sort(sorted);
            return new ScanResult(Status.SUCCESS, sorted,
                    Collections.singletonList(startIp + "-" + endIp),
                    scanned.get(), (System.currentTimeMillis() - start) / 1000.0, null);
        } catch (Exception e) {
            return new ScanResult(Status.ERROR, Collections.emptyList(),
                    Collections.emptyList(), scanned.get(),
                    (System.currentTimeMillis() - start) / 1000.0, e.getMessage());
        }
    }

    private boolean checkPort(String ip) {
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress(ip, port), timeoutMillis);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> hostsInCidr(String cidr) {
        String[] parts = cidr.split("/");
        String base = parts[0];
        int prefix = Integer.parseInt(parts[1]);
        long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        long network = ipToLong(base) & mask;
        long broadcast = network | (~mask & 0xFFFFFFFFL);
        List<String> hosts = new ArrayList<>();
        for (long ip = network + 1; ip < broadcast; ip++) hosts.add(longToIp(ip));
        return hosts;
    }

    private static long ipToLong(String ip) {
        String[] p = ip.split("\\.");
        return (Long.parseLong(p[0]) << 24) | (Long.parseLong(p[1]) << 16)
                | (Long.parseLong(p[2]) << 8) | Long.parseLong(p[3]);
    }

    private static String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "."
                + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
    }

    public static ScanResult findServersLocal(int port, double timeout, int maxThreads, FoundCallback cb) {
        return new NetworkScanner(port, timeout, maxThreads, cb).scanLocalNetworks();
    }

    public static ScanResult findServersGlobal(String startIp, String endIp, int port,
                                               double timeout, int maxThreads, FoundCallback cb) {
        return new NetworkScanner(port, timeout, maxThreads, cb).scanIpRange(startIp, endIp);
    }
}
