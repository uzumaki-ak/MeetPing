package com.yourname.meetinglistener.ai

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.yourname.meetinglistener.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * ClaudeClient.kt
 *
 * PURPOSE:
 * Handles all API calls to Anthropic's Claude API
 * Provides question answering and summarization capabilities
 *
 * FEATURES:
 * - Async API calls using coroutines
 * - Error handling with fallback
 * - Token usage tracking
 * - Latency monitoring
 */
class ClaudeClient(private val apiKey: String) {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://api.anthropic.com/v1/messages"

    companion object {
        private const val MODEL = "claude-sonnet-4-20250514" // Latest Claude Sonnet
        private const val MAX_TOKENS = 1000
    }

    /**
     * Answer a user's question based on meeting context
     * @param question User's question
     * @param context Meeting context information
     * @return LLMResponse with answer
     */
    suspend fun answerQuestion(question: String, context: String): LLMResponse {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                val prompt = buildQuestionPrompt(question, context)
                val response = makeApiCall(prompt)

                val latency = System.currentTimeMillis() - startTime

                LLMResponse(
                    content = response.content.firstOrNull()?.text ?: "",
                    provider = "claude",
                    success = true,
                    tokensUsed = response.usage.outputTokens,
                    latencyMs = latency
                )
            } catch (e: Exception) {
                LLMResponse(
                    content = "",
                    provider = "claude",
                    success = false,
                    errorMessage = e.message ?: "Unknown error",
                    latencyMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }

    /**
     * Generate a summary of meeting content
     * @param request Summary request with content and type
     * @return Summarized text
     */
    suspend fun generateSummary(request: SummaryRequest): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildSummaryPrompt(request)
                val response = makeApiCall(prompt)
                response.content.firstOrNull()?.text ?: ""
            } catch (e: Exception) {
                "Error generating summary: ${e.message}"
            }
        }
    }

    /**
     * Build prompt for question answering
     */
    private fun buildQuestionPrompt(question: String, context: String): String {
        return """
You are an AI meeting assistant. Your job is to answer questions about an ongoing meeting based on the provided context.

MEETING CONTEXT:
$context

USER'S QUESTION:
$question

INSTRUCTIONS:
- Provide a direct, concise answer (2-4 sentences max)
- If the information isn't in the context, say "I don't have that information from the current meeting"
- Focus on being helpful and accurate
- Don't make up information

ANSWER:
        """.trimIndent()
    }

    /**
     * Build prompt for summarization
     */
    private fun buildSummaryPrompt(request: SummaryRequest): String {
        return when (request.summaryType) {
            SummaryType.MICRO -> """
Summarize the following meeting segment in 2-3 concise sentences. Focus on key points, decisions, and action items.

CONTENT:
${request.content}

SUMMARY:
            """.trimIndent()

            SummaryType.SECTION -> """
Compress the following meeting content into a single sentence highlighting only the most important point.

CONTENT:
${request.content}

COMPRESSED SUMMARY:
            """.trimIndent()

            SummaryType.FINAL -> """
Create a comprehensive end-of-meeting summary with:
1. Main topics discussed
2. Key decisions made
3. Action items and assignments
4. Important deadlines

MEETING CONTENT:
${request.content}

FINAL SUMMARY:
            """.trimIndent()

            SummaryType.DECISION -> """
Extract only the decisions made from this meeting content. List each decision as a bullet point.

CONTENT:
${request.content}

DECISIONS:
            """.trimIndent()

            SummaryType.ACTION_ITEM -> """
Extract only action items and task assignments from this content. Format: "Task - Assigned to [Name] - Deadline [if mentioned]"

CONTENT:
${request.content}

ACTION ITEMS:
            """.trimIndent()
        }
    }

    /**
     * Make actual API call to Claude
     */
    private fun makeApiCall(userMessage: String): ClaudeResponse {
        val requestBody = ClaudeRequest(
            model = MODEL,
            maxTokens = MAX_TOKENS,
            messages = listOf(
                Message(role = "user", content = userMessage)
            )
        )

        val jsonBody = gson.toJson(requestBody)
        val body = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("API call failed: ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response")

            return gson.fromJson(responseBody, ClaudeResponse::class.java)
        }
    }

    // API request/response data classes
    private data class ClaudeRequest(
        val model: String,
        @SerializedName("max_tokens")
        val maxTokens: Int,
        val messages: List<Message>
    )

    private data class Message(
        val role: String,
        val content: String
    )

    private data class ClaudeResponse(
        val content: List<ContentBlock>,
        val usage: Usage
    )

    private data class ContentBlock(
        val type: String,
        val text: String
    )

    private data class Usage(
        @SerializedName("output_tokens")
        val outputTokens: Int
    )
}

