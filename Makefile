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

copy-apk: ## Copy APK to Root
	@printf $(_TITLE) "Copy" "Copying APK to Root"
	@cp $(APK_SOURCE) $(APK_TARGET)

remove-apk: ## Remove APK from Root
	@printf $(_TITLE) "Remove" "Removing APK from Root"
	@rm $(APK_TARGET)

### Workflows
info: ## Info
infos: info ## Extended Info
prepare: ## Onetime Setup
setup: build copy-apk ## Setup
clean: remove-apk ## Clean
reset: clean setup info ## Reset
all:prepare reset ## Run All Targets

### Formatting
_INFO := "\033[33m[%s]\033[0m %s\n"  # Yellow text for "printf"
_DETAIL := "\033[34m[%s]\033[0m %s\n"  # Blue text for "printf"
_TITLE := "\033[32m[%s]\033[0m %s\n" # Green text for "printf"
_WARN := "\033[31m[%s]\033[0m %s\n" # Red text for "printf"