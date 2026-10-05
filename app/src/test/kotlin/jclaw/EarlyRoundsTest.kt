package jclaw

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.prompt.message.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jclaw.domain.Scenario
import kotlinx.serialization.json.Json
import java.nio.file.Files

class EarlyRoundsTest : StringSpec({
    "round capability registries accumulate without exposing later workers or skill tools" {
        Mcp.boot("calendar-mcp", "organizer-mcp", onStderr = {}).use { mcp ->
            val skills = AgentSkills.discover(java.io.File("../skills"), trace = {})
            val actions = EarlyActions(mcp, null, {}, {}, { throw it })
            (1..4).forEach { number ->
                val names = earlyRoundTools(DemoStage(number), mcp, actions, if (number == 4) skills else AgentSkills.EMPTY).tools.map { it.name }.toSet()
                names shouldBe when (number) {
                    1 -> emptySet()
                    4 -> setOf("getCalendar", "getOrganizerSensitivity", "sendDecline", "__list_directory__", "__read_file__")
                    else -> setOf("getCalendar", "getOrganizerSensitivity", "sendDecline")
                }
            }
        }
    }

    "round three's model-selected real send validates its receipt and stores the literal message" {
        val root = Files.createTempDirectory("early-memory-")
        Files.createDirectory(root.resolve("documents"))
        val embedder = object : Embedder {
            override suspend fun embed(text: String) = Vector(listOf(1.0, 1.0))
            override fun diff(embedding1: Vector, embedding2: Vector) = 0.0
        }
        try {
            val memory = Memory.open(embedder, root, trace = {})
            Mcp.boot("calendar-mcp", "organizer-mcp", onStderr = {}).use { mcp ->
                var delivered = 0
                val tools = earlyRoundTools(DemoStage(3), mcp, EarlyActions(mcp, memory, { delivered++ }, {}, { throw it }), AgentSkills.EMPTY)
                var calls = 0
                val agent = AIAgent(promptExecutor = ScriptedExecutor { _, descriptors ->
                    calls++
                    if (calls == 1) Message.Assistant(MessagePart.Tool.Call("send-1", "sendDecline",
                        """{"eventId":"${Scenario.EVENT_ID}","organizerName":"${Scenario.ORGANIZER}","message":"Literal test message.","flavor":"DEADLINE"}"""), ResponseMetaInfo.Empty)
                    else {
                        val finish = descriptors.single { it.name == "finalize_task_result" }
                        Message.Assistant(MessagePart.Tool.Call("finish-1", finish.name, """{"result":"Delivered."}"""), ResponseMetaInfo.Empty)
                    }
                }, llmModel = Models.flash, systemPrompt = Persona.PROMPT, toolRegistry = tools,
                    strategy = earlyRoundStrategy(DemoStage(3), tools.tools))
                try { agent.run("Send Literal test message.") shouldBe JclawResult.ChatReply("Delivered.") }
                finally { agent.close() }
                delivered shouldBe 1
                val records = root.resolve("documents").toFile().listFiles()!!.toList()
                records.size shouldBe 1
                records.single().readText() shouldContain "Literal test message."
                Memory.open(embedder, root, trace = {}).usedFlavors(Scenario.ORGANIZER).map { it.name } shouldBe listOf("DEADLINE")
            }
        } finally { root.toFile().deleteRecursively() }
    }

    "round three conversation survives a new agent and system catalog changes for round four" {
        val directory = Files.createTempDirectory("round-continuity-")
        val file = directory.resolve("conversation.json")
        try {
            val first = Conversation("No skills yet", file)
            val agent = AIAgent(promptExecutor = ScriptedExecutor { _, descriptors ->
                val finish = descriptors.single { it.name == "finalize_task_result" }
                Message.Assistant(MessagePart.Tool.Call("finish", finish.name, """{"result":"The release is delayed. Next update tomorrow."}"""), ResponseMetaInfo.Empty)
            }, llmModel = Models.flash, strategy = earlyRoundStrategy(DemoStage(3), emptyList())) {
                install(ChatMemory) { first.configure(this) }
            }
            try { first.run(agent, "Draft a release update.") } finally { agent.close() }
            val second = Conversation("New runtime catalog: corporate-speak", file)
            val next = AIAgent(promptExecutor = ScriptedExecutor { prompt, _ ->
                val text = prompt.messages.joinToString { it.textContent() }
                text shouldContain "The release is delayed. Next update tomorrow."
                text shouldContain "New runtime catalog: corporate-speak"
                Message.Assistant("Rewritten.", ResponseMetaInfo.Empty)
            }, llmModel = Models.flash) { install(ChatMemory) { second.configure(this) } }
            try { second.run(next, "Rewrite that message.") shouldBe "Rewritten." } finally { next.close() }
        } finally { directory.toFile().deleteRecursively() }
    }
})
