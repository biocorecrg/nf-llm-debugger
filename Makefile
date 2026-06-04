# Makefile per nf-llm-debugger (Nextflow Plugin)

.PHONY: clean assemble test install release

# Pulisce i file temporanei e i build Gradle
clean:
	./gradlew clean
	rm -rf work/ .nextflow* validation_run.log

# Compila e assembla il plugin (genera lo ZIP in build/distributions/)
assemble:
	./gradlew assemble

# Esegue gli unit test
test:
	./gradlew test

# Compila e installa il plugin localmente in ~/.nextflow/plugins/
install:
	./install-plugin.sh

# Rilascia/Pubblica il plugin
release:
	./gradlew releasePlugin
