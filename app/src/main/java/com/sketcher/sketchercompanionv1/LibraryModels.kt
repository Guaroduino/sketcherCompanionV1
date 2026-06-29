package com.sketcher.sketchercompanionv1

sealed class LibraryItem {
    abstract val id: String
    abstract var name: String
    abstract var parentId: String?
}

data class LibraryFolder(
    override val id: String,
    override var name: String,
    override var parentId: String? = null
) : LibraryItem()

data class LibraryComponent(
    override val id: String,
    override var name: String,
    override var parentId: String? = null,
    val definition: ComponentDefinition,
    val thumbnailFileName: String? = null
) : LibraryItem()
