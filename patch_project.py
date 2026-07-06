import re

with open("app/src/main/java/com/dipdev/aiautocaptioner/data/repository/ProjectRepository.kt", "r") as f:
    content = f.read()

# getProjectById
content = content.replace("suspend fun getProjectById(projectId: String): ProjectEntity? =\n        withContext(Dispatchers.IO) {\n            projectDao.getProjectById(projectId)\n        }",
"suspend fun getProjectById(projectId: String): ProjectEntity? =\n        projectDao.getProjectById(projectId)")

# updateStatus
content = content.replace("suspend fun updateStatus(projectId: String, status: ProjectStatus) {\n        withContext(Dispatchers.IO) {\n            projectDao.updateStatus(projectId, status)\n        }\n    }",
"suspend fun updateStatus(projectId: String, status: ProjectStatus) {\n        projectDao.updateStatus(projectId, status)\n    }")

# updateWorkingVideoPath
content = content.replace("""    suspend fun updateWorkingVideoPath(projectId: String, videoPath: String) {
        withContext(Dispatchers.IO) {
            val project = projectDao.getProjectById(projectId) ?: return@withContext
            projectDao.updateProject(project.copy(workingVideoPath = videoPath, updatedAt = System.currentTimeMillis()))
        }
    }""",
"""    suspend fun updateWorkingVideoPath(projectId: String, videoPath: String) {
        val project = projectDao.getProjectById(projectId) ?: return
        projectDao.updateProject(project.copy(workingVideoPath = videoPath, updatedAt = System.currentTimeMillis()))
    }""")

# updateVisitedCaptionEditor
content = content.replace("""    suspend fun updateVisitedCaptionEditor(projectId: String, hasVisited: Boolean = true) {
        withContext(Dispatchers.IO) {
            projectDao.updateVisitedCaptionEditor(projectId, hasVisited)
        }
    }""",
"""    suspend fun updateVisitedCaptionEditor(projectId: String, hasVisited: Boolean = true) {
        projectDao.updateVisitedCaptionEditor(projectId, hasVisited)
    }""")

# updateProject
content = content.replace("""    suspend fun updateProject(project: ProjectEntity) {
        withContext(Dispatchers.IO) {
            projectDao.updateProject(project)
        }
    }""",
"""    suspend fun updateProject(project: ProjectEntity) {
        projectDao.updateProject(project)
    }""")

# renameProject
content = content.replace("""    suspend fun renameProject(projectId: String, newTitle: String) {
        withContext(Dispatchers.IO) {
            val project = projectDao.getProjectById(projectId) ?: return@withContext
            projectDao.updateProject(
                project.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis())
            )
            Log.i(TAG, "Project renamed to: $newTitle")
        }
    }""",
"""    suspend fun renameProject(projectId: String, newTitle: String) {
        val project = projectDao.getProjectById(projectId) ?: return
        projectDao.updateProject(
            project.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis())
        )
        Log.i(TAG, "Project renamed to: $newTitle")
    }""")


with open("app/src/main/java/com/dipdev/aiautocaptioner/data/repository/ProjectRepository.kt", "w") as f:
    f.write(content)
