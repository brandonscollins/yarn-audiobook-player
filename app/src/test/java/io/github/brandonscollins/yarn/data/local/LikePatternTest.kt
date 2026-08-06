package io.github.brandonscollins.yarn.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class LikePatternTest {
    @Test
    fun `plain query is wrapped in wildcards`() {
        assertEquals("%dune%", likePattern("dune"))
    }

    @Test
    fun `like metacharacters are escaped, not honoured`() {
        assertEquals("""%50\%\_off%""", likePattern("50%_off"))
    }

    @Test
    fun `backslash is escaped first so it does not double the others`() {
        assertEquals("""%a\\\%b%""", likePattern("""a\%b"""))
    }
}
