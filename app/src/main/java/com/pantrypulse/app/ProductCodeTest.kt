package com.pantrypulse.app

import com.pantrypulse.app.domain.ProductCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductCodeTest {
    @Test fun plainNumbers() {
        assertEquals("5449000000996", ProductCode.fromQr("5449000000996"))
        assertEquals("05449000000996".drop(1), ProductCode.fromQr("05449000000996")) // GTIN-14 -> EAN-13
    }

    @Test fun gs1DigitalLink() {
        assertEquals("5449000000996", ProductCode.fromQr("https://id.gs1.org/01/05449000000996"))
        assertEquals("5449000000996", ProductCode.fromQr("https://brand.example/01/05449000000996/10/LOT42?exp=261231"))
    }

    @Test fun gs1ElementStrings() {
        assertEquals("5449000000996", ProductCode.fromQr("(01)05449000000996(17)261231"))
        assertEquals("5449000000996", ProductCode.fromQr("0105449000000996\u001d10LOT42"))
    }

    @Test fun linksWithTheNumberInTheQuery() {
        assertEquals("5449000000996", ProductCode.fromQr("https://shop.example/p?gtin=5449000000996&ref=qr"))
    }

    @Test fun codesWithoutAProductNumber() {
        assertNull(ProductCode.fromQr("https://example.com/menu"))
        assertNull(ProductCode.fromQr("WIFI:S:Home;T:WPA;P:secret;;"))
        assertNull(ProductCode.fromQr("   "))
        assertNull(ProductCode.fromQr("12345"))
    }
}
