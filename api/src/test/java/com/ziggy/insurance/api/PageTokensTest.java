// Unit tests for the Base64 codec that carries Temporal visibility page tokens over HTTP.
// Covers the empty/null normalization and binary round-trip the paginated list path relies on.
package com.ziggy.insurance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

class PageTokensTest {

    @Test
    void encodeReturnsNullForEmptyToken() {
        assertThat(PageTokens.encode(ByteString.EMPTY)).isNull();
    }

    @Test
    void encodeReturnsNullForNullToken() {
        assertThat(PageTokens.encode(null)).isNull();
    }

    @Test
    void decodeReturnsEmptyForNullOrBlank() {
        assertThat(PageTokens.decode(null)).isEqualTo(ByteString.EMPTY);
        assertThat(PageTokens.decode("   ")).isEqualTo(ByteString.EMPTY);
    }

    @Test
    void encodeThenDecodeRoundTripsBinaryToken() {
        ByteString original = ByteString.copyFrom(new byte[] {0, 1, 2, -1, -2, 127, -128, 63});

        String encoded = PageTokens.encode(original);

        assertThat(PageTokens.decode(encoded)).isEqualTo(original);
    }
}
