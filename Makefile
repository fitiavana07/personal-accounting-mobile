.PHONY: test release

test:
	./gradlew assembleDebug testDebugUnitTest

release:
	./scripts/release.sh
