import os
import glob

files = [
    "app/src/main/java/com/sketcher/sketchercompanionv1/utils/SerializationUtils.kt",
    "app/src/main/java/com/sketcher/sketchercompanionv1/utils/SvgExporter.kt",
    "app/src/main/java/com/sketcher/sketchercompanionv1/RenderEngine.kt",
    "app/src/main/java/com/sketcher/sketchercompanionv1/RenderHelper.kt",
    "app/src/main/java/com/sketcher/sketchercompanionv1/SketcherViewModel.kt",
    "app/src/main/java/com/sketcher/sketchercompanionv1/StrokePipeline.kt"
]

replacements = [
    ('this.brushType == "FREEHAND" || this.brushType == "PLUMA" || this.brushType == "PENCIL_CUMULATIVE"', 
     'this.brushType == "FREEHAND" || this.brushType == "PEN" || this.brushType == "PLUMA" || this.brushType == "PENCIL_CUMULATIVE"'),
    
    ('stroke.brushType == "FREEHAND" || stroke.brushType == "PLUMA" || stroke.brushType == "PENCIL_CUMULATIVE" || stroke.brushType == "PAINT" || stroke.brushType == "WATERCOLOR"',
     'stroke.brushType == "FREEHAND" || stroke.brushType == "PEN" || stroke.brushType == "PLUMA" || stroke.brushType == "PENCIL_CUMULATIVE" || stroke.brushType == "PAINT" || stroke.brushType == "WATERCOLOR"'),
    
    ('stroke.brushType == "FREEHAND" || stroke.brushType == "PLUMA" || stroke.brushType == "PENCIL_CUMULATIVE"',
     'stroke.brushType == "FREEHAND" || stroke.brushType == "PEN" || stroke.brushType == "PLUMA" || stroke.brushType == "PENCIL_CUMULATIVE"'),
     
    ('brushType == "FREEHAND" || brushType == "PLUMA" || brushType == "PENCIL_CUMULATIVE"',
     'brushType == "FREEHAND" || brushType == "PEN" || brushType == "PLUMA" || brushType == "PENCIL_CUMULATIVE"'),
     
    ('vStroke.brushType == "FREEHAND" || vStroke.brushType == "PLUMA" || vStroke.brushType == "PENCIL_CUMULATIVE"',
     'vStroke.brushType == "FREEHAND" || vStroke.brushType == "PEN" || vStroke.brushType == "PLUMA" || vStroke.brushType == "PENCIL_CUMULATIVE"'),
     
    ('element.brushType == "FREEHAND" || element.brushType == "PLUMA" || element.brushType == "PENCIL_CUMULATIVE" || element.brushType == "PAINT" || element.brushType == "WATERCOLOR"',
     'element.brushType == "FREEHAND" || element.brushType == "PEN" || element.brushType == "PLUMA" || element.brushType == "PENCIL_CUMULATIVE" || element.brushType == "PAINT" || element.brushType == "WATERCOLOR"'),
     
    ('activeTool == ToolType.FREEHAND || activeTool == ToolType.PLUMA || activeTool == ToolType.PENCIL_CUMULATIVE || activeTool == ToolType.PAINT || activeTool == ToolType.WATERCOLOR',
     'activeTool == ToolType.FREEHAND || activeTool == ToolType.PEN || activeTool == ToolType.PLUMA || activeTool == ToolType.PENCIL_CUMULATIVE || activeTool == ToolType.PAINT || activeTool == ToolType.WATERCOLOR')
]

for f in files:
    with open(f, "r", encoding="utf-8") as file:
        content = file.read()
    
    original = content
    for old, new in replacements:
        content = content.replace(old, new)
        
    if original != content:
        with open(f, "w", encoding="utf-8") as file:
            file.write(content)
        print(f"Updated {f}")
    else:
        print(f"No changes in {f}")

