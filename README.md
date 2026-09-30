# Bytus

https://discord.gg/qPvKA5BH

Bytus is a Java bytecode obfuscator.

![Bytus](https://github.com/user-attachments/assets/d3eb33d2-e277-421a-94da-a86626bdf03e)

## Project status

> Warning: Bytus is highly unstable, so is the obfuscated output. It is not production ready.

This project should be considered a research and passion project rather than a finished product.

## License

This project is licensed under the GNU General Public License v3.0 (GPL-3.0).

See the [LICENSE](LICENSE) file for the full license text.

## Requirements

- JDK 26
- Git
- Gradle

## Setup & Usage

Bytus depends on ASMPlus, which must be built and published to your local Maven repository before building Bytus itself.

### 1. Clone ASMPlus

```bash
git clone https://github.com/Exeos/ASMPlus.git
cd ASMPlus
```

### 2. Build and publish ASMPlus to Maven Local

```bash
./gradlew publishToMavenLocal
```

### 3. Clone Bytus

```bash
git clone https://github.com/Exeos/Bytus.git
cd Bytus
```

### 4. Configure the project

Copy the example configuration file:

```bash
cp config.json.example config.json
```

Open `config.json` and configure it

### 5. Run Bytus

```bash
./gradlew :cli:run --args="path/to/config.json"
```
