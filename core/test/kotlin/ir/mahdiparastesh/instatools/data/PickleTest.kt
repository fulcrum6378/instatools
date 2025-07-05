package ir.mahdiparastesh.instatools.data

import ir.mahdiparastesh.instatools.data.Pickle.Companion.branch
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import java.io.File
import java.io.FileInputStream

object PickleTest {
    private val root = File("test_pickles")

    @Test
    fun main() {
        val model = listOf("One", "Two", "Three")

        // save the data model as a Pickle
        val type = Pickle.Type.SAVED
        val acc = 1L
        val pickle = Pickle(root, acc, type, null)
        pickle.save(model)

        // test if the saved file corresponds our data model
        val branch = branch(root, type, acc)
        val leaf = File(branch, "${type.name.lowercase()}.json")
        val savedJson = FileInputStream(leaf).use { String(it.readBytes()) }
        assertEquals(Json.encodeToString(model), savedJson)

        // test if the restored data corresponds our initial data
        assertIterableEquals(model, pickle.restore<List<String>>())

        // make the Pickle expire
        leaf.setLastModified(leaf.lastModified() - pickle.type.lifespan.toLong())
        assertNull(pickle.restore<List<String>>())
    }

    @AfterAll
    @JvmStatic
    fun finish() {
        root.deleteRecursively()
    }
}
