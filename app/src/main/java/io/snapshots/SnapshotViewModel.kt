package io.snapshots

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val json = Json { ignoreUnknownKeys = true }

fun parseContentItems(jsonString: String): List<ContentItem> {
    return json.decodeFromString<List<ContentItem>>(jsonString)
}

class SnapshotViewModel : ViewModel() {
    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.apiKey
    )

    fun generateContent(topic: String) {
        _uiState.value = UiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentList = generateContentList(topic)
                _uiState.value = UiState.Success(contentList)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "An error occurred")
                Log.e("SnapshotViewModel", "Error generating content", e)
            }
        }
    }

    private suspend fun generateContentList(topic: String): List<ContentItem> {
        val overview = getTopicOverview(topic)
        return overview
    }

    private suspend fun getTopicOverview(topic: String): List<ContentItem> {
        Log.d("SnapshotViewModel", "Requesting key points for topic: $topic")
        val response = generativeModel.generateContent(
            content {
                text(
                    """
Provide a high-level overview and related areas of interest for $topic.

Based on this overview and related areas of interest, return content related to $topic that users might be interested in.

Please use a concise, fun, and easy-to-understand tone for the content and use emojis only when is actually suitable. Provide examples like code or table or any formats Markdown supports when is necessary.

Use markdown format for the summary, including tables, code snippets, images, and interactive elements like quizzes and polls to make the content engaging and fun.

Ensure each summary is short enough to fit on one screen, so users can keep swiping and exploring all the related content.

Strictly respond in the following format without adding any thing, no need to mark the response in markdown as json

[
{
"title": "title",
"summary": "summary"
}]
            """.trimIndent()
                )
            }
        )
        val text = response.text ?: throw Exception("Failed to generate topic overview")
        Log.d("SnapshotViewModel", "Received response: $text")
        return parseContentItems(text)
    }
}

@Serializable
data class ContentItem(
    val title: String,
    val summary: String,
)

sealed class UiState {
    data object Initial : UiState()
    data object Loading : UiState()
    data class Success(val contentList: List<ContentItem>) : UiState()
    data class Error(val message: String) : UiState()
}
