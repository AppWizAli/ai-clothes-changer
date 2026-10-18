# AI Clothes Changer

Android virtual try-on app: pick a photo, pick an outfit, generate, compare original vs. result, save and share.
Dark neon design modelled on the reference app's store listing. **No ads, no ad SDK, no forced screens.**

## Status — read this first

* The app, its pipeline and its failure handling are implemented and tested (see below).
* **Real AI generation is not connected.** No AI provider/backend was configured in this repository, so none
  could be tested. Implement the small contract in [`docs/BACKEND_API.md`](docs/BACKEND_API.md), set
  `tryon.backendUrl`, and generation works. Until then the app says generation isn't configured and never fakes it.
* There is no built-in outfit catalogue (no licensed garment images exist in the project); users import garments.

## Build & run

Requirements: JDK 21, Android SDK (API 37 platform, build-tools 36+).

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
echo "tryon.backendUrl=https://your-backend.example.com" >> local.properties   # optional, see docs
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # unit tests
```

### Try the whole pipeline without an AI service

```bash
pip install pillow
python3 tools/mock-backend/server.py --mode ok     # also: fail | rate-limit | server-error | junk | slow
echo "tryon.backendUrl=http://10.0.2.2:8099" >> local.properties   # emulator → host machine
```
The mock returns a watermarked composite, clearly *not* an AI result. It exists to exercise upload, polling,
cancel, retry and error handling.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for structure and design decisions.