// ==========================================
// GeminiClient.kt
// ==========================================

/**
 * GeminiClient.kt
 *
 * PURPOSE:
 * Handles API calls to Google's Gemini API
 * Provides same interface as ClaudeClient for easy switching
 */
class GeminiClient(private val apiKey: String) {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    companion object {
        private const val MODEL = "gemini-2.0-flash-exp"
    }

    suspend fun answerQuestion(question: String, context: String): LLMResponse {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                val prompt = """
Meeting Context:
$context

Question: $question

Provide a brief, direct answer (2-4 sentences max). If information isn't available, say so clearly.
                """.trimIndent()

                val response = makeApiCall(prompt)
                val answer = response.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull()?.text ?: ""

                LLMResponse(
                    content = answer,
                    provider = "gemini",
                    success = true,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                LLMResponse(
                    content = "",
                    provider = "gemini",
                    success = false,
                    errorMessage = e.message,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }

    suspend fun generateSummary(request: SummaryRequest): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = when (request.summaryType) {
                    SummaryType.MICRO -> "Summarize this in 2-3 sentences:\n${request.content}"
                    SummaryType.SECTION -> "Compress this into one sentence:\n${request.content}"
                    else -> "Summarize:\n${request.content}"
                }

                val response = makeApiCall(prompt)
                response.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull()?.text ?: ""
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    private fun makeApiCall(prompt: String): GeminiResponse {
        val requestBody = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            )
        )

        val jsonBody = gson.toJson(requestBody)
        val body = jsonBody.toRequestBody("application/json".toMediaType())

        val url = "$baseUrl/$MODEL:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("API call failed: ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response")

            return gson.fromJson(responseBody, GeminiResponse::class.java)
        }
    }

    private data class GeminiRequest(
        val contents: List<Content>
    )

    private data class Content(
        val parts: List<Part>
    )

    private data class Part(
        val text: String
    )

    private data class GeminiResponse(
        val candidates: List<Candidate>?
    )

    private data class Candidate(
        val content: Content
    )
}

// ==========================================
// EuronClient.kt
// ==========================================

/**
 * EuronClient.kt
 *
 * PURPOSE:
 * Handles API calls to Euron's API
 * Provides OpenAI-compatible interface
 */
class EuronClient(private val apiKey: String) {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://api.euron.one/api/v1/euri/chat/completions"

    companion object {
        private const val MODEL = "gpt-4.1-nano"
    }

    suspend fun answerQuestion(question: String, context: String): LLMResponse {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                val prompt = """
Context: $context

Question: $question

Answer briefly (2-4 sentences):
                """.trimIndent()

                val response = makeApiCall(prompt)
                val answer = response.choices?.firstOrNull()?.message?.content ?: ""

                LLMResponse(
                    content = answer,
                    provider = "euron",
                    success = true,
                    tokensUsed = response.usage?.totalTokens,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                LLMResponse(
                    content = "",
                    provider = "euron",
                    success = false,
                    errorMessage = e.message,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }

    suspend fun generateSummary(request: SummaryRequest): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = "Summarize concisely:\n${request.content}"
                val response = makeApiCall(prompt)
                response.choices?.firstOrNull()?.message?.content ?: ""
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    private fun makeApiCall(userMessage: String): EuronResponse {
        val requestBody = EuronRequest(
            model = MODEL,
            messages = listOf(
                EuronMessage(role = "user", content = userMessage)
            ),
            maxTokens = 1000,
            temperature = 0.7
        )

        val jsonBody = gson.toJson(requestBody)
        val body = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("API call failed: ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response")

            return gson.fromJson(responseBody, EuronResponse::class.java)
        }
    }

    private data class EuronRequest(
        val model: String,
        val messages: List<EuronMessage>,
        @SerializedName("max_tokens")
        val maxTokens: Int,
        val temperature: Double
    )

    private data class EuronMessage(
        val role: String,
        val content: String
    )

    private data class EuronResponse(
        val choices: List<Choice>?,
        val usage: Usage?
    )

    private data class Choice(
        val message: EuronMessage
    )

    private data class Usage(
        @SerializedName("total_tokens")
        val totalTokens: Int
    )
}