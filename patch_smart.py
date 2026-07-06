import re

with open("app/src/main/java/com/dipdev/aiautocaptioner/ui/recorder/SmartRecorderViewModel.kt", "r") as f:
    content = f.read()

# Add imports
content = content.replace("import androidx.lifecycle.ViewModel\nimport androidx.lifecycle.viewModelScope",
"""import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.extensions.stateInDefault
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map""")

content = content.replace("import kotlinx.coroutines.flow.MutableStateFlow\nimport kotlinx.coroutines.flow.asStateFlow\n", "")

# Add State class before the ViewModel
state_class = """
data class SmartRecorderState(
    val recordingMode: RecordingMode = RecordingMode.CAMERA,
    val selectedBackground: BackgroundState = BackgroundState.SolidColor(Color.Black),
    val recordingState: RecordingState = RecordingState.IDLE,
    val elapsedSeconds: Int = 0,
    val finishedProjectId: String? = null,
    val isAudioMuted: Boolean = false,
    val showGrid: Boolean = false,
    val countdownTimer: Int = 0,
    val showTeleprompter: Boolean = false,
    val teleprompterText: String = "",
    val audioAmplitude: Float = 0f,
    val isCountdownActive: Boolean = false,
    val countdownRemaining: Int = 0
) : UiState

"""

content = content.replace("@HiltViewModel", state_class + "@HiltViewModel")

# Change superclass
content = content.replace(") : ViewModel() {", ") : BaseViewModel<SmartRecorderState, UiEvent, UiEffect>(SmartRecorderState()) {")

# Replace properties
properties = [
    ("recordingMode", "RecordingMode", "RecordingMode.CAMERA"),
    ("selectedBackground", "BackgroundState", "BackgroundState.SolidColor(Color.Black)"),
    ("recordingState", "RecordingState", "RecordingState.IDLE"),
    ("elapsedSeconds", "Int", "0"),
    ("finishedProjectId", "String?", "null"),
    ("isAudioMuted", "Boolean", "false"),
    ("showGrid", "Boolean", "false"),
    ("countdownTimer", "Int", "0"),
    ("showTeleprompter", "Boolean", "false"),
    ("teleprompterText", "String", '""'),
    ("audioAmplitude", "Float", "0f"),
    ("isCountdownActive", "Boolean", "false"),
    ("countdownRemaining", "Int", "0")
]

for prop, typ, init in properties:
    old_str = f"    private val _{prop} = MutableStateFlow<{typ}>({init})\n    val {prop} = _{prop}.asStateFlow()"
    if old_str not in content:
        # try without generic type
        old_str = f"    private val _{prop} = MutableStateFlow({init})\n    val {prop} = _{prop}.asStateFlow()"
    new_str = f"    val {prop}: StateFlow<{typ}> = uiState.map {{ it.{prop} }}.stateInDefault(viewModelScope, currentState.{prop})"
    content = content.replace(old_str, new_str)

# Replace assignments
content = re.sub(r'_(.*?)\.value\s*=\s*(.*?)\n', r'setState { copy(\1 = \2) }\n', content)
content = re.sub(r'_(.*?)\.value\s*\+=\s*(.*?)\n', r'setState { copy(\1 = currentState.\1 + \2) }\n', content)

# Add handleEvent
content = content.replace("    init {\n", "    override fun handleEvent(event: UiEvent) {}\n\n    init {\n")

# Specifically fix _recordingState.value == RecordingState.IDLE
content = content.replace("_recordingState.value", "currentState.recordingState")
content = content.replace("_isCountdownActive.value", "currentState.isCountdownActive")
content = content.replace("_countdownTimer.value", "currentState.countdownTimer")
content = content.replace("_recordingMode.value", "currentState.recordingMode")
content = content.replace("_isAudioMuted.value", "currentState.isAudioMuted")
content = content.replace("_selectedBackground.value", "currentState.selectedBackground")

# Specifically fix the toggle methods
content = content.replace("setState { copy(isAudioMuted = !currentState.isAudioMuted) }", "setState { copy(isAudioMuted = !currentState.isAudioMuted) }")
# Since we replaced _isAudioMuted.value = !_isAudioMuted.value earlier:
content = content.replace("setState { copy(isAudioMuted = !_isAudioMuted.value) }", "setState { copy(isAudioMuted = !currentState.isAudioMuted) }")
content = content.replace("setState { copy(showGrid = !_showGrid.value) }", "setState { copy(showGrid = !currentState.showGrid) }")
content = content.replace("setState { copy(showTeleprompter = !_showTeleprompter.value) }", "setState { copy(showTeleprompter = !currentState.showTeleprompter) }")

with open("app/src/main/java/com/dipdev/aiautocaptioner/ui/recorder/SmartRecorderViewModel.kt", "w") as f:
    f.write(content)
