# Mi Botiquín 🏥

> **Inventario de confianza** para tu botiquín doméstico. Se abre en momentos de necesidad y te da calma, orden y rapidez absoluta.

## ✨ Características

| Función | Detalle |
|---------|---------|
| **Búsqueda instantánea** | Campo activo al abrir; filtra nombre/código/categoría en tiempo real |
| **Lista con vida** | Productos agrupados por contexto: Medicamentos / Primeros auxilios / Tratamientos tópicos |
| **Escáner real** | CameraX + ML Kit (EAN-13, UPC-A, EAN-8, Code-128); linterna; haptic al detectar |
| **Re-escaneo inteligente** | Si el código ya existe, el formulario viene pre-relleno (edita, no duplica) |
| **Caducidad visual** | Chips discretos: OK (gris) · PRÓXIMO ≤30d (ámbar) · URGENTE ≤7d (naranja) · CADUCADO/AGOTADO (rojo sutil) |
| **Alertas silenciosas** | WorkManager diario → notificación con acciones **Eliminar / Mantener** (borra de la BD aunque la app esté cerrada) |
| **Multi-botiquín** | Casa · Trabajo · Moto… cada uno con su inventario independiente |
| **Compartir / Importar** | Exporta un botiquín a JSON (SAF) → envíalo por WhatsApp/Drive → impórtalo en otro dispositivo; **gana el de fecha más reciente** (reemplazo total) |
| **Backup local** | Exporta/importa todo el inventario vía SAF (JSON) |
| **Cero onboarding** | Abres la app y ya estás viendo tu botiquín; si está vacío, pide nombre del primero |

## 📱 Capturas

<!-- Añade screenshots aquí -->

| Home | Scanner | Sheet | Alertas |
|------|---------|-------|---------|
| ![home](docs/home.png) | ![scan](docs/scan.png) | ![sheet](docs/sheet.png) | ![notify](docs/notify.png) |

## 🏗️ Arquitectura

```
app/
├── data/
│   ├── local/          # Room (ProductEntity, CabinetEntity, DAOs, Migración v1→v2)
│   ├── repository/     # ProductRepositoryImpl
│   └── transfer/       # CabinetTransferManager (export/import JSON + merge rule)
├── domain/
│   ├── model/          # Product, Cabinet, Category, ExpiryStatus, ProductUiModel
│   ├── repository/     # Interfaces
│   └── usecase/        # GetProducts, CreateCabinet, ShareCabinet, ...
├── presentation/
│   ├── ui/
│   │   ├── theme/      # Calm Tech (light/dark, tipografía Inter/Roboto Flex)
│   │   ├── components/ # SearchBar, ProductCard, AddProductSheet, Dialogs
│   │   └── screen/     # HomeScreen, ScannerScreen
│   └── navigation/     # NavHost + deep links (mibotiquin://scan)
└── notifications/      # ExpiryCheckWorker (WorkManager) + ProductActionReceiver
```

**Stack**: Kotlin 2.4.10 · AGP 9.1 · Compose 1.7 (Material3) · Room 2.8 · KSP 2.3 · CameraX 1.4 · ML Kit 17.3 · WorkManager 2.9

## 🚀 Instalación

```bash
# Clona y abre en Android Studio
git clone https://github.com/damagr/mibotiquin.git

# O instala el APK release directamente
adb install mibotiquin-release.apk
```

## 🔐 Firma & Release

- Keystore: `release.jks` (RSA 4096, 10k días, alias `mibotiquin`)
- Configurado en `signingConfigs.release` → lee `keystore.properties` (gitignored)
- Workflow: `.github/workflows/release.yml` (auto-tag al subir versión en `app/build.gradle.kts`)

### Secrets requeridos en GitHub

| Secret | Descripción |
|--------|-------------|
| `KEYSTORE_BASE64` | `base64 -w 0 release.jks` |
| `KEYSTORE_PASS` | Password del almacén |
| `KEY_ALIAS` | `mibotiquin` |
| `KEY_PASS` | Password de la clave (igual que store en PKCS12) |

## 📦 Release

```bash
# Sube versionName en app/build.gradle.kts
versionName = "1.1.0"
git push
# → Workflow detecta cambio → tag v1.1.0 → build → Release GH con APK
```

## 📄 Licencia

MIT — libre para uso personal y comercial.

---

> **Hecho con calma** para esos momentos en los que cada segundo cuenta. 💚