package com.novibe.common.util;

import com.novibe.common.base_structures.BypassRoute;
import com.novibe.common.base_structures.DnsProfile;
import com.novibe.common.exception.UserInputException;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Address;
import org.xbill.DNS.DohResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

public class DonorDnsUtils {

    public static void replaceIPs(List<BypassRoute> bypassRoutes, DnsProfile dnsProfile) {
        Resolver dnsResolver = getDnsResolver(dnsProfile);
        dnsResolver.setTimeout(Duration.ofSeconds(5));
        List<Future<BypassRoute>> pending = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            for (BypassRoute route : bypassRoutes) {
                pending.add(executor.submit(() -> replaceIp(route, dnsResolver)));
            }
            List<BypassRoute> resolved = new ArrayList<>();
            for (Future<BypassRoute> task : pending) {
                try {
                    BypassRoute route = task.get();
                    if (route != null) resolved.add(route);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    pending.forEach(future -> future.cancel(true));
                    throw new IllegalStateException("Donor lookup interrupted; rewrites were not saved", e);
                } catch (ExecutionException e) {
                    pending.forEach(future -> future.cancel(true));
                    throw new IllegalStateException("Donor lookup failed; rewrites were not saved", e.getCause());
                }
            }
            if (!bypassRoutes.isEmpty() && resolved.isEmpty()) {
                throw new IllegalStateException("Donor resolved no domains; rewrites were not saved");
            }
            int skipped = bypassRoutes.size() - resolved.size();
            if (skipped > 0) {
                Log.fail("Skipped %s donor lookups; existing rules for these domains are preserved".formatted(skipped));
                if ("true".equals(System.getenv("GITHUB_ACTIONS"))) {
                    System.out.println("::warning::Skipped " + skipped + " unresolved donor domains; existing rules preserved. See lookup errors above.");
                }
            }
            bypassRoutes.clear();
            bypassRoutes.addAll(resolved);
        }
    }

    private static BypassRoute replaceIp(BypassRoute bypassRoute, Resolver dnsResolver) {
        SequencedSet<String> donorIps = fetchUsableDonorIps(bypassRoute.website(), dnsResolver);
        if (donorIps.isEmpty()) {
            Log.fail("Donor has no A record for %s; leaving existing rewrite untouched".formatted(bypassRoute.website()));
            return null;
        }
        String newIp = chooseIp(bypassRoute.ip(), donorIps);
        if (!bypassRoute.ip().equals(newIp)) {
            Log.common("Changed IP for %s: %s -> %s".formatted(bypassRoute.website(), bypassRoute.ip(), newIp));
            bypassRoute.ip(newIp);
        }
        return bypassRoute;
    }

    static SequencedSet<String> fetchUsableDonorIps(String domain, Resolver resolver) {
        try {
            return fetchDonorIps(domain, resolver);
        } catch (IllegalStateException e) {
            Log.fail(e.getMessage() + "; preserving existing rewrite instead of using the source IP");
            return new LinkedHashSet<>();
        }
    }

    // Donor may return several IPs in random order, so keep current IP if it is still among them
    static String chooseIp(String currentIp, SequencedSet<String> donorIps) {
        if (donorIps.isEmpty() || donorIps.contains(currentIp)) {
            return currentIp;
        }
        return donorIps.getFirst();
    }

    private static Resolver getDnsResolver(DnsProfile dnsProfile) {
        String dns = dnsProfile.donorDns();
        try {
            if (dns.startsWith("http")) {
                return new DohResolver(dns);
            } else if (Address.isDottedQuad(dns)) {
                return new SimpleResolver(dns);
            } else {
                throw new UnknownHostException();
            }
        } catch (UnknownHostException e) {
            throw UserInputException.noStackTrace("Invalid DONOR_DNS value: %s. Value must follow Ipv4 or DoH format".formatted(dns));
        }
    }

    static SequencedSet<String> fetchDonorIps(String domain, Resolver resolver) {
        try {
            for (int attempt = 1; attempt <= 3; attempt++) {
                Lookup lookup = new Lookup(domain, Type.A);
                lookup.setResolver(resolver);
                // Each donor must be queried independently of dnsjava's shared cache.
                lookup.setCache(null);
                Record[] records = lookup.run();
                if (isNull(records)) {
                    if (lookup.getResult() == Lookup.HOST_NOT_FOUND || lookup.getResult() == Lookup.TYPE_NOT_FOUND) {
                        return new LinkedHashSet<>();
                    }
                    Log.fail("Donor lookup %s/3 failed for %s: %s".formatted(attempt, domain, lookup.getErrorString()));
                    continue;
                }
                return Arrays.stream(records)
                        .filter(ARecord.class::isInstance)
                        .map(record -> ((ARecord) record).getAddress().getHostAddress())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }
            throw new IllegalStateException("Donor did not resolve " + domain + " after 3 attempts");
        } catch (TextParseException e) {
            throw new IllegalArgumentException("Invalid domain address: " + domain, e);
        }
    }

}
