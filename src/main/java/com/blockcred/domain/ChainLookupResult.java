package com.blockcred.domain;

public record ChainLookupResult(
        boolean chainReachable,
        boolean chainRecordFound,
        boolean chainRevoked,
        String txHash
) {
    public static ChainLookupResult unavailable() {
        return new ChainLookupResult(false, false, false, null);
    }
}
