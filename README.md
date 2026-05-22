# FaceDetector App

App Android nativo em Kotlin para detecção de silhuetas de rostos em tempo real via ML Kit, com auto-salvamento de frames na galeria.

## Funcionalidades

- Detecção dual em paralelo: modo `ACCURATE` (azul) + `FAST` (verde), com consenso `CONSENSUS` (magenta)
- IoU > 35% para fundir detecções dos dois modelos
- Auto-save automático de frames limpos em `Pictures/FaceDetector/` sempre que um rosto for detectado
- HUD com FPS do pipeline e de cada detector
- Funciona 100% offline — modelo ML Kit embutido no APK
- Suporte a Android 8.0+ (API 26+)

## Como usar

### 1. Obter o `gradle-wrapper.jar`

Este arquivo não está incluído por restrições de rede. Há três opções:

**Opção A** — Se você tem Gradle instalado:
```sh
gradle wrapper --gradle-version 8.2
```

**Opção B** — Download manual:
Baixe de: https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar  
Coloque em: `gradle/wrapper/gradle-wrapper.jar`

**Opção C** — Copie de qualquer projeto Android existente.

### 2. Criar repositório e fazer push

```sh
git init
git add .
git commit -m "initial"
git remote add origin https://github.com/SEU_USUARIO/SEU_REPO.git
git push -u origin main
```

### 3. Baixar o APK

Após o push, o GitHub Actions compila automaticamente:

**Actions → último workflow → Artifacts → app-debug**

O APK fica disponível por 7 dias.

---

## Estrutura do projeto

```
FaceDetectorApp/
├── build.gradle                          ← root (só plugins)
├── settings.gradle
├── gradle.properties
├── gradlew
├── .github/workflows/build.yml           ← GitHub Actions CI
└── app/
    ├── build.gradle
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/facedetector/
        │   ├── camera/
        │   │   ├── CameraManager.kt      ← CameraX adaptativo
        │   │   └── ImageSaver.kt         ← salva frame limpo na galeria
        │   ├── detector/
        │   │   ├── DetectionResult.kt    ← data class + DetectorSource
        │   │   ├── FaceDetectorWrapper.kt← wrapper ML Kit
        │   │   └── DualDetectorManager.kt← paralelo + IoU + auto-save
        │   ├── overlay/
        │   │   ├── FaceOverlayView.kt    ← Canvas: oval + cantos + landmarks
        │   │   └── HudView.kt            ← HUD de métricas
        │   └── ui/
        │       └── MainActivity.kt
        └── res/
            ├── layout/activity_main.xml
            └── values/
                ├── strings.xml
                └── themes.xml
```

## Cores dos detectores

| Detector  | Cor     | Hex       |
|-----------|---------|-----------|
| ACCURATE  | Azul    | `#1E90FF` |
| FAST      | Verde   | `#39FF14` |
| CONSENSUS | Magenta | `#FF00FF` |

## Permissões

- `CAMERA` — obrigatória em todas as versões
- `WRITE_EXTERNAL_STORAGE` + `READ_EXTERNAL_STORAGE` — apenas Android 8/9 (API ≤ 28)
