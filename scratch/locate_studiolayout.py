import os

search_dir = r"c:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1"
for root, dirs, files in os.walk(search_dir):
    for file in files:
        if "StudioLayout" in file:
            print(os.path.join(root, file))
