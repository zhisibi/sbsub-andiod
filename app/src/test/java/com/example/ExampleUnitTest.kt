package com.example

import com.example.data.generator.SingBoxConfigGenerator
import com.example.data.model.ProxyNode
import com.example.data.parser.SubscriptionParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun parseVlessUri_isCorrect() {
        val uri = "vless://12345678-1234-1234-1234-1234567890ab@example.com:443?type=ws&security=tls&sni=example.com&path=/ws#TestVless"
        val nodes = SubscriptionParser.parseContent(uri)

        assertEquals(1, nodes.size)
        val node = nodes[0]
        assertEquals("TestVless", node.name)
        assertEquals("vless", node.protocol)
        assertEquals("example.com", node.server)
        assertEquals(443, node.port)
        assertEquals("12345678-1234-1234-1234-1234567890ab", node.uuidOrPassword)
        assertTrue(node.tls)
        assertEquals("example.com", node.sni)
        assertEquals("ws", node.network)
        assertEquals("/ws", node.path)
    }

    @Test
    fun generateSingBoxConfig_isCorrect() {
        val node = ProxyNode(
            name = "Node1",
            protocol = "vless",
            server = "1.2.3.4",
            port = 443,
            uuidOrPassword = "uuid-123",
            tls = true
        )

        val jsonStr = SingBoxConfigGenerator.generateJson(listOf(node))
        val root = JSONObject(jsonStr)

        assertNotNull(root.optJSONObject("log"))
        assertNotNull(root.optJSONObject("dns"))
        assertNotNull(root.optJSONArray("inbounds"))
        assertNotNull(root.optJSONArray("outbounds"))
        assertNotNull(root.optJSONObject("route"))
    }
}
