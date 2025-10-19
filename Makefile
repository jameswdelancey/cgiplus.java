JAVAC ?= javac
JAVA  ?= java
JFLAGS ?=
SRC_DIR := src
OUT_DIR := build
CP := $(OUT_DIR)

SOURCES := $(shell find $(SRC_DIR) -name "*.java" 2>/dev/null)

all: serve

serve: compile
	$(JAVA) -cp $(CP) app.Main

compile:
	@mkdir -p $(OUT_DIR)
	$(JAVAC) $(JFLAGS) -d $(OUT_DIR) $(SOURCES)

# Called by the server per-request / per-job
run: compile
	@[ -n "$(CLASS)" ] || (echo "CLASS not set"; exit 2)
	@$(JAVA) -cp $(CP) $(CLASS) "$(Q)"

fake-adapters: compile
	@$(JAVA) -cp $(CP) tools.FakeAdaptersCli $(ARGS)

clean:
	@rm -rf $(OUT_DIR)
