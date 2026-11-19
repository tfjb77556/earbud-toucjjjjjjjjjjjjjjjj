# Earbud Touch

Turn your earbuds' touch sensor into a custom remote: open apps or type text with your own tap patterns.

## Build the APK (no coding)
### Option A - GitHub (easiest)
1. Create a free GitHub account and a new empty repository.
2. Upload ALL files of this folder (keep the folders, including the hidden `.github` folder).
3. Open the repo > Actions > "Build APK" > wait ~3-5 min.
4. Open the finished run > Artifacts > download `EarbudTouch-apk` > unzip > `app-debug.apk`.
5. Copy to your phone, install it (allow "install unknown apps").

### Option B - Android Studio
File > Open this folder > Build > Build APK(s). The file appears in app/build/outputs/apk/debug/.

## First run
1. Open the app, switch on "Earbuds control".
2. Press the Accessibility button and turn on "Earbud Touch" (needed to open apps from the background and to type). No "display over other apps" permission is needed.
3. Tap your earbuds and watch the Live monitor to see which symbols they send.
4. "+ Add gesture": record a pattern from your earbuds, choose what it does, Save.

## Pattern symbols
- S short tap, L long press
- N double tap (earbuds firmware sends "next"), P triple tap (sends "previous")
Examples: SLS = short, long, short. SS = two quick taps (if your earbuds send two play/pause events).

## Notes
- Set your earbuds' own touch settings (their companion app) so a single tap = play/pause.
- Some earbuds keep long press for themselves (noise control, voice assistant) and never send it to the phone.
