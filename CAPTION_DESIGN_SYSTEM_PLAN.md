# Caption Design System - Complete Overhaul Plan

## Executive Summary

The current caption system has a **solid foundation** (40+ style properties, 5 display modes, 10 animations, Canvas-based 60fps rendering, clean MVI architecture) but has critical gaps that prevent users from designing captions "any way they want." This plan addresses every gap with a phased implementation.

---

## Current State Assessment

### What Works Well (Keep As-Is)
- Canvas-based rendering engine with paint caching (60fps)
- MVI architecture (StyleViewModel, events, undo/redo)
- 5 display modes, 10 enter/exit animations, 5 karaoke modes
- 5 emphasis types per word
- RTL language support
- BitmapOverlay export pipeline
- Live preview in style editor
- Room DB persistence with 30-level undo stack

### Critical Gaps to Fix
| Gap | Impact | Priority |
|-----|--------|----------|
| No font picker UI (fontFamily exists but unused) | Users can't change fonts | **P0** |
| No bundled custom fonts | Only system fonts available | **P0** |
| No gradient text (secondaryColor unused) | Limited visual appeal | **P1** |
| No shadow UI (shadow fields exist, no controls) | No depth effects | **P1** |
| No text opacity control | Can't make text semi-transparent | **P1** |
| No exit animation UI (field exists, no picker) | Incomplete animation control | **P1** |
| No emphasis type picker (hardcoded to BOUNCE) | No emphasis variety | **P1** |
| No position X control (always center) | Can't left/right align block | **P2** |
| No text transform (UPPERCASE etc.) | Common need for captions | **P2** |
| No line height control | Tight/loose line spacing | **P2** |
| No outline-only mode | Neon/sign effects impossible | **P2** |
| No glow/neon effect | Popular TikTok/Reels style | **P3** |
| No blur background | Modern glass-morphism look | **P3** |

---

## Architecture Decision: Rendering Approach

**Chosen: Enhance existing Canvas-based engine** (not migrate to ASS/FFmpeg)

Rationale:
1. The current Canvas engine already handles 60fps with paint caching
2. Android Canvas has native support for everything we need: `LinearGradient`, `BlurMaskFilter`, `setShadowLayer`, `Typeface.createFromAsset`
3. ASS format is overkill — it adds complexity for export-only benefit, but we burn-in via bitmap anyway
4. FFmpeg is commented out and adds 20-50MB APK size
5. Adding a new rendering backend would require rewriting CaptionRenderer, CaptionPaints, CaptionOverlayEffect, and all preview paths — massive risk for no gain

**What we gain by enhancing Canvas:**
- `Shader`-based gradient text (LinearGradient/RadialGradient)
- `BlurMaskFilter` for glow/neon effects  
- `Typeface.createFromAsset()` for custom font loading
- `Paint.setShadowLayer()` for drop shadows (already partially wired)
- All rendering stays in Kotlin, no native dependencies

---

## Phase 1: Font System (P0 - Highest Priority)

### 1A. Bundle Custom Fonts

**Files to create/modify:**
- `app/src/main/assets/fonts/` — new directory with bundled TTF/OTF files
- `engine/CaptionPaints.kt:97-119` — update `resolveTypeface()` to load from assets

**Font bundle (12 curated fonts covering major use cases):**
```
assets/fonts/
  montserrat_regular.ttf, montserrat_bold.ttf, montserrat_black.ttf
  bebas_neue_regular.ttf          (condensed/titles)
  pacifico_regular.ttf            (handwritten)
  oswald_bold.ttf                 (condensed bold)
  roboto_regular.ttf, roboto_bold.ttf
  impact_regular.ttf              (meme style)
  playfair_display_bold.ttf       (elegant)
  space_mono_regular.ttf          (monospace/tech)
  noto_sans_regular.ttf           (multi-script fallback)
```

