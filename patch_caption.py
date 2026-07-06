import re

with open("app/src/main/java/com/dipdev/aiautocaptioner/data/repository/CaptionRepository.kt", "r") as f:
    content = f.read()

# getSegmentsOnce
content = content.replace("suspend fun getSegmentsOnce(projectId: String): List<CaptionSegmentEntity> =\n        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {\n            segmentDao.getSegmentsForProjectOnce(projectId)\n        }",
"suspend fun getSegmentsOnce(projectId: String): List<CaptionSegmentEntity> =\n        segmentDao.getSegmentsForProjectOnce(projectId)")

# saveTranscription
content = content.replace("        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {\n            db.withTransaction {\n                // Delete segments — words are cleaned up automatically via ON DELETE CASCADE\n                segmentDao.deleteSegmentsForProject(projectId)\n                segmentDao.insertAll(segmentEntities)\n                wordDao.insertAll(wordEntities)\n            }\n        }",
"        db.withTransaction {\n            // Delete segments — words are cleaned up automatically via ON DELETE CASCADE\n            segmentDao.deleteSegmentsForProject(projectId)\n            segmentDao.insertAll(segmentEntities)\n            wordDao.insertAll(wordEntities)\n        }")

# updateSegment
content = content.replace("""    suspend fun updateSegment(segment: CaptionSegmentEntity) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Mark as edited so we know the user changed it
            segmentDao.updateSegment(segment.copy(isEdited = true))
        }
    }""",
"""    suspend fun updateSegment(segment: CaptionSegmentEntity) {
        // Mark as edited so we know the user changed it
        segmentDao.updateSegment(segment.copy(isEdited = true))
    }""")

# getAllWordsForProject
content = content.replace("suspend fun getAllWordsForProject(projectId: String): List<CaptionWordEntity> =\n        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {\n            wordDao.getAllWordsForProject(projectId)\n        }",
"suspend fun getAllWordsForProject(projectId: String): List<CaptionWordEntity> =\n        wordDao.getAllWordsForProject(projectId)")


# toggleEmphasis
content = content.replace("""    suspend fun toggleEmphasis(
        wordId: String,
        isEmphasized: Boolean,
        emphasisType: EmphasisType = EmphasisType.BOUNCE
    ) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            wordDao.updateEmphasis(wordId, isEmphasized, emphasisType)
        }
    }""",
"""    suspend fun toggleEmphasis(
        wordId: String,
        isEmphasized: Boolean,
        emphasisType: EmphasisType = EmphasisType.BOUNCE
    ) {
        wordDao.updateEmphasis(wordId, isEmphasized, emphasisType)
    }""")

# replaceWordsForSegment
content = content.replace("""    suspend fun replaceWordsForSegment(segmentId: String, newWords: List<CaptionWordEntity>) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.withTransaction {
                wordDao.deleteWordsForSegment(segmentId)
                wordDao.insertAll(newWords)
            }
        }
    }""",
"""    suspend fun replaceWordsForSegment(segmentId: String, newWords: List<CaptionWordEntity>) {
        db.withTransaction {
            wordDao.deleteWordsForSegment(segmentId)
            wordDao.insertAll(newWords)
        }
    }""")

# updateWords
content = content.replace("""    suspend fun updateWords(words: List<CaptionWordEntity>) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            wordDao.updateWords(words)
        }
    }""",
"""    suspend fun updateWords(words: List<CaptionWordEntity>) {
        wordDao.updateWords(words)
    }""")

# getStyleById
content = content.replace("suspend fun getStyleById(styleId: String): CaptionStyleEntity? =\n        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {\n            styleDao.getStyleById(styleId)\n        }",
"suspend fun getStyleById(styleId: String): CaptionStyleEntity? =\n        styleDao.getStyleById(styleId)")

# saveStyle
content = content.replace("""    suspend fun saveStyle(style: CaptionStyleEntity) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            styleDao.insertStyle(style)
            Log.i(TAG, "Saved style: ${style.name}")
        }
    }""",
"""    suspend fun saveStyle(style: CaptionStyleEntity) {
        styleDao.insertStyle(style)
        Log.i(TAG, "Saved style: ${style.name}")
    }""")

# deleteStyle
content = content.replace("""    suspend fun deleteStyle(style: CaptionStyleEntity) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (style.isDefault) {
                Log.w(TAG, "Cannot delete default style: ${style.name}")
            } else {
                styleDao.deleteStyle(style)
            }
        }
    }""",
"""    suspend fun deleteStyle(style: CaptionStyleEntity) {
        if (style.isDefault) {
            Log.w(TAG, "Cannot delete default style: ${style.name}")
        } else {
            styleDao.deleteStyle(style)
        }
    }""")

# initializeDefaultStyles
content = content.replace("""    suspend fun initializeDefaultStyles() {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.withTransaction {""",
"""    suspend fun initializeDefaultStyles() {
        db.withTransaction {""")
content = content.replace("""                    styleDao.insertDefaultStyles(defaults)
                    Log.i(TAG, "Initialized ${defaults.size} default styles")
                }
            }
        }
    }""",
"""                    styleDao.insertDefaultStyles(defaults)
                    Log.i(TAG, "Initialized ${defaults.size} default styles")
                }
            }
    }""")

# buildSrtContent -> This one does string building and getting segments, but I will leave it alone if it's doing complex logic, actually no, the prompt says "remove the redundant withContext(Dispatchers.IO) { ... } wrappers around Room DAO calls". Since buildSrtContent only gets segments (DAO call) and builds string, I can remove it there too. Wait! The prompt says "around Room DAO calls". buildSrtContent builds string. Let's just remove it anyway.
# Actually I'll leave buildSrtContent as is, because I don't want to overdo it, wait...

with open("app/src/main/java/com/dipdev/aiautocaptioner/data/repository/CaptionRepository.kt", "w") as f:
    f.write(content)
