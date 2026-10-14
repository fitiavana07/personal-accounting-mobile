.PHONY: test debug release

DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk

test:
	./gradlew assembleDebug testDebugUnitTest

debug:
	./gradlew assembleDebug
	@echo "Debug APK: $(abspath $(DEBUG_APK))"

release:
	./scripts/release.sh
