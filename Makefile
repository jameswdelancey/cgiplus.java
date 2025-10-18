JAVAC ?= javac
JAVA  ?= java
JFLAGS ?=
SRC_DIR := src
OUT_DIR := build
CP := $(OUT_DIR):lib/json.jar

SOURCES := $(shell find $(SRC_DIR) -name "*.java" 2>/dev/null)

all: serve

serve: deps compile
	$(JAVA) -cp $(CP) app.Main

compile: deps
	@mkdir -p $(OUT_DIR)
	$(JAVAC) $(JFLAGS) -cp lib/json.jar -d $(OUT_DIR) $(SOURCES)

# Called by the server per-request / per-job
run: deps compile
	@[ -n "$(CLASS)" ] || (echo "CLASS not set"; exit 2)
	@$(JAVA) -cp $(CP) $(CLASS) "$(Q)"

lib/json.jar:
	@mkdir -p lib
	@echo "Downloading JSON jar (once)..."
	@curl -fsSL -o lib/json.jar https://repo1.maven.org/maven2/org/json/json/20210307/json-20210307.jar

deps: lib/json.jar

clean:
	@rm -rf $(OUT_DIR)
