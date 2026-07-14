// Base64 (URL-safe) codec for Temporal visibility page tokens crossing the HTTP boundary.
// Normalizes the empty/no-more-pages token to null so callers can treat it as "exhausted".
package com.ziggy.insurance.api;

import com.google.protobuf.ByteString;
import java.util.Base64;

final class PageTokens {

    private PageTokens() {}

    static String encode(ByteString token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray());
    }

    static ByteString decode(String token) {
        if (token == null || token.isBlank()) {
            return ByteString.EMPTY;
        }
        return ByteString.copyFrom(Base64.getUrlDecoder().decode(token));
    }
}
