package com.novibe.common.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Resolver;

class DonorDnsUtilsTest {

    @Test
    void retriesTransientFailuresInsteadOfKeepingSourceIp() {
        try (var lookups = mockConstruction(Lookup.class, (lookup, context) -> {
            when(lookup.run()).thenReturn(null);
            when(lookup.getResult()).thenReturn(Lookup.TRY_AGAIN);
            when(lookup.getErrorString()).thenReturn("timed out");
        })) {
            assertThrows(IllegalStateException.class,
                    () -> DonorDnsUtils.fetchDonorIps("chatgpt.com", mock(Resolver.class)));
            assertEquals(3, lookups.constructed().size());
            lookups.constructed().forEach(lookup -> verify(lookup).setCache(null));
        }
    }

    @Test
    void skipsDomainsThatDonorDoesNotHave() {
        try (var lookups = mockConstruction(Lookup.class, (lookup, context) -> {
            when(lookup.run()).thenReturn(null);
            when(lookup.getResult()).thenReturn(Lookup.HOST_NOT_FOUND);
        })) {
            assertEquals(donorIps(), DonorDnsUtils.fetchDonorIps("removed.example", mock(Resolver.class)));
            assertEquals(1, lookups.constructed().size());
        }
    }

    private static SequencedSet<String> donorIps(String... ips) {
        return new LinkedHashSet<>(List.of(ips));
    }

    @Test
    void keepsCurrentIpWhenDonorStillReturnsIt() {
        assertEquals("1.1.1.2", DonorDnsUtils.chooseIp("1.1.1.2", donorIps("1.1.1.1", "1.1.1.2", "1.1.1.3")));
    }

    @Test
    void takesFirstDonorIpWhenCurrentIpIsOutdated() {
        assertEquals("1.1.1.1", DonorDnsUtils.chooseIp("9.9.9.9", donorIps("1.1.1.1", "1.1.1.2")));
    }

    @Test
    void keepsCurrentIpWhenDonorReturnsNothing() {
        assertEquals("9.9.9.9", DonorDnsUtils.chooseIp("9.9.9.9", donorIps()));
    }
}
