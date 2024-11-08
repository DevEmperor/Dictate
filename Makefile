### Variables
.DEFAULT_GOAL := help
OUT := /dev/null

GRADLE := ./gradlew
APK_SOURCE := app/build/outputs/apk/debug/app-debug.apk
APK_TARGET := ./dictate.apk

### Basic
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'
	printf $(_TITLE) "FirstTime: prepare/all, OUT=/dev/stdout (Debug) "

### APK Setup
build: ## Build the APK
	@printf $(_TITLE) "Build" "Building APK"
	@$(GRADLE)

copy-apk:
	@printf $(_TITLE) "Copy" "Copying APK to Root"
	@cp $(APK_SOURCE) $(APK_TARGET)

remove-apk:
	@printf $(_TITLE) "Remove" "Removing APK from Root"
	@rm $(APK_TARGET)

### Emulator
verify-emulator:
	printf $(_TITLE) "Verify" "Checking emulator status"
	if adb devices | grep -q "localhost:5555.*device"; then \
		printf $(_INFO) "Status" "SUCCESS - Emulator is running"; \
	else \
		printf $(_WARN) "Status" "FAILED - Emulator not running"; \
		exit 1; \
	fi

adb-install: verify-emulator
	printf $(_TITLE) "Install" "Installing APK to emulator"
	adb -s localhost:5555 install -r $(APK_TARGET) > $(OUT) || (printf $(_WARN) "Error" "Failed to install APK" && exit 1)
	printf $(_INFO) "Success" "APK installed successfully"

### Workflows
info: ## Info
infos: info ## Extended Info
prepare: ## Onetime Setup
setup: build copy-apk ## Setup
install: setup adb-install ## Build and install APK to emulator
clean: remove-apk ## Clean
reset: clean setup info ## Reset
all:prepare reset ## Run All Targets

### Formatting
_INFO := "\033[33m[%s]\033[0m %s\n"  # Yellow text for "printf"
_DETAIL := "\033[34m[%s]\033[0m %s\n"  # Blue text for "printf"
_TITLE := "\033[32m[%s]\033[0m %s\n" # Green text for "printf"
_WARN := "\033[31m[%s]\033[0m %s\n" # Red text for "printf"