# Proton 10 File Structure Reference
## Complete Guide for GameNative & Winlator Compatibility

**Document Version:** 1.0
**Date:** November 20, 2025
**Subject:** Proton 10.0 ARM64EC File Structure Analysis
**Purpose:** Template for future Wine/Proton package conversions

---

## Table of Contents

1. [Overview](#overview)
2. [Original File Structure](#original-file-structure)
3. [WCP Converted Structure](#wcp-converted-structure)
4. [Conversion Mapping](#conversion-mapping)
5. [File Specifications](#file-specifications)
6. [Integration Requirements](#integration-requirements)
7. [Future Conversion Template](#future-conversion-template)

---

## Overview

### Package Types

GameNative/Winlator supports two packaging methods for Wine/Proton distributions:

#### Method 1: Two-File Pattern (Legacy/Assets)
- **Binary Archive:** `<name>-<version>-<arch>.txz` (stored in Git LFS)
- **Container Pattern:** `<name>-<version>-<arch>_container_pattern.tzst` (in assets)
- **Used by:** Proton 9.0, Wine 9.0 (existing in codebase)

#### Method 2: WCP (Wine Content Package)
- **Single Archive:** `<name>-<version>-<arch>.wcp` (tar.xz format)
- **Installable:** Through ContentsManager UI or manual extraction
- **Used by:** User-installed Wine/Proton variants

### Proton 10.0 ARM64EC Statistics

| File | Size | File Count | Format |
|------|------|------------|--------|
| Original Binary (TXZ) | 223 MB | 1,760 files | tar.xz |
| Container Pattern (TZST) | 27 MB | 473 files | tar.zst |
| Input DLLs (TZST) | 1.0 MB | 9 files | tar.zst |
| **WCP Package** | **251 MB** | **1,761 files** | **tar.xz** |

---

## Original File Structure

### File 1: `proton-10.0-arm64ec.txz` (223 MB, 1,760 files)

**SHA256:** `d7c106284c839b7a03ab082b32ca45ff7f88bd3abaea8f1ecc4d6ce134a064aa`

#### Top-Level Structure

```text
proton-10.0-arm64ec.txz
├── bin/                    (17 executables)
├── lib/                    (Wine libraries)
└── share/                  (Resources, fonts, configs)
```

#### Detailed Directory Layout

##### `bin/` - Wine Executables (17 files)

```text
bin/
├── wine                    # Main Wine loader
├── wineserver             # Wine server daemon
├── winecfg                # Wine configuration GUI
├── wineboot               # Wine initialization
├── winedbg                # Wine debugger
├── winepath               # Path converter
├── wineconsole            # Console window
├── winefile               # File manager
├── winemine               # Minesweeper game
├── notepad                # Notepad application
├── regedit                # Registry editor
├── regsvr32               # DLL registration
├── msiexec                # MSI installer
├── msidb                  # MSI database tool
└── wine-preloader         # ELF loader
```

##### `lib/wine/` - Wine Libraries (1,737 files)

```text
lib/wine/
├── aarch64-unix/          (34 files - Native ARM64 Unix libraries)
│   ├── advapi32.so
│   ├── kernel32.so
│   ├── ntdll.so
│   └── ... (Unix-side Wine components)
│
├── aarch64-windows/       (757 files - ARM64 Windows PE libraries)
│   ├── kernel32.dll
│   ├── ntdll.dll
│   ├── user32.dll
│   ├── gdi32.dll
│   ├── d3d11.dll
│   ├── dxgi.dll
│   ├── xinput1_4.dll
│   ├── dinput8.dll
│   └── ... (Windows DLLs for ARM64)
│
└── i386-windows/          (816 files - x86 Windows PE libraries for WoW64)
    ├── kernel32.dll
    ├── ntdll.dll
    ├── user32.dll
    ├── gdi32.dll
    └── ... (32-bit Windows DLLs)
```

**Critical Libraries by Category:**

| Category | Examples | Location |
|----------|----------|----------|
| Core Runtime | ntdll.dll, kernel32.dll, kernelbase.dll | All architectures |
| Graphics | d3d9.dll, d3d11.dll, dxgi.dll, opengl32.dll | aarch64/i386-windows |
| Input | dinput.dll, dinput8.dll, xinput1_*.dll, hid.dll | aarch64/i386-windows |
| Audio | dsound.dll, xaudio2_*.dll, winmm.dll | aarch64/i386-windows |
| Networking | ws2_32.dll, wininet.dll, urlmon.dll | aarch64/i386-windows |
| COM/OLE | ole32.dll, oleaut32.dll, combase.dll | aarch64/i386-windows |

##### `share/wine/` - Resources (6 files + fonts/nls)

```text
share/wine/
├── wine.inf               # Wine initialization file
├── fonts/                 # TrueType and FON fonts (100+ files)
│   ├── tahoma.ttf
│   ├── tahomabd.ttf
│   ├── marlett.ttf
│   ├── symbol.ttf
│   ├── webdings.ttf
│   ├── wingding.ttf
│   └── ... (various FON files)
└── nls/                   # National Language Support files
```

---

### File 2: `proton-10.0-arm64ec_container_pattern.tzst` (27 MB, 473 files)

**SHA256:** `0327cbc6bdd5addf5707ecc20b6d91a4f12792af613b903442d5e7c32b7b1855`

#### Wine Prefix Structure

This file contains a complete Wine prefix template (`.wine/` directory) that gets extracted to each container's directory.

```text
.wine/                             # Wine prefix root
├── .update-timestamp              # Installation timestamp
│
├── system.reg                     # System registry (3.8 MB)
├── user.reg                       # User registry (50 KB)
├── userdef.reg                    # User defaults (4 KB)
│
├── dosdevices/                    # DOS drive mappings
│   ├── c: -> ../drive_c           # Symlink to C: drive
│   └── z: -> /                    # Symlink to Unix root
│
└── drive_c/                       # C: drive contents
    ├── ProgramData/               # Application data
    ├── Program Files/             # 64-bit programs
    ├── Program Files (x86)/       # 32-bit programs (WoW64)
    ├── users/                     # User profiles
    │   └── <username>/
    │       ├── AppData/
    │       ├── Desktop/
    │       ├── Documents/
    │       └── ...
    │
    └── windows/                   # Windows system directory
        ├── system32/              # 64-bit system files (NLS, drivers)
        │   ├── c_*.nls           # Code page tables (100+ files)
        │   ├── drivers/          # System drivers
        │   └── tasks/            # Task scheduler
        │
        ├── syswow64/             # 32-bit system files (WoW64)
        │
        ├── Fonts/                # Windows fonts
        ├── inf/                  # Driver information files
        ├── resources/            # UI resources
        ├── winsxs/               # Side-by-side assemblies
        ├── temp/                 # Temporary files
        │
        ├── explorer.exe          # Windows Explorer
        ├── notepad.exe           # Notepad
        ├── regedit.exe           # Registry Editor
        ├── system.ini            # Legacy system config
        └── win.ini               # Legacy Windows config
```

**Registry Files:**

| File | Size | Purpose |
|------|------|---------|
| `system.reg` | 3.8 MB | System-wide Wine configuration (Direct3D, drivers, etc.) |
| `user.reg` | 50 KB | User-specific settings (file associations, desktop) |
| `userdef.reg` | 4 KB | Default user settings template |

**Important Registry Keys:**
- `Software\\Wine\\Direct3D` - Graphics settings (renderer, CSMT, etc.)
- `Software\\Wine\\Drivers` - Driver configuration
- `Software\\Microsoft\\Windows NT\\CurrentVersion` - Windows version info

---

### File 3: `proton10_arm64ec_input_dlls.tzst` (1.0 MB, 9 files)

**SHA256:** `2737ace0b3e30bf094f11b2400a9a9e59117eeea2d80b258e44d5e5ce7fde004`

#### Input DLLs for Game Controllers

These are ARM64 Windows DLLs extracted from `lib/wine/aarch64-windows/` for game input support:

```text
proton10_arm64ec_input_dlls.tzst
├── dinput.dll              # DirectInput (legacy input API)
├── dinput8.dll             # DirectInput 8 (modern input API)
├── xinput1_1.dll           # XInput 1.1
├── xinput1_2.dll           # XInput 1.2
├── xinput1_3.dll           # XInput 1.3
├── xinput1_4.dll           # XInput 1.4 (latest)
├── xinput9_1_0.dll         # XInput 9.1.0
├── winmm.dll               # Windows Multimedia (joystick API)
└── hid.dll                 # Human Interface Device
```

**Purpose:** These DLLs are extracted separately to the Wine installation directory during first boot to ensure game controller support works correctly.

---

## WCP Converted Structure

### File: `proton-10.0-arm64ec.wcp` (251 MB, 1,761 files)

**SHA256:** `c1638b1f830304222660e338bc032099145ad210d4e36a07d91f39c750a062f7`

#### WCP Package Layout

```text
proton-10.0-arm64ec.wcp (tar.xz archive)
│
├── profile.json            # WCP metadata (287 bytes)
│
├── bin/                    # Wine executables (17 files)
│   └── ... (identical to original TXZ)
│
├── lib/                    # Wine libraries (1,737 files)
│   └── wine/
│       ├── aarch64-unix/   (34 files)
│       ├── aarch64-windows/ (757 files)
│       └── i386-windows/   (816 files)
│
├── share/                  # Resources (6 files + fonts)
│   └── wine/
│       ├── wine.inf
│       ├── fonts/
│       └── nls/
│
└── prefixPack.tzst         # Wine prefix template (27 MB, 473 files)
    └── .wine/              (complete prefix structure)
```

#### `profile.json` Specification

```json
{
  "type": "Proton",
  "versionName": "10.0-arm64ec",
  "versionCode": 2,
  "description": "Proton 10.0 ARM64EC - Windows compatibility layer with improved gaming support",
  "files": [],
  "proton": {
    "binPath": "bin",
    "libPath": "lib",
    "prefixPack": "prefixPack.tzst"
  }
}
```

**Metadata Block Key:**
Both `"wine"` and `"proton"` keys are supported for Wine/Proton packages. Use `"proton"` for Proton packages and `"wine"` for Wine packages. The loader checks for `"proton"` first, then falls back to `"wine"` for backward compatibility (see `ContentsManager.readProfile()`).

**Field Specifications:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | String | ✅ | "Wine" or "Proton" |
| `versionName` | String | ✅ | Format: `<major>.<minor>-<arch>` (e.g., "10.0-arm64ec") |
| `versionCode` | Integer | ✅ | Incremental version number (1, 2, 3...) |
| `description` | String | ✅ | Human-readable description |
| `files` | Array | ✅ | Additional files to copy (empty for Wine packages) |
| `wine.binPath` / `proton.binPath` | String | ✅ | Relative path to `bin/` directory |
| `wine.libPath` / `proton.libPath` | String | ✅ | Relative path to `lib/` directory |
| `wine.prefixPack` / `proton.prefixPack` | String | ✅ | Filename of prefix archive (must be .txz or .tzst) |

---

## Conversion Mapping

### Original → WCP Transformation

```text
ORIGINAL FILES                      WCP STRUCTURE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
proton-10.0-arm64ec.txz      ──┐
├── bin/                     ──┼──→ bin/
├── lib/                     ──┼──→ lib/
└── share/                   ──┘  └─→ share/

proton-10.0-arm64ec_         ──┐
  container_pattern.tzst       │
└── .wine/                   ──┼──→ prefixPack.tzst
                               │    └── .wine/
                               │
(New file)                   ──┘  └─→ profile.json
```

### File Size Breakdown

| Component | Original | WCP | Change |
|-----------|----------|-----|--------|
| Wine Binaries | 223 MB (TXZ) | 223 MB (in WCP) | ±0 MB |
| Container Pattern | 27 MB (TZST) | 27 MB (prefixPack.tzst) | ±0 MB |
| Metadata | N/A | <1 KB (profile.json) | +1 KB |
| Compression | tar.xz + tar.zst | tar.xz only | Unified |
| **Total** | **250 MB (2 files)** | **251 MB (1 file)** | **+1 MB** |

---

## File Specifications

### Archive Format Requirements

#### TXZ (XZ Compressed Tar)
```bash
# Create TXZ archive
tar -cJf output.txz input_directory/

# Compression level (default: -6)
tar --use-compress-program='xz -9' -cf output.txz input_directory/

# Extract
tar -xJf archive.txz
```

#### TZST (Zstandard Compressed Tar)
```bash
# Create TZST archive (high compression)
tar --use-compress-program='zstd -19 -T0' -cf output.tzst input_directory/

# Extract
tar --use-compress-program=unzstd -xf archive.tzst
# or
tar --zstd -xf archive.tzst  # if tar supports --zstd
```

### Naming Conventions

#### Wine/Proton Identifier Format

```text
<type>-<major>.<minor>-<arch>

Examples:
  proton-10.0-arm64ec     ✅ Correct
  wine-9.2-x86_64         ✅ Correct
  proton-10-arm64ec       ❌ Missing minor version
  Proton-10.0-arm64ec     ❌ Uppercase type
```

**Architecture Codes:**
- `arm64ec` - ARM64 with Windows x86 emulation compatibility
- `x86_64` - x86-64 (AMD64)
- `x86` - 32-bit x86 (rare)

#### File Naming Patterns

| Format | Pattern | Example |
|--------|---------|---------|
| **TXZ Binary** | `<identifier>.txz` | `proton-10.0-arm64ec.txz` |
| **Container Pattern** | `<identifier>_container_pattern.tzst` | `proton-10.0-arm64ec_container_pattern.tzst` |
| **Input DLLs** | `<name>_input_dlls.tzst` | `proton10_arm64ec_input_dlls.tzst` |
| **WCP Package** | `<identifier>.wcp` | `proton-10.0-arm64ec.wcp` |

---

## Integration Requirements

### GameNative Assets Integration (Method 1)

#### Step 1: Add to `arrays.xml`

**File:** `app/src/main/res/values/arrays.xml`

```xml
<resources>
    <string-array name="bionic_wine_entries">
        <item>proton-9.0-arm64ec</item>
        <item>proton-10.0-arm64ec</item>     <!-- ADD THIS -->
        <item>wine-9.0-x86_64</item>
    </string-array>
</resources>
```

#### Step 2: Place Assets

```text
app/src/main/assets/
├── proton-10.0-arm64ec.txz                    # Git LFS pointer (133 bytes)
├── proton-10.0-arm64ec_container_pattern.tzst # 27 MB
└── proton10_arm64ec_input_dlls.tzst           # 1 MB (optional)
```

#### Step 3: Git LFS Setup

```bash
# Track TXZ files with Git LFS
git lfs track "app/src/main/assets/*.txz"

# Add the pointer file (NOT the actual 223 MB file)
git add app/src/main/assets/proton-10.0-arm64ec.txz

# Commit (Git LFS handles the upload)
git commit -m "Add Proton 10.0 ARM64EC binaries"
```

#### Step 4: Code Integration (Optional - for input DLLs)

**File:** `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`


Note: This function is no longer required as they've fixed the issue a newer release, but keeping it here for reference.
Add extraction function:
```kotlin
private fun extractProton10Arm64ecInputDLLs(context: Context, container: Container) {
    val wineVersion = container.wineVersion
    if (wineVersion != null && wineVersion.contains("proton-10.0-arm64ec")) {
        val wineDir = ImageFs.find(context).getInstalledWineDir()
        val wineFolder = File(wineDir, container.wineVersion)
        val inputAsset = "proton10_arm64ec_input_dlls.tzst"

        Timber.d("Extracting Proton 10 arm64ec input DLLs to ${wineFolder.path}")
        val success = TarCompressorUtils.extract(
            TarCompressorUtils.Type.ZSTD,
            context.assets,
            inputAsset,
            wineFolder
        )
        if (!success) {
            Timber.e("Failed to extract Proton 10 arm64ec input DLLs")
        }
    }
}
```

Call in `setupWineSystemFiles()`:
```kotlin
if (firstTimeBoot) {
    extractArm64ecInputDLLs(context, container)           // Proton 9
    extractx86_64InputDlls(context, container)            // Proton 9
    extractProton10Arm64ecInputDLLs(context, container)   // Proton 10 - NEW
}
```

---

### WCP Installation (Method 2)

#### Installation Paths

WCP files are extracted to:

```text
/data/data/app.gamenative/files/contents/wine/<version_identifier>-<vercode>/
```

**Example for Proton 10.0:**

```text
/data/data/app.gamenative/files/contents/wine/10.0-arm64ec-02/
├── bin/
├── lib/
├── share/
├── prefixPack.tzst
└── profile.json
```

**Naming Pattern:** `<versionName>-<verCode>` → `10.0-arm64ec-02`

#### ContentsManager Flow

```java
// File: com/winlator/contents/ContentsManager.java

public void extraContentFile(Uri uri, OnInstallFinishedCallback callback) {
    // 1. Extract WCP to temp directory
    TarCompressorUtils.extract(Type.XZ, context, uri, tmpDir);

    // 2. Read and validate profile.json
    ContentProfile profile = readProfile(new File(tmpDir, "profile.json"));

    // 3. Validate required files exist
    //    - For Wine: bin/, lib/, prefixPack.txz/tzst

    // 4. Move to permanent location
    File installDir = getInstallDir(context, profile);
    FileUtils.move(tmpDir, installDir);

    callback.onSucceed(profile);
}
```

#### UI Import Flow

1. User opens **Settings → Emulation**
2. Taps **"Install additional components (.wcp)"**
3. Selects `proton-10.0-arm64ec.wcp` from file picker
4. ContentsManager extracts and validates
5. Wine version appears in container creation dropdown

---

## Future Conversion Template

### Prerequisites Checklist

- [ ] Wine/Proton binaries compiled for target architecture
- [ ] Wine prefix created and tested
- [ ] Input DLLs identified (if needed)
- [ ] Version naming decided (follows `<type>-<major>.<minor>-<arch>`)

### Step-by-Step Conversion Process

#### Phase 1: Prepare Source Files

```bash
# Organize source files
mkdir -p /tmp/wine_conversion
cd /tmp/wine_conversion

# Expected structure:
wine_source/
├── bin/          # Wine executables
├── lib/          # Wine libraries
│   └── wine/
│       ├── <arch>-unix/
│       ├── <arch>-windows/
│       └── i386-windows/    (for WoW64)
├── share/        # Resources
└── .wine/        # Wine prefix template
```

#### Phase 2: Create Binary Archive

```bash
VERSION="10.0"
ARCH="arm64ec"
TYPE="proton"
IDENTIFIER="${TYPE}-${VERSION}-${ARCH}"

# Package binaries (TXZ)
tar -cJf "${IDENTIFIER}.txz" bin/ lib/ share/

# Verify
ls -lh "${IDENTIFIER}.txz"
tar -tJf "${IDENTIFIER}.txz" | head -20
```

#### Phase 3: Create Container Pattern

```bash
# Package Wine prefix (TZST with high compression)
tar --use-compress-program='zstd -19 -T0' \
    -cf "${IDENTIFIER}_container_pattern.tzst" \
    .wine/

# Verify
ls -lh "${IDENTIFIER}_container_pattern.tzst"
tar -tvf "${IDENTIFIER}_container_pattern.tzst" | head -20
```

#### Phase 4: Extract Input DLLs (Optional)

```bash
# Identify input DLLs
INPUT_DLLS=(
    "dinput.dll"
    "dinput8.dll"
    "xinput1_1.dll"
    "xinput1_2.dll"
    "xinput1_3.dll"
    "xinput1_4.dll"
    "xinput9_1_0.dll"
    "winmm.dll"
    "hid.dll"
)

# Extract from library
cd lib/wine/<arch>-windows/
tar --use-compress-program='zstd -19 -T0' \
    -cf "/tmp/${IDENTIFIER}_input_dlls.tzst" \
    "${INPUT_DLLS[@]}"
```

#### Phase 5: Create WCP Package

```bash
cd /tmp/wine_conversion

# 1. Extract container pattern temporarily
mkdir -p prefix_temp
tar --use-compress-program=unzstd \
    -xf "${IDENTIFIER}_container_pattern.tzst" \
    -C prefix_temp/

# 2. Repackage as prefixPack.tzst
cd prefix_temp
tar --use-compress-program='zstd -19 -T0' \
    -cf ../prefixPack.tzst .wine/
cd ..
rm -rf prefix_temp

# 3. Create profile.json
cat > profile.json << EOF
{
  "type": "$(echo ${TYPE} | sed 's/\b\(.\)/\u\1/g')",
  "versionName": "${VERSION}-${ARCH}",
  "versionCode": 2,
  "description": "$(echo ${TYPE} | sed 's/\b\(.\)/\u\1/g') ${VERSION} ${ARCH^^} - Windows compatibility layer",
  "files": [],
  "wine": {
    "binPath": "bin",
    "libPath": "lib",
    "prefixPack": "prefixPack.tzst"
  }
}
EOF

# 4. Extract original TXZ
mkdir wcp_contents
cd wcp_contents
tar -xJf "../${IDENTIFIER}.txz"
mv ../prefixPack.tzst .
mv ../profile.json .

# 5. Create final WCP
tar -cJf "../${IDENTIFIER}.wcp" *
cd ..

# 6. Verify
ls -lh "${IDENTIFIER}.wcp"
tar -tJf "${IDENTIFIER}.wcp" | head -30
```

#### Phase 6: Validate WCP Structure

```bash
# Extract WCP for validation
mkdir wcp_test
tar -xJf "${IDENTIFIER}.wcp" -C wcp_test/

# Check structure
cd wcp_test
echo "=== Required Files ==="
ls -l profile.json prefixPack.tzst
echo "=== Directories ==="
ls -ld bin/ lib/ share/
echo "=== Profile Content ==="
cat profile.json | jq .
echo "=== Library Subdirectories ==="
ls -l lib/wine/
```

#### Phase 7: Create Checksums

```bash
# Generate SHA256 checksums for all files
cd /tmp/wine_conversion
sha256sum "${IDENTIFIER}.txz" > checksums.txt
sha256sum "${IDENTIFIER}_container_pattern.tzst" >> checksums.txt
sha256sum "${IDENTIFIER}_input_dlls.tzst" >> checksums.txt
sha256sum "${IDENTIFIER}.wcp" >> checksums.txt

cat checksums.txt
```

### Validation Checklist

**Binary Archive (TXZ):**
- [ ] Contains `bin/`, `lib/`, `share/` directories
- [ ] `lib/wine/<arch>-windows/` has 700+ DLL files
- [ ] `lib/wine/i386-windows/` exists (for WoW64 support)
- [ ] Executable files in `bin/` (wine, wineserver, etc.)

**Container Pattern (TZST):**
- [ ] Contains `.wine/` directory at root
- [ ] Has `system.reg`, `user.reg`, `userdef.reg`
- [ ] Has `drive_c/windows/system32/` structure
- [ ] Has `dosdevices/` with symlinks

**WCP Package:**
- [ ] Contains valid `profile.json` with all required fields
- [ ] Has `prefixPack.tzst` with correct Wine prefix
- [ ] Directory structure matches TXZ exactly
- [ ] Total size approximately TXZ + Container Pattern + 1 MB

**profile.json:**
- [ ] `type` is "Wine" or "Proton"
- [ ] `versionName` follows `<major>.<minor>-<arch>` format
- [ ] `versionCode` is incremental integer
- [ ] `wine.prefixPack` points to `prefixPack.tzst`

---

## Troubleshooting

### Common Issues

#### Issue: WCP Installation Fails with "ERROR_MISSINGFILES"

**Cause:** Required files not found in WCP archive

**Solution:**
```bash
# Verify WCP contents
tar -tJf package.wcp | grep -E "(bin/|lib/|prefixPack)"

# Required files:
# - profile.json
# - bin/ (directory with executables)
# - lib/ (directory with wine/ subdirectory)
# - prefixPack.tzst or prefixPack.txz
```

#### Issue: Container Creation Fails After Install

**Cause:** Missing container pattern or incorrect extraction

**Solution:**
```bash
# Check prefixPack.tzst contents
tar -tvf prefixPack.tzst | head -20

# Should show:
# .wine/
# .wine/system.reg
# .wine/drive_c/
# ...
```

#### Issue: Input DLLs Not Working

**Cause:** DLLs not extracted or wrong architecture

**Solution:**
```bash
# Verify DLL architecture matches
file lib/wine/aarch64-windows/dinput8.dll
# Should show: "PE32+ executable (DLL) (console) Aarch64"

# Check extraction in XServerScreen.kt
Timber.d("Extracting input DLLs...")  # Add logging
```

#### Issue: Version Not Appearing in UI

**Cause:** Identifier mismatch in arrays.xml

**Solution:**
```xml
<!-- Must match profile.json versionName exactly -->
<item>proton-10.0-arm64ec</item>   <!-- ✅ Correct -->
<item>proton-10-arm64ec</item>     <!-- ❌ Wrong -->
```

---

## References

### Code Files

| File | Purpose |
|------|---------|
| `ContentsManager.java` | WCP import/extraction logic |
| `ContainerManager.java` | Container pattern extraction |
| `WineInfo.java` | Version identifier parsing |
| `XServerScreen.kt` | Input DLL extraction |
| `arrays.xml` | Available Wine/Proton versions |

### External Documentation

- [Wine Documentation](https://www.winehq.org/documentation)
- [Proton GitHub](https://github.com/ValveSoftware/Proton)
- [GameNative Repository](https://github.com/utkarshdalal/GameNative)
- [Winlator](https://github.com/brunodev85/winlator)

---

## Appendix: Complete File Trees

### Proton 10.0 ARM64EC TXZ (First 100 files)

```text
./
./bin/
./bin/regsvr32
./bin/wineboot
./bin/wine-preloader
./bin/msidb
./bin/wineconsole
./bin/winefile
./bin/msiexec
./bin/regedit
./bin/winecfg
./bin/wineserver
./bin/winedbg
./bin/winepath
./bin/wine
./bin/winemine
./bin/notepad
./lib/
./lib/wine/
./lib/wine/aarch64-unix/
./lib/wine/i386-windows/
./lib/wine/aarch64-windows/
./lib/wine/aarch64-windows/view.exe
./lib/wine/aarch64-windows/sti.dll
./lib/wine/aarch64-windows/faultrep.dll
./lib/wine/aarch64-windows/icinfo.exe
./lib/wine/aarch64-windows/x3daudio1_7.dll
./lib/wine/aarch64-windows/msi.dll
./lib/wine/aarch64-windows/mpr.dll
./lib/wine/aarch64-windows/d3dx9_33.dll
./lib/wine/aarch64-windows/cmd.exe
./lib/wine/aarch64-windows/quartz.dll
./lib/wine/aarch64-windows/netio.sys
./lib/wine/aarch64-windows/ngen.exe
./lib/wine/aarch64-windows/iexplore.exe
./lib/wine/aarch64-windows/appwiz.cpl
./lib/wine/aarch64-windows/dinput8.dll
./lib/wine/aarch64-windows/xinput1_4.dll
./share/
./share/wine/
./share/wine/wine.inf
./share/wine/fonts/
./share/wine/nls/
... (1,760 total files)
```

### Container Pattern TZST (First 50 files)

```text
.wine/
.wine/system.reg
.wine/user.reg
.wine/dosdevices/
.wine/.update-timestamp
.wine/userdef.reg
.wine/drive_c/
.wine/drive_c/ProgramData/
.wine/drive_c/Program Files (x86)/
.wine/drive_c/Program Files/
.wine/drive_c/users/
.wine/drive_c/windows/
.wine/drive_c/windows/temp/
.wine/drive_c/windows/system32/
.wine/drive_c/windows/syswow64/
.wine/drive_c/windows/Fonts/
... (473 total files)
```

---

## Document End
