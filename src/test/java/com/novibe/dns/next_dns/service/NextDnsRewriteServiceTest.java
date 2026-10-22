package com.novibe.dns.next_dns.service;

import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.http.dto.response.rewrite.RewriteDto;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NextDnsRewriteServiceTest {
    private final List<CreateRewriteDto> expected = List.of(new CreateRewriteDto("chatgpt.com", "83.217.211.149"));

    @Test void acceptsSavedRewrite() {
        assertDoesNotThrow(() -> NextDnsRewriteService.verifySaved(expected,
                List.of(new RewriteDto("id", "chatgpt.com", "83.217.211.149"))));
    }

    @Test void rejectsMissingOrStaleRewrite() {
        assertThrows(IllegalStateException.class, () -> NextDnsRewriteService.verifySaved(expected, List.of()));
        assertThrows(IllegalStateException.class, () -> NextDnsRewriteService.verifySaved(expected,
                List.of(new RewriteDto("id", "chatgpt.com", "193.233.112.88"))));
    }

    @Test void rejectsStaleDuplicateEvenWhenCorrectRecordExists() {
        assertThrows(IllegalStateException.class, () -> NextDnsRewriteService.verifySaved(expected, List.of(
                new RewriteDto("new", "chatgpt.com", "83.217.211.149"),
                new RewriteDto("old", "chatgpt.com", "193.233.112.88"))));
    }
}
