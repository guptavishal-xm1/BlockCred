package com.blockcred.service;

import com.blockcred.domain.ChainLookupResult;

public interface BlockchainGateway {
    String anchor(String hash);
    String revoke(String hash);
    ChainLookupResult lookup(String hash);
}
