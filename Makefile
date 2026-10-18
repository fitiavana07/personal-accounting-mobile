.PHONY: test debug release changelog

DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk

test:
	./gradlew assembleDebug testDebugUnitTest

debug:
	./gradlew assembleDebug
	@echo "Debug APK: $(abspath $(DEBUG_APK))"

changelog:
	./scripts/changelog.sh commit

release:
	./scripts/release.sh
