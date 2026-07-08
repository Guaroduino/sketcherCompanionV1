import os
import sys

try:
    import firebase_admin
    from firebase_admin import credentials, firestore, auth
except ImportError:
    print("El paquete 'firebase-admin' no está instalado.")
    print("Por favor, instálalo ejecutando:")
    print("    pip install firebase-admin")
    sys.exit(1)

# Buscar el archivo de credenciales de Firebase en el proyecto
# Se asume que el script corre desde la carpeta 'scratch' o desde la raíz
POSSIBLE_PATHS = [
    os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "service-account.json")),
    os.path.abspath(os.path.join(os.path.dirname(__file__), "service-account.json")),
    os.path.abspath("service-account.json")
]

service_account_path = None
for path in POSSIBLE_PATHS:
    if os.path.exists(path):
        service_account_path = path
        break

if not service_account_path:
    print("Error: No se encontró el archivo 'service-account.json'.")
    print("Asegúrate de descargar las credenciales de tu cuenta de servicio desde la consola de Firebase:")
    print("  Consola -> Configuración del proyecto -> Cuentas de servicio -> Generar nueva clave privada")
    print("Coloca el archivo descargado como 'service-account.json' en la raíz del proyecto.")
    sys.exit(1)

print(f"Usando credenciales: {service_account_path}")

# Inicializar Firebase Admin SDK
try:
    cred = credentials.Certificate(service_account_path)
    firebase_admin.initialize_app(cred)
except Exception as e:
    print(f"Error al inicializar Firebase Admin SDK: {e}")
    sys.exit(1)

db = firestore.client()

def delete_collection(coll_ref, batch_size=100):
    """
    Elimina todos los documentos y subcolecciones dentro de una colección de manera recursiva.
    """
    docs = coll_ref.limit(batch_size).stream()
    deleted = 0

    for doc in docs:
        # Primero eliminar recursivamente todas las subcolecciones del documento
        for subcoll in doc.reference.collections():
            delete_collection(subcoll, batch_size)
        
        doc.reference.delete()
        deleted += 1

    # Si se alcanzó el límite del lote, continuar borrando el resto
    if deleted >= batch_size:
        return deleted + delete_collection(coll_ref, batch_size)
    return deleted

def reset_firestore():
    print("\n--- Borrando Firestore Database ---")
    try:
        print("Eliminando colección raíz: 'users'...")
        users_ref = db.collection('users')
        deleted = delete_collection(users_ref)
        print(f"Colección 'users' vaciada ({deleted} elementos/subcolecciones eliminados).")
    except Exception as e:
        print(f"Error al vaciar Firestore: {e}")
        print("Si el error es de permisos, asegúrate de que tu Cuenta de Servicio en service-account.json")
        print("tenga asignado el rol 'Administrador de Cloud Datastore' o 'Propietario' en Google Cloud Console.")


def reset_auth():
    print("\n--- Borrando Firebase Authentication ---")
    deleted_count = 0
    try:
        page = auth.list_users()
        while page:
            uids = [user.uid for user in page.users]
            if uids:
                result = auth.delete_users(uids)
                deleted_count += len(uids) - len(result.errors)
                if result.errors:
                    print(f"Error al borrar {len(result.errors)} usuarios:")
                    for error in result.errors:
                        print(f"  - UID {error.index}: {error.reason}")
            page = page.get_next_page()
        
        if deleted_count == 0:
            print("No se encontraron usuarios en Firebase Authentication.")
        else:
            print(f"Authentication limpio. Se eliminaron {deleted_count} cuentas de usuario.")
    except Exception as e:
        print(f"Error al vaciar Authentication: {e}")

if __name__ == "__main__":
    print("=" * 70)
    print(" ¡CUIDADO! Este script borrará PERMANENTEMENTE:")
    print("   1. Toda la base de datos de Firestore (todas las colecciones y subcolecciones).")
    print("   2. Todos los usuarios registrados en Firebase Authentication.")
    print("=" * 70)
    
    confirm = input("¿Estás seguro de que deseas continuar? (Escribe 'si' para confirmar): ")
    if confirm.lower().strip() == "si":
        print("\nIniciando limpieza...")
        reset_firestore()
        reset_auth()
        print("\n[OK] El entorno de Firebase ha sido restablecido a cero.")
    else:
        print("\nOperación cancelada. No se modificó ningún dato.")
