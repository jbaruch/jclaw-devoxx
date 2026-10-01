package jclaw

import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.llm.LLModel

/** Use Koog 1.3's real profiles rather than cloning an older model's capabilities. */
object Models {
    val flash: LLModel = when (val version = System.getenv("JCLAW_FLASH")) {
        "3.5" -> GoogleModels.Gemini3_5Flash
        "3.6" -> GoogleModels.Gemini3_6Flash
        null, "3.7" -> GoogleModels.Gemini3_7Flash
        else -> error("No verified built-in profile for JCLAW_FLASH=$version; use 3.5, 3.6 or 3.7")
    }

}
