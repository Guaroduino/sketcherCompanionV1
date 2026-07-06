import os
import re
import sys
import subprocess
import json

def get_and_increment_versions():
    gradle_path = os.path.join("app", "build.gradle.kts")
    if not os.path.exists(gradle_path):
        print(f"Error: No se encontró {gradle_path}")
        return None, None
    
    with open(gradle_path, "r", encoding="utf-8") as f:
        content = f.read()
    
    version_code_match = re.search(r"(versionCode\s*=\s*)(\d+)", content)
    version_name_match = re.search(r'(versionName\s*=\s*")([^"]+)"', content)
    
    if not version_code_match or not version_name_match:
        print("Error: No se pudo parsear el versionCode o versionName en build.gradle.kts")
        return None, None
    
    old_code = int(version_code_match.group(2))
    old_name = version_name_match.group(2)
    
    # Increment versionCode
    new_code = old_code + 1
    
    # Increment versionName (e.g., "1.0" -> "1.0.1" or "1.0.1" -> "1.0.2")
    parts = old_name.split('.')
    if len(parts) == 1:
        new_name = f"{parts[0]}.0.1"
    elif len(parts) == 2:
        new_name = f"{parts[0]}.{parts[1]}.1"
    else:
        try:
            last_digit = int(parts[-1])
            new_name = '.'.join(parts[:-1]) + f".{last_digit + 1}"
        except ValueError:
            new_name = old_name + ".1"
            
    # Update content in memory
    content = re.sub(r"(versionCode\s*=\s*)\d+", f"\\g<1>{new_code}", content)
    content = re.sub(r'(versionName\s*=\s*")[^"]+"', f'\\g<1>{new_name}"', content)
    
    # Write back to file
    with open(gradle_path, "w", encoding="utf-8") as f:
        f.write(content)
        
    print(f"Versión de la App actualizada en build.gradle.kts:")
    print(f"  - Código: {old_code} -> {new_code}")
    print(f"  - Nombre: \"{old_name}\" -> \"{new_name}\"")
    
    return new_code, new_name

def build_apk():
    print("\nCompilando el APK de depuración (Debug)...")
    gradle_cmd = "gradlew.bat" if os.name == "nt" else "./gradlew"
    
    # Run assembleDebug inside the workspace
    result = subprocess.run(gradle_cmd + " assembleDebug", shell=True)
    if result.returncode != 0:
        print("Error: Falló la compilación del APK.")
        sys.exit(1)
    
    apk_path = os.path.join("app", "build", "outputs", "apk", "debug", "app-debug.apk")
    if not os.path.exists(apk_path):
        print(f"Error: No se encontró el APK compilado en {apk_path}")
        sys.exit(1)
        
    print(f"¡Compilación exitosa! APK generado en: {apk_path}")
    return apk_path

def main():
    print("=== Sketcher Companion V1 - Publicador de Actualizaciones Automático ===")
    
    # 1. Increment versions in build.gradle.kts
    version_code, version_name = get_and_increment_versions()
    if not version_code:
        sys.exit(1)
        
    # 2. Prompt for release notes
    if len(sys.argv) > 1:
        release_notes = sys.argv[1]
    else:
        release_notes = input("\nIntroduce las notas de esta versión (ej: Ajustes de interfaz y rendimiento): ").strip()
    if not release_notes:
        release_notes = f"Actualización a la versión {version_name}."
        
    # 3. Build APK
    apk_path = build_apk()
    
    # 4. Create update.json
    bucket_name = "sketchercompanionapp.firebasestorage.app"
    apk_url = f"https://firebasestorage.googleapis.com/v0/b/{bucket_name}/o/updates%2Fapp-debug.apk?alt=media"
    
    update_data = {
        "versionCode": version_code,
        "versionName": version_name,
        "apkUrl": apk_url,
        "releaseNotes": release_notes,
        "forceUpdate": False
    }
    
    update_json_path = "update.json"
    with open(update_json_path, "w", encoding="utf-8") as f:
        json.dump(update_data, f, indent=2, ensure_ascii=False)
        
    print(f"\n¡Se ha creado el archivo {update_json_path} con la información de actualización!")
    
    # 5. Firebase Storage upload
    service_account_path = "service-account.json"
    if os.path.exists(service_account_path):
        print(f"\nSe encontró '{service_account_path}'. Intentando subir automáticamente a Firebase Storage...")
        try:
            import firebase_admin
            from firebase_admin import credentials, storage
        except ImportError:
            print("Instalando la librería 'firebase-admin' de Python para automatizar la subida...")
            subprocess.run([sys.executable, "-m", "pip", "install", "firebase-admin"])
            import firebase_admin
            from firebase_admin import credentials, storage
            
        try:
            # Inicializar Firebase Admin
            cred = credentials.Certificate(service_account_path)
            firebase_admin.initialize_app(cred, {
                'storageBucket': bucket_name
            })
            
            bucket = storage.bucket()
            
            # Subir APK
            print("Subiendo APK a Firebase Storage (updates/app-debug.apk)...")
            apk_blob = bucket.blob("updates/app-debug.apk")
            apk_blob.upload_from_filename(apk_path)
            
            # Subir update.json
            print("Subiendo update.json a Firebase Storage (updates/update.json)...")
            json_blob = bucket.blob("updates/update.json")
            json_blob.upload_from_filename(update_json_path)
            
            print("\n¡Todo subido con éxito! La app ya puede detectar e instalar la nueva versión.")
            
        except Exception as e:
            print(f"\nError durante la subida automática: {e}")
            print("Por favor, realiza la subida de forma manual.")
    else:
        print("\n--- CONFIGURACIÓN DE AUTO-SUBIDA PENDIENTE ---")
        print("\nPara subir automáticamente sin intervención manual:")
        print("  1. Abre este enlace en tu navegador:")
        print("     https://console.firebase.google.com/project/sketchercompanionapp/settings/serviceaccounts/adminsdk")
        print("  2. Haz clic en 'Generar nueva clave privada' para descargar un archivo JSON.")
        print(f"  3. Guarda ese archivo como '{service_account_path}' en la raíz de este proyecto.")
        print("  4. Vuelve a ejecutar este script para subir la actualización de forma 100% automática.")
        print("\nSi prefieres subir de forma manual temporalmente:")
        print("  1. Abre tu consola de Firebase Storage.")
        print("  2. Sube a la carpeta 'updates/':")
        print(f"     - '{apk_path}' (renombrado a 'app-debug.apk')")
        print(f"     - '{update_json_path}'")

if __name__ == "__main__":
    main()
