package com.novibe.dns.next_dns.http;

import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.common.exception.DnsHttpError;
import com.novibe.dns.next_dns.http.dto.response.rewrite.MultiRewriteResponse;
import com.novibe.dns.next_dns.http.dto.response.rewrite.RewriteDto;
import com.novibe.dns.next_dns.http.dto.response.rewrite.SingleRewriteResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NextDnsRewriteClient extends AbstractNextDnsApiClient {

    public List<RewriteDto> fetchRewrites() {
        return get(path(), MultiRewriteResponse.class)
                .getData();
    }

    public void saveRewrites(List<CreateRewriteDto> rewriteDtos) {
        callApiForEach(rewriteDtos, this::saveRewrite);
    }

    public void deleteRewritesByIds(List<String> ids) {
        callApiForEach(ids, this::deleteRewriteById);
    }

    private SingleRewriteResponse saveRewrite(CreateRewriteDto rewriteDto) {
        return post(path(), rewriteDto, SingleRewriteResponse.class);
    }

    private @Nullable SingleRewriteResponse deleteRewriteById(String id) {
        try {
            return delete(path() + "/" + id, SingleRewriteResponse.class);
        } catch (DnsHttpError e) {
            // A lost response may cause a retry after the first DELETE already succeeded.
            if (e.getCode() == 404 && fetchRewrites().stream().noneMatch(record -> record.id().equals(id))) {
                return null;
            }
            throw e;
        }
    }

    @Override
    protected String path() {
        return "/rewrites";
    }

}
