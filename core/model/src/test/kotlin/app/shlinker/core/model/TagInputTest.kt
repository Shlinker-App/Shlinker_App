package app.shlinker.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TagInputTest {
    @Test fun separatorsTrimOuterSpacesPreserveInnerSpacesAndRemoveDuplicates() {
        assertEquals(
            listOf("one", "two words", "three", "four"),
            parseTagInput(" one,two words , three ,four,one\r\n three \n , "),
        )
    }

    @Test fun blankInputDoesNotCreateATag() {
        assertEquals(emptyList<String>(), parseTagInput(" , \n\r , "))
    }
}
