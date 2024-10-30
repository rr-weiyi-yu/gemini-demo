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
        val mainContentAreas = getMainContentAreas(topic, overview)
        val detailedContent = getDetailedContent(topic, mainContentAreas)
        return validateAndEnhanceContent(topic, detailedContent)
    }

    private suspend fun getTopicOverview(topic: String): String {
        Log.d("SnapshotViewModel", "Requesting overview for topic: $topic")
        val response = generativeModel.generateContent(
            content { text("Provide a brief overview of the topic '$topic'.") }
        )
        val overview = response.text ?: throw Exception("Failed to generate topic overview")
        Log.d("SnapshotViewModel", "Received overview: $overview")
        return overview
    }

    private suspend fun getMainContentAreas(topic: String, overview: String): List<String> {
        Log.d("SnapshotViewModel", "Requesting main content areas for topic: $topic")
        val response = generativeModel.generateContent(
            content {
                text(
                    "Based on this overview: '$overview', list 3 main content areas that should be covered for a comprehensive understanding of '$topic'. do not contain \n in the response" +
                            "Strictly format the response as:" +
                            "Area 1\nArea2\nArea3"
                )
            }
        )
        val mainAreas = response.text?.split("\n")?.filter { it.isNotBlank() } ?: throw Exception("Failed to generate main content areas")
        Log.d("SnapshotViewModel", "Received main content areas: ${mainAreas.joinToString(", ")}")
        return mainAreas
    }

    private suspend fun getDetailedContent(topic: String, mainContentAreas: List<String>): List<ContentItem> {
        val detailedContent = mutableListOf<ContentItem>()
        for (area in mainContentAreas) {
            Log.d("SnapshotViewModel", "Requesting detailed content for area: $area")
            val response = generativeModel.generateContent(
                content { text("For the main content area '$area' of '$topic', provide a detailed explanation in about 100 words. Do not repeat the area title, do not contain \n in the response") }
            )
            val subtopics = response.text?.split("\n") ?: emptyList()
            Log.d("SnapshotViewModel", "Received detailed content for $area: ${subtopics.joinToString("\n")}")
            detailedContent.add(ContentItem(area, subtopics))
        }
        return detailedContent
    }

    private suspend fun validateAndEnhanceContent(topic: String, detailedContent: List<ContentItem>): List<ContentItem> {
        val contentString = detailedContent.joinToString("\n") { "${it.title}:\n${it.subtopics.joinToString("\n")}" }
        Log.d("SnapshotViewModel", "Final content for $topic:\n$contentString")
        // Validation and enhancement step is commented out, so we're just returning the original list
        return detailedContent
    }
}

data class ContentItem(val title: String, val subtopics: List<String>)

sealed class UiState {
    data object Initial : UiState()
    data object Loading : UiState()
    data class Success(val contentList: List<ContentItem>) : UiState()
    data class Error(val message: String) : UiState()
}
