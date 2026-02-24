import os

def replace_in_file(path, replacements):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    
    for old_str, new_str in replacements:
        content = content.replace(old_str, new_str)
        
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

base_dir = "c:\\Users\\corad\\OneDrive\\Documentos\\GitHub\\sketcherCompanionV1\\app\\src\\main\\java\\com\\sketcher\\sketchercompanionv1"

# 1. SketcherViewModel.kt
svm_path = os.path.join(base_dir, "SketcherViewModel.kt")
svm_replacements = [
    ("val layers: StateFlow<List<Layer>> = layerManager.layers", "val layers: androidx.compose.runtime.snapshots.SnapshotStateList<Layer> get() = layerManager.layers"),
    ("layers.value", "layers"),
    ("layerManager.triggerLayerEmission()", "")
]
replace_in_file(svm_path, svm_replacements)

# 2. UI files
ui_files = [
    "ui\\panels\\OutlinerPanel.kt",
    "ui\\layout\\StudioLayout.kt"
]

for ui_file in ui_files:
    ui_path = os.path.join(base_dir, ui_file)
    if os.path.exists(ui_path):
        replace_in_file(ui_path, [
            ("val layers by viewModel.layers.collectAsState()", "val layers = viewModel.layers"),
            ("val currentLayers by viewModel.layers.collectAsState()", "val currentLayers = viewModel.layers")
        ])

print("Replacements completed.")
