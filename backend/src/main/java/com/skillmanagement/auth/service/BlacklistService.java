package com.skillmanagement.auth.service;

public interface BlacklistService {

    void blacklistToken(String jwtToken);

    boolean isTokenBlacklisted(String jwtToken);
}
