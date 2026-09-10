package com.bakeitoff.data.gemini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ApiKeyManagerTest {

    @Test
    fun `toggleKey gira entre as chaves e volta pra primeira depois da ultima`() {
        val manager = ApiKeyManager()
        val nomes = mutableListOf(manager.getActiveKeyName())

        repeat(manager.sizeKeys) {
            manager.toggleKey()
            nomes.add(manager.getActiveKeyName())
        }

        // Depois de girar sizeKeys vezes, volta pro nome inicial.
        assertEquals(nomes.first(), nomes.last())
        // E não fica preso no mesmo nome o tempo todo.
        assertNotEquals(nomes[0], nomes[1])
    }

    @Test
    fun `instancias diferentes nao compartilham estado`() {
        val a = ApiKeyManager()
        val b = ApiKeyManager()

        a.toggleKey()

        // Girar uma instância não deve afetar a outra — é exatamente o que
        // o singleton antigo impedia de testar (estado global compartilhado).
        assertNotEquals(a.getActiveKeyName(), b.getActiveKeyName())
    }
}
