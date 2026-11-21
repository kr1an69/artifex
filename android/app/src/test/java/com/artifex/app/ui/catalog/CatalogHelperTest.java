package com.artifex.app.ui.catalog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.*;

/**
 * Unit test cho CatalogHelper (phân quyền ADMIN token & validate input tạo sản phẩm).
 */
public class CatalogHelperTest {

    private String makeToken(Long userId, String role, long expiresAt) {
        String payload = userId + ":" + role + ":" + expiresAt + ":abcd1234";
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return "artifex." + encoded + ".dummySignature";
    }

    @Test
    public void isAdminToken_returnsTrueForValidAdminToken() {
        long now = 1_700_000_000_000L;
        String token = makeToken(2L, "ADMIN", now + 60_000L);
        assertTrue(CatalogHelper.isAdminToken(token, now));
    }

    @Test
    public void isAdminToken_returnsFalseForCustomerOrExpiredOrInvalid() {
        long now = 1_700_000_000_000L;
        assertFalse(CatalogHelper.isAdminToken(makeToken(1L, "USER", now + 60_000L), now));
        assertFalse(CatalogHelper.isAdminToken(makeToken(1L, "CUSTOMER", now + 60_000L), now));
        assertFalse(CatalogHelper.isAdminToken(makeToken(2L, "ADMIN", now - 1_000L), now));
        assertFalse(CatalogHelper.isAdminToken("", now));
        assertFalse(CatalogHelper.isAdminToken(null, now));
        assertFalse(CatalogHelper.isAdminToken("invalid-token", now));
    }

    @Test
    public void validateProductName_enforcesRequiredAndMax150Chars() {
        assertNotNull(CatalogHelper.validateProductName(""));
        assertNotNull(CatalogHelper.validateProductName("   "));
        assertNotNull(CatalogHelper.validateProductName(null));

        String over150 = "A".repeat(151);
        assertNotNull(CatalogHelper.validateProductName(over150));

        assertNull(CatalogHelper.validateProductName("RTX 4070 Super"));
        assertNull(CatalogHelper.validateProductName("A".repeat(150)));
    }

    @Test
    public void validateProductPrice_rejectsZeroNegativeAndOverflow() {
        assertNotNull(CatalogHelper.validateProductPrice(""));
        assertNotNull(CatalogHelper.validateProductPrice("abc"));
        assertNotNull(CatalogHelper.validateProductPrice("0"));
        assertNotNull(CatalogHelper.validateProductPrice("-1000"));
        assertNotNull(CatalogHelper.validateProductPrice("10000000000"));

        assertNull(CatalogHelper.validateProductPrice("1"));
        assertNull(CatalogHelper.validateProductPrice("7890000"));
        assertNull(CatalogHelper.validateProductPrice("9999999999"));
    }
}
