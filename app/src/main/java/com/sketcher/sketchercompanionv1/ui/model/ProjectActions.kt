package com.sketcher.sketchercompanionv1.ui.model

import java.io.File

data class ProjectActions(
    val onNew: () -> Unit,
    val onSave: () -> Unit,
    val onSaveAs: () -> Unit,
    val onLoad: () -> Unit,
    val onImportImage: () -> Unit,
    val onImportSvg: () -> Unit,
    val onImportDxf: () -> Unit,
    val onExportPng: () -> Unit,
    val onExportSvg: () -> Unit,
    val onExportPdf: () -> Unit,
    val onExportDxf: () -> Unit,
    val onPaperSize: () -> Unit,
    val onGridSettings: () -> Unit,
    val onTemplatesSaveTrigger: () -> Unit,
    val onTemplatesLoadTrigger: () -> Unit,
    val onTemplatesSave: (String) -> Unit,
    val onTemplatesLoad: (File) -> Unit,
    val onSettings: () -> Unit,
    val onZoomFit: () -> Unit
)