**Typeface resolution logic (CaptionPaints.kt):**
```kotlin
private fun resolveTypeface(style: CaptionStyleEntity): Typeface {
    if (cachedTypeface != null && ...) return cachedTypeface!!
    
    val tf = try {
        // 1. Try bundled font from assets
        val assetPath = "fonts/${style.fontFamily.lowercase().replace(" ", "_")}.ttf"
        val typefaceFromAsset = Typeface.createFromAsset(appContext.assets, assetPath)
        // Apply weight/style
        val styleInt = when {
            style.fontWeight > 600 && style.isItalic -> Typeface.BOLD_ITALIC
            style.fontWeight > 600 -> Typeface.BOLD
            style.isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        Typeface.create(typefaceFromAsset, styleInt)
    } catch (_: Exception) {
        // 2. Fallback to system font
        val base = Typeface.create(style.fontFamily, Typeface.NORMAL)
        // ... existing logic
    }
    // cache and return
}
```

**Impact:** CaptionPaints needs access to `Context` (currently it's an `object` with no context). Options:
- Pass `Context` to `configure()` method (preferred — minimal change)
- Or make CaptionPaints a `@Singleton` injected via Hilt (more invasive)

**Recommended:** Add `context: Context` parameter to `CaptionPaints.configure()` and `CaptionRenderer.draw()`. Update all call sites (PreviewSection, StylePreview, CaptionOverlayEffect).

### 1B. Font Picker UI

**New file:** `ui/videoeditor/style/tabs/FontPickerSheet.kt`

**Design:** Modal bottom sheet with:
- Grid of font previews (each row shows font name rendered in that font)
- Currently selected font highlighted
- Preview text: "The quick brown fox" (shows the actual font)
- Categories: Sans-Serif, Serif, Display, Handwritten, Monospace
- Search bar at top

**Integration:** Add `FONT` to `TextSubTool` enum in `TextTab.kt`
- New SubToolButton: `SubToolButton(FeatherIcons.Type, "Font") { activeTool = TextSubTool.FONT }`
- When tapped, open FontPickerSheet
- On selection: `onFontFamilyChange(fontFamily: String)`

**TextTab.kt changes:**
- Add `onFontFamilyChange: (String) -> Unit` parameter
- Add `FONT` to `TextSubTool` enum
- Add `SubToolButton` for Font
- Add `TextSubTool.FONT -> FontPickerSheet(...)` case

**StylePanel.kt changes:**
- Wire `onFontFamilyChange` to ViewModel event

**StyleViewModel.kt changes:**
- No changes needed (already uses generic `UpdateStyle` event)

### 1C. Font Weight Expanded

**Current:** 3 options (400 Light, 700 Bold, 900 Black)
**New:** Full weight picker with visual preview

**TextTab.kt Weight tool change:**
Replace the 3 `FilterChip` with a horizontal scrollable row of weight options:
- 100 Thin, 200 ExtraLight, 300 Light, 400 Regular, 500 Medium, 600 SemiBold, 700 Bold, 800 ExtraBold, 900 Black
- Each chip shows the weight name rendered in that weight (if font supports it)

---

## Phase 2: Missing Style Controls (P1)

### 2A. Gradient Text

**CaptionStyleEntity.kt:**
- Add `gradientDirection: GradientDirection = GradientDirection.NONE` (new enum: NONE, LEFT_RIGHT, TOP_BOTTOM, DIAGONAL)
- `secondaryColor` already exists (0xFFFFFFFF default) — wire it up
- Add `gradientStartColor` / `gradientEndColor` as aliases or repurpose `textColor` + `secondaryColor`

**CaptionPaints.kt changes:**
```kotlin
// In configure(), when gradient is active:
if (style.gradientDirection != GradientDirection.NONE) {
    val shader = when (style.gradientDirection) {
        GradientDirection.LEFT_RIGHT -> LinearGradient(
            0f, 0f, textWidth, 0f,
            style.textColor.toInt(), style.secondaryColor.toInt(),
            Shader.TileMode.CLAMP
        )
        GradientDirection.TOP_BOTTOM -> LinearGradient(
            0f, top, 0f, bottom,
            style.textColor.toInt(), style.secondaryColor.toInt(),
            Shader.TileMode.CLAMP
        )
        // ...
    }
    text.shader = shader
}
```

**Challenge:** LinearGradient needs the text bounds. We can't know these until layout time. Solution: set the shader in `CaptionRenderer.drawWord()` after measuring word width, not in `CaptionPaints.configure()`.

**CaptionRenderer.kt changes in `drawWord()`:**
```kotlin
// Before drawing text fill:
if (style.gradientDirection != GradientDirection.NONE && isBgPass.not()) {
    val gradient = LinearGradient(
        x, lineTop, x + wordW, lineBot,
        style.textColor.toInt(), style.secondaryColor.toInt(),
        Shader.TileMode.CLAMP
    )
    CaptionPaints.text.shader = gradient
} else {
    CaptionPaints.text.shader = null
}
```

**ColorTab.kt changes:**
- Add `GRADIENT` to `ColorSubTool` enum
- New SubToolButton: "Gradient" with direction picker + secondary color picker
- Shows gradient direction chips (None, Left→Right, Top→Bottom, Diagonal) + AdvancedColorPicker for secondaryColor

### 2B. Shadow Controls

**Current state:** `shadowColor`, `shadowRadius`, `shadowOffsetX`, `shadowOffsetY` exist in entity but no UI, and the shadow is applied then cleared before text fill.

**CaptionPaints.kt changes:**
```kotlin
// In configure(), apply shadow to outline paint (not text):
if (style.shadowRadius > 0f) {
    outline.setShadowLayer(
        style.shadowRadius * baseScale,
        style.shadowOffsetX * baseScale,
        style.shadowOffsetY * baseScale,
        style.shadowColor.toInt()
    )
}
```

**CaptionRenderer.kt changes:**
- Don't clear shadow before Pass 2 — let it persist for the outline pass
- Or: apply shadow to text paint selectively during Pass 1

**ColorTab.kt changes:**
- Add `SHADOW` to `ColorSubTool` enum
- New SubToolButton: "Shadow" 
- Opens sheet with: Shadow Color picker, Shadow Radius slider (0-20), Offset X slider (-10..10), Offset Y slider (-10..10)

**CaptionStyleEntity.kt:** Already has all shadow fields — no schema change needed.

### 2C. Text Opacity

**CaptionStyleEntity.kt:** Add `textOpacity: Float = 1f`

**CaptionRenderer.kt:** Apply to text alpha:
```kotlin
CaptionPaints.text.alpha = (255 * xfm.alpha * style.textOpacity).toInt()
```

**TextTab.kt:** Add `OPACITY` to `TextSubTool`, add SubToolButton "Opacity" with slider 0-100%.

### 2D. Exit Animation UI

**Current:** `wordExitAnimation` field exists, but AnimationTab only shows enter animation picker.

**AnimationTab.kt changes:**
- Add `EXIT` to `AnimSubTool` enum
- Add SubToolButton: "Exit Anim"
- Show same animation type picker as enter, but bound to `wordExitAnimation`
- Wire through `StylePanel.kt` → ViewModel → `StyleEditorUiEvent.UpdateStyle("wordExitAnimation")`

### 2E. Emphasis Type Picker

**Current:** Long-pressing a word in CaptionEditor toggles emphasis, but always uses `BOUNCE`.

**CaptionEditorViewModel.kt changes:**
- When toggling emphasis, show a picker for emphasis type
- Store the emphasis type in `CaptionWordEntity.emphasisType` (field already exists)

**CaptionEditorScreen.kt / WordChip.kt changes:**
- On long-press, show a small popup menu with emphasis options: None, Bounce, Scale, Shake, Color Pop
- Each option shows a brief animated preview

---

## Phase 3: Advanced Positioning & Layout (P2)

### 3A. Position X Control

**CaptionStyleEntity.kt:** `positionX` already exists (default 0.5). No schema change.

**TextTab.kt or new LayoutSubTool:**
- Add `POSITION` to `TextSubTool`
- Show a 2D position pad: horizontal (0.1-0.9) and vertical (0.1-0.9) sliders
- Or: a visual drag handle on the preview (already exists for Y, add X)

**StylePreview.kt changes:**
- Add horizontal drag gesture (currently only vertical drag for positionY)
- On horizontal drag: update `positionX`

### 3B. Text Transform

**CaptionStyleEntity.kt:** Add `textTransform: TextTransform = TextTransform.NONE`
```kotlin
enum class TextTransform { NONE, UPPERCASE, LOWERCASE, TITLE_CASE, SENTENCE_CASE }
```

**CaptionUtils.kt `sanitize()` function:** Apply transform:
```kotlin
fun sanitize(text: String, style: CaptionStyleEntity): String {
    var result = text
    if (style.removePunctuation) result = result.replace(Regex("[\\p{Punct}]"), "")
    result = when (style.textTransform) {
        TextTransform.UPPERCASE -> result.uppercase()
        TextTransform.LOWERCASE -> result.lowercase()
        TextTransform.TITLE_CASE -> result.split(" ").joinToString(" ") { 
            it.replaceFirstChar { c -> c.uppercaseChar() } 
        }
        TextTransform.SENTENCE_CASE -> result.replaceFirstChar { it.uppercaseChar() }
        TextTransform.NONE -> result
    }
    return result
}
```

**TextTab.kt:** Add `TRANSFORM` subtool with chip selection.

**DB Migration:** Add column to `caption_styles` table (Migration 13→14).

### 3C. Line Height Control

**CaptionStyleEntity.kt:** Add `lineHeight: Float = 1.2f` (multiplier)

**CaptionRenderer.kt:** Replace hardcoded `lineH`:
```kotlin
val lineH = (fm.bottom - fm.top) * style.lineHeight
```

**TextTab.kt:** Add `LINE_HEIGHT` subtool with slider (0.8 - 2.5).

**DB Migration:** Add column.

### 3D. Outline-Only Mode (Neon/Sign Effect)

**CaptionStyleEntity.kt:** Add `outlineOnly: Boolean = false`

**CaptionRenderer.kt:** In Pass 2 (text fill), skip fill when `outlineOnly`:
```kotlin
if (style.outlineOnly) {
    // Draw only the outline, no fill — creates neon/sign effect
    CaptionPaints.outline.color = style.highlightColor.toInt()
    drawText(txt, 0, charsToDraw, x, y, CaptionPaints.outline)
} else {
    // existing fill logic
}
```

**ColorTab.kt:** Add toggle "Outline Only" in the Outline subtool.

---

## Phase 4: Visual Effects (P3)

### 4A. Glow/Neon Effect

**CaptionStyleEntity.kt:** Add `glowEnabled: Boolean = false`, `glowColor: Long`, `glowRadius: Float`

**CaptionPaints.kt:** Apply `BlurMaskFilter`:
```kotlin
if (style.glowEnabled && style.glowRadius > 0f) {
    text.maskFilter = BlurMaskFilter(
        style.glowRadius * baseScale, BlurMaskFilter.Blur.NORMAL
    )
}
```

**Rendering approach:** Draw the text twice — once with blur mask (glow layer), once normal on top.

**ColorTab.kt:** New "Glow" subtool with color + radius controls.

### 4B. Blur Background

**Challenge:** Android Canvas doesn't support real-time blur of underlying content. Options:
1. **Frosted glass approximation:** Draw a semi-transparent white/dark rect with rounded corners (already supported via background system)
2. **RenderScript blur:** Capture the video frame region behind the caption, blur it, draw as background. Heavy on performance.
3. **Static blur:** Pre-blur a portion of the video frame. Not dynamic.

**Recommendation:** Skip true blur for now. The existing `FULL_LINE` background type with high opacity already achieves a similar readability effect. If needed later, use `RenderEffect.createBlurEffect()` (API 31+, but our minSdk is 24).

### 4C. Background Gradient

**CaptionStyleEntity.kt:** Add `backgroundGradientEnabled: Boolean = false`, `backgroundGradientColor: Long`

**CaptionRenderer.kt `drawLineBackground()`:** Use LinearGradient shader on bg paint when gradient is enabled.

---

## Phase 5: Enhanced Preset System

### 5A. More Built-In Presets

Add 10 more presets to `CaptionRepository.initializeDefaultStyles()`:

| Name | Font | Mode | Style |
|------|------|------|-------|
| Hormozi | Bebas Neue | WORD_BY_WORD | Bold white, no outline, SCALE_POP |
| Minimal | Roboto | PHRASE | Thin white, subtle shadow |
| News | Oswald | LINE_HIGHLIGHT | White on dark box, underline highlight |
| Story | Pacifico | WORD_BY_WORD | Handwritten, fade in |
| Tech | Space Mono | TYPEWRITER | Green on dark, full-line bg |
| Elegant | Playfair Display | PHRASE | Gold text, black outline |
| Bold Pop | Montserrat Black | WORD_BY_WORD | Yellow highlight, bounce anim |
| Cinematic | Montserrat | KARAOKE_FILL | Semi-transparent box, fill L-R |
| Retro | Impact | PHRASE | White with thick black outline |
| Neon | Bebas Neue | WORD_BY_WORD | Cyan glow, outline-only |

### 5B. User Preset Management

Already works (save/delete in CaptionRepository). Enhance:
- Add rename capability
- Add duplicate preset
- Show preset thumbnail preview using actual caption rendering

---

## Implementation Order (Build Sequence)

### Sprint 1 (Font System) — 3-4 days
1. Bundle fonts in `assets/fonts/`
2. Add `context: Context` parameter to `CaptionPaints.configure()` and `CaptionRenderer.draw()`
3. Update `resolveTypeface()` to load from assets
4. Create `FontPickerSheet.kt` composable
5. Add `FONT` subtool to `TextTab.kt`
6. Wire through `StylePanel.kt`
7. Update all call sites for new `context` parameter (PreviewSection, StylePreview, CaptionOverlayEffect)

### Sprint 2 (Missing Controls) — 3-4 days
1. Add `textOpacity` to entity + DB migration 13→14
2. Add `textTransform` enum + entity field + DB migration
3. Add `lineHeight` to entity + DB migration
4. Add `outlineOnly` to entity + DB migration
5. Update `CaptionPaints.configure()` to apply text opacity
6. Update `CaptionRenderer` for text transform, lineHeight, outlineOnly
7. Update `CaptionUtils.sanitize()` for text transform
8. Add Text subtools: Opacity, Transform, Line Height
9. Add exit animation picker to AnimationTab
10. Add emphasis type picker to CaptionEditorScreen

### Sprint 3 (Color & Effects) — 3-4 days
1. Add gradient direction enum + entity field
2. Implement gradient text in CaptionRenderer (LinearGradient shader)
3. Add Gradient subtool to ColorTab
4. Wire shadow UI controls in ColorTab (fields already exist)
5. Add outline-only toggle to ColorTab
6. Add glow/neon effect (entity fields + BlurMaskFilter rendering)

### Sprint 4 (Positioning & Presets) — 2-3 days
1. Add horizontal drag to StylePreview for positionX
2. Add Position subtool to TextTab
3. Add 10 new built-in presets
4. Polish and test all interactions

---

## DB Migration (13 → 14)

```sql
ALTER TABLE caption_styles ADD COLUMN textOpacity REAL NOT NULL DEFAULT 1.0;
ALTER TABLE caption_styles ADD COLUMN textTransform TEXT NOT NULL DEFAULT 'NONE';
ALTER TABLE caption_styles ADD COLUMN lineHeight REAL NOT NULL DEFAULT 1.2;
ALTER TABLE caption_styles ADD COLUMN outlineOnly INTEGER NOT NULL DEFAULT 0;
ALTER TABLE caption_styles ADD COLUMN gradientDirection TEXT NOT NULL DEFAULT 'NONE';
ALTER TABLE caption_styles ADD COLUMN glowEnabled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE caption_styles ADD COLUMN glowColor INTEGER NOT NULL DEFAULT 4294967295;
ALTER TABLE caption_styles ADD COLUMN glowRadius REAL NOT NULL DEFAULT 0.0;
ALTER TABLE caption_styles ADD COLUMN backgroundGradientEnabled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE caption_styles ADD COLUMN backgroundGradientColor INTEGER NOT NULL DEFAULT 4294967295;
```

---

## Files to Modify (Complete List)

### Engine Layer
| File | Changes |
|------|---------|
| `engine/CaptionPaints.kt` | Add Context param, gradient shader support, glow BlurMaskFilter, text opacity |
| `engine/CaptionRenderer.kt` | Add Context param, gradient text rendering, shadow pass, outlineOnly, lineHeight, glow layer |
| `engine/CaptionUtils.kt` | Text transform in sanitize() |

### Data Layer
| File | Changes |
|------|---------|
| `data/db/entity/CaptionStyleEntity.kt` | Add 8 new fields + 2 new enums |
| `data/db/AppDatabase.kt` | Migration 13→14, bump version |
| `data/db/Converters.kt` | Add converters for new enums |
| `data/repository/CaptionRepository.kt` | Update default presets, new preset styles |

### UI Layer — Style Editor
| File | Changes |
|------|---------|
| `ui/videoeditor/style/tabs/TextTab.kt` | Add Font, Opacity, Transform, LineHeight, PositionX subtools |
| `ui/videoeditor/style/tabs/ColorTab.kt` | Add Shadow, Gradient, Glow, OutlineOnly subtools |
| `ui/videoeditor/style/tabs/AnimationTab.kt` | Add exit animation picker |
| `ui/videoeditor/style/StylePanel.kt` | Wire new callbacks |
| `ui/videoeditor/style/StyleViewModel.kt` | No structural changes (generic UpdateStyle) |
| `ui/videoeditor/style/StylePreview.kt` | Add horizontal drag for positionX |

### UI Layer — New Files
| File | Purpose |
|------|---------|
| `ui/videoeditor/style/tabs/FontPickerSheet.kt` | Font selection bottom sheet |
| `ui/videoeditor/style/tabs/FontItem.kt` | Individual font preview item |

### UI Layer — Caption Editor
| File | Changes |
|------|---------|
| `ui/captioneditor/CaptionEditorScreen.kt` | Emphasis type picker popup |
| `ui/captioneditor/CaptionEditorViewModel.kt` | Handle emphasis type selection |

### Preview/Export Call Sites (Context param update)
| File | Changes |
|------|---------|
| `ui/videoeditor/player/PreviewSection.kt` | Pass context to CaptionRenderer.draw() |
| `ui/videoeditor/style/StylePreview.kt` | Pass context to CaptionRenderer.draw() |
| `engine/CaptionOverlayEffect.kt` | Pass context to CaptionRenderer.draw() |

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Font loading crashes (bad TTF) | Wrap in try-callback to system font; validate fonts at build time |
| Gradient shader perf at 60fps | Cache gradient per word per frame; only recreate when bounds change |
| BlurMaskFilter perf | Glow is opt-in, off by default; measure fps impact before shipping |
| DB migration data loss | Use ALTER TABLE ADD COLUMN with defaults — safe, no data loss |
| Breaking existing styles | All new fields have defaults matching current behavior |
| Context leak in CaptionPaints | Use applicationContext, never activity context |

---

## Testing Strategy

1. **Font loading:** Test each bundled font renders correctly at various sizes/weights
2. **Gradient text:** Visual regression — screenshot comparison at different gradient angles
3. **Performance:** Profile Canvas draw calls with new effects — must stay under 16ms/frame
4. **Export:** Verify gradient/glow/outline-only renders correctly in exported video (CaptionOverlayEffect)
5. **Migration:** Test upgrade from DB v13 to v14 with existing data
6. **Undo/redo:** Verify all new properties participate in undo stack correctly
7. **RTL:** Test gradient direction with RTL text
