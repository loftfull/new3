package com.knowledgehub.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkIdentityTest {
    @Test
    fun canonicalizesOnlyEquivalentComponents() {
        assertEquals(
            "https://example.org/a%2Fb?q=x%26y#section",
            LinkIdentity.parse(" HTTPS://EXAMPLE.ORG:443/a%2Fb?q=x%26y#section ")?.canonicalUrl,
        )
        assertEquals("http://example.org/", LinkIdentity.parse("http://example.org")?.canonicalUrl)
    }

    @Test
    fun rejectsNonWebAndAmbiguousInput() {
        assertNull(LinkIdentity.parse("javascript:alert(1)"))
        assertNull(LinkIdentity.parse("https://trusted.test@actual.test"))
        assertNull(LinkIdentity.parse("https://example.org/a b"))
        assertNull(LinkIdentity.parse("https://example.org:99999"))
    }

    @Test
    fun supportsInternationalDomainNamesAndIpv6() {
        assertEquals("xn--e1afmkfd.xn--p1ai", LinkIdentity.parse("https://пример.рф/")?.host)
        assertEquals("http://[::1]:8080/", LinkIdentity.parse("http://[::1]:8080")?.canonicalUrl)
    }
}
