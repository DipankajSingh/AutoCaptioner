import re

with open("app/src/main/java/com/dipdev/aiautocaptioner/ui/videoeditor/timeline/TimelineComponents.kt", "r") as f:
    content = f.read()

# Update parameters
content = content.replace(
    "hasGapBefore: Boolean\n)",
    "hasGapBefore: Boolean,\n    onTrimClip: (String, Long, Long) -> Unit,\n    pixelsPerMs: Float,\n    totalEditedMs: Long\n)"
)

# Remove the old Drag Grip
old_drag_grip = """        // Drag Grip
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                )
            }
        }"""

new_handles = """        // CapCut-style Trim Handles
        if (isSelected) {
            // Left Trim Handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(16.dp)
                    .background(Color.White, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                    .pointerInput(clip.id + "_left") {
                        var accumulatedDeltaX = 0f
                        detectDragGestures(
                            onDragStart = { 
                                onDragStateChange(true)
                                accumulatedDeltaX = 0f 
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDeltaX += dragAmount.x
                                val deltaMs = (accumulatedDeltaX / pixelsPerMs).toLong()
                                if (deltaMs != 0L) {
                                    accumulatedDeltaX -= (deltaMs * pixelsPerMs)
                                    val newStart = (clip.startTrimMs + deltaMs).coerceIn(0L, clip.endTrimMs - 100L)
                                    if (newStart != clip.startTrimMs) {
                                        onTrimClip(clip.id, newStart, clip.endTrimMs)
                                    }
                                }
                            },
                            onDragEnd = { onDragStateChange(false) },
                            onDragCancel = { onDragStateChange(false) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.width(2.dp).height(12.dp).background(Color.Black.copy(alpha=0.3f), RoundedCornerShape(1.dp)))
            }
            
            // Right Trim Handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(16.dp)
                    .background(Color.White, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .pointerInput(clip.id + "_right") {
                        var accumulatedDeltaX = 0f
                        detectDragGestures(
                            onDragStart = { 
                                onDragStateChange(true)
                                accumulatedDeltaX = 0f 
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDeltaX += dragAmount.x
                                val deltaMs = (accumulatedDeltaX / pixelsPerMs).toLong()
                                if (deltaMs != 0L) {
                                    accumulatedDeltaX -= (deltaMs * pixelsPerMs)
                                    val newEnd = maxOf(clip.startTrimMs + 100L, clip.endTrimMs + deltaMs)
                                    if (newEnd != clip.endTrimMs) {
                                        onTrimClip(clip.id, clip.startTrimMs, newEnd)
                                    }
                                }
                            },
                            onDragEnd = { onDragStateChange(false) },
                            onDragCancel = { onDragStateChange(false) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.width(2.dp).height(12.dp).background(Color.Black.copy(alpha=0.3f), RoundedCornerShape(1.dp)))
            }
        }"""

content = content.replace(old_drag_grip, new_handles)

with open("app/src/main/java/com/dipdev/aiautocaptioner/ui/videoeditor/timeline/TimelineComponents.kt", "w") as f:
    f.write(content)

print("Patched TimelineComponents.kt")
