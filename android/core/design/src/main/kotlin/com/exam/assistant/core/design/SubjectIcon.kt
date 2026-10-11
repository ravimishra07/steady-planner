package com.exam.assistant.core.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Science
import androidx.compose.ui.graphics.vector.ImageVector

/** One icon per exam-pack subject id, shared by Syllabus and Progress; unknown subjects get a book. */
fun subjectIcon(subjectId: String): ImageVector = when (subjectId.lowercase()) {
    "physics" -> Icons.Filled.Bolt
    "chemistry" -> Icons.Filled.Science
    "botany" -> Icons.Filled.LocalFlorist
    "zoology" -> Icons.Filled.Pets
    else -> Icons.AutoMirrored.Filled.MenuBook
}
