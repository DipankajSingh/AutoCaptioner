#!/bin/bash
sed -i '1s/^/import androidx.compose.runtime.getValue\n/' app/src/main/java/com/dipdev/aiautocaptioner/ui/components/CoreComponents.kt
sed -i '1s/^/import androidx.compose.ui.unit.dp\n/' app/src/main/java/com/dipdev/aiautocaptioner/ui/components/CoreComponents.kt
sed -i '1s/^/import androidx.compose.ui.unit.sp\n/' app/src/main/java/com/dipdev/aiautocaptioner/ui/components/CoreComponents.kt
sed -i '1s/^/import androidx.compose.foundation.layout.height\n/' app/src/main/java/com/dipdev/aiautocaptioner/ui/components/CoreComponents.kt
sed -i 's/androidx\.compose\.foundation\.layout\.Modifier/androidx.compose.ui.Modifier/g' app/src/main/java/com/dipdev/aiautocaptioner/ui/components/CoreComponents.kt
