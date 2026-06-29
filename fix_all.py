import re

# 1. Fix SketcherViewModel.kt for Thumbnails
file_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\SketcherViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

old_canvas = '''                    val m = android.graphics.Matrix()
                    m.postTranslate(-bounds.left, -bounds.top)
                    m.postScale(scale, scale)
                    m.postTranslate(dx, dy)
                    
                    com.sketcher.sketchercompanionv1.RenderEngine().drawElementRecursive('''

new_canvas = '''                    val m = android.graphics.Matrix()
                    m.postTranslate(-bounds.left, -bounds.top)
                    m.postScale(scale, scale)
                    m.postTranslate(dx, dy)
                    
                    canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.concat(m)
                    
                    com.sketcher.sketchercompanionv1.RenderEngine().drawElementRecursive('''

content = content.replace(old_canvas, new_canvas)
with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)


# 2. Fix RenderEngine.kt to FILL for PEN as well (since PEN uses PerfectFreehandGenerator)
render_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\RenderEngine.kt'
with open(render_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('if (stroke.strokeType == StrokeType.FREEHAND) {', 'if (stroke.strokeType == StrokeType.FREEHAND || stroke.strokeType == StrokeType.PEN) {')

with open(render_path, 'w', encoding='utf-8') as f:
    f.write(content)


# 3. Fix StudioLayout.kt width hardcodings
layout_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\ui\layout\StudioLayout.kt'
with open(layout_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('.width(300.dp)', '.width(200.sdp)')
content = content.replace('.width(180.dp)', '.width(120.sdp)')

# Also ensure sdp is imported
if 'import com.sketcher.sketchercompanionv1.ui.theme.sdp' not in content:
    content = content.replace('import androidx.compose.ui.unit.dp', 'import androidx.compose.ui.unit.dp\nimport com.sketcher.sketchercompanionv1.ui.theme.sdp')

with open(layout_path, 'w', encoding='utf-8') as f:
    f.write(content)


# 4. Fix LibraryPanel.kt button size
panel_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\ui\panels\LibraryPanel.kt'
with open(panel_path, 'r', encoding='utf-8') as f:
    content = f.read()

old_button = '''            Button(
                onClick = { viewModel.instantiateFromGlobalLibrary(selectedItem) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = scaler.smallMargin, vertical = 4.sdp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor)
            ) {
                Icon(Icons.Default.AddCircleOutline, null, modifier = Modifier.size(16.sdp))
                Spacer(Modifier.width(8.dp))
                Text("Insertar en Lienzo")
            }'''

new_button = '''            Button(
                onClick = { viewModel.instantiateFromGlobalLibrary(selectedItem) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = scaler.smallMargin, vertical = 4.sdp).height(32.sdp),
                contentPadding = PaddingValues(horizontal = 8.sdp, vertical = 0.sdp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor)
            ) {
                Icon(Icons.Default.AddCircleOutline, null, modifier = Modifier.size(14.sdp), tint = theme.barBackgroundColor)
                Spacer(Modifier.width(4.sdp))
                Text("Insertar", style = MaterialTheme.typography.labelSmall, color = theme.barBackgroundColor)
            }'''

content = content.replace(old_button, new_button)

with open(panel_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Applied fixes")
