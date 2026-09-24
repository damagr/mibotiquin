# Mi Botiquín 🏥

> **Inventario de confianza** para tu botiquín doméstico. Se abre en momentos de necesidad y te da calma, orden y rapidez absoluta.

## ✨ Características

| Función | Detalle |
|---------|---------|
| **Búsqueda instantánea** | Campo activo al abrir; filtra nombre/código/categoría en tiempo real |
| **Familias personalizadas** | Familia predefinida "Medicamentos" + las que tú crees: crear, renombrar y eliminar desde Ajustes, o al instante desde el formulario (+ Nueva) |
| **Artículos sin código** | Vendas, cinta y otros sin código de barras ni CN: escríbelos a mano (nombre + familia + caducidad si procede) |
| **No perecedero** | Toggle para artículos que no caducan — la tarjeta muestra "No perecedero" en vez de fecha |
| **Escáner real** | CameraX + ML Kit (EAN-13, UPC-A, EAN-8, Code-128 y **DataMatrix** con caducidad GS1 AI 17 → MM/AAAA); linterna; dos botones en lectura CN |
| **Re-escaneo inteligente** | Re-escanear abre el formulario en modo nuevo: la deduplicación decide al guardar (suma si es la misma caja, nueva entrada si cambia fecha o familia). Editar desde la tarjeta |
| **Fusión automática** | Mismo artículo (por código o por nombre) + mismo botiquín + misma caducidad + misma familia → suma cantidades, nunca filas idénticas |
| **CIMA (AEMPS)** | Consulta el nombre del medicamento por CN al escanear/introducir + chip "Prospecto" que abre la ficha técnica en el navegador |
| **Caducidad visual** | Por MESES (los medicamentos caducan por mes, no por día): OK (>3 meses) · SOON (≤3) · CRITICAL (≤1) · EXPIRED · EMPTY — chips discretos |
| **Alertas silenciosas** | WorkManager diario → notificación con acciones **Eliminar / Mantener** (borra de la BD aunque la app esté cerrada) |
| **Multi-botiquín** | Casa · Trabajo · Moto… cada uno con su inventario independiente |
| **Compartir / Importar** | Exporta un botiquín a JSON (SAF) → envíalo por WhatsApp/Drive → impórtalo en otro dispositivo; **gana el de fecha más reciente** (reemplazo total) |
| **Backup local** | Exporta/importa todo el inventario vía SAF (JSON) + carpeta de backups para el guardado automático |
| **Auto-actualización** | Comprueba GitHub Releases al arrancar; crea backup de tus datos antes de descargar e instalar el nuevo APK |
| **Cero onboarding** | Abres la app y ya estás viendo tu botiquín; si está vacío, pide nombre del primero |

## 📱 Flujo

Abres la app → búsqueda activa → añades → listo.

**Con cámara**: escaneas el código de barras (o "Introducir CN a mano") → CIMA consulta el nombre → DataMatrix lee la caducidad (o la introduces a mano) → formulario con prefills.

**Sin cámara**: eliges "Introducir CN" (teclado → CIMA) o "Introducir medicamento a mano" (formulario directo, para artículos sin código).

Cero onboarding.

## 🏗️ Arquitectura

```
app/
├── data/
│   ├── api/             # CIMA (AEMPS) + GitHub Releases (Retrofit)
│   ├── update/          # UpdateChecker (comprobación + descarga de APK)
│   ├── local/           # Room (ProductEntity, CabinetEntity, CustomCategoryEntity, DAOs, Migraciones v1→v5)
│   ├── repository/      # ProductRepositoryImpl (deduplicación y fusión a nivel de repositorio)
│   ├── scan/            # CnExtractor, Gs1Parser (DataMatrix), BarcodeAnalyzer
│   └── transfer/        # CabinetTransferManager (export/import JSON + merge rule)
├── domain/
│   ├── model/           # Product, Cabinet, Category, ExpiryStatus, ProductUiModel, AddProductResult
│   ├── repository/      # Interfaces
│   └── usecase/         # GetProducts, CreateCabinet, AddProduct, ...
├── presentation/
│   ├── ui/
│   │   ├── theme/       # Calm Tech (light/dark, tipografía Inter/Roboto Flex)
│   │   ├── components/  # SearchBar, ProductCard, AddProductSheet, UpdateDialogs, ...
│   │   └── screen/      # HomeScreen, ScannerScreen, SettingsScreen, SetupScreen
│   └── navigation/     # NavHost + deep links (mibotiquin://scan)
└── notifications/      # ExpiryCheckWorker (WorkManager) + ProductActionReceiver
```

**Stack**: Kotlin 2.4.10 · AGP 9.1 · Compose 1.7 (Material3) · Room 2.8 · KSP 2.3 · CameraX 1.4 · ML Kit 17.3 · WorkManager 2.9 · Retrofit 2.11

## 🚀 Instalación

```bash
# Clona y abre en Android Studio
git clone https://github.com/damagr/mibotiquin.git

# O instala el APK release directamente
adb install mibotiquin-release.apk
```

## 📦 Release

```bash
# Sube versionName en app/build.gradle.kts (debe coincidir con el tag)
versionName = "1.1.21"
git push
git tag v1.1.21 && git push origin v1.1.21
# → Workflow detecta el tag → build → Release GH con APK
```

## 📄 Licencia

MIT — libre para uso personal y comercial.

Icono: "Farmacia" de [Freepik](https://www.flaticon.es/icono-gratis/farmacia_2180406) (Flaticon).

---

> **Hecho con calma** para esos momentos en los que cada segundo cuenta. 💚
