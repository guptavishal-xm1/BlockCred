package com.blockcred.service;

import com.blockcred.domain.ChainLookupResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryBlockchainGateway implements BlockchainGateway {
    private final Map<String, ChainState> stateByHash = new ConcurrentHashMap<>();
    private volatile boolean unavailable;

    @Override
    public String anchor(String hash) {
        ensureAvailable();
        ChainState existing = stateByHash.get(hash);
        if (existing != null && existing.exists) {
            throw new IllegalStateException("Already anchored");
        }
        String txHash = tx();
        stateByHash.put(hash, new ChainState(true, false, txHash));
        return txHash;
    }

    @Override
    public String revoke(String hash) {
        ensureAvailable();
        ChainState existing = stateByHash.get(hash);
        if (existing == null || !existing.exists) {
            throw new IllegalStateException("Credential not anchored");
        }
        if (existing.revoked) {
            throw new IllegalStateException("Already revoked");
        }
        String txHash = tx();
        stateByHash.put(hash, new ChainState(true, true, txHash));
        return txHash;
    }

    @Override
    public ChainLookupResult lookup(String hash) {
        ensureAvailable();
        ChainState state = stateByHash.get(hash);
        if (state == null) {
            return new ChainLookupResult(true, false, false, null);
        }
        return new ChainLookupResult(true, true, state.revoked, state.txHash);
    }

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    public void clear() {
        this.stateByHash.clear();
    }

    private void ensureAvailable() {
        if (unavailable) {
            throw new ChainUnavailableException("RPC unavailable");
        }
    }

    private String tx() {
        return "0x" + UUID.randomUUID().toString().replace("-", "");
    }

    private record ChainState(boolean exists, boolean revoked, String txHash) {
    }
}
