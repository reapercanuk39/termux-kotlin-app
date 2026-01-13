# F-Droid Metadata

This directory contains metadata for F-Droid submission following the [Fastlane Supply](https://docs.fastlane.tools/actions/supply/) structure.

## Current Status: ✅ Ready for Submission

## Structure
```
fastlane/metadata/android/en-US/
├── full_description.txt    ✅ Complete
├── short_description.txt   ✅ Complete  
├── title.txt               ✅ Complete
├── changelogs/
│   ├── 118.txt             ✅ Current version
│   └── 119.txt             ✅ Next version
└── images/
    ├── icon.png            ✅ App icon
    └── phoneScreenshots/
        ├── 1.jpg           ✅ Screenshot 1
        ├── 2.jpg           ✅ Screenshot 2
        ├── 3.jpg           ✅ Screenshot 3
        └── 4.jpg           ✅ Screenshot 4
```

## Submission Checklist
- [x] Full description (4000 chars max)
- [x] Short description (80 chars max)
- [x] App icon (512x512 PNG)
- [x] Screenshots (4 provided)
- [x] Changelog for current version
- [ ] Feature graphic (1024x500 PNG) - Optional

## How to Submit to F-Droid

### Option 1: Request for Packaging (RFP)

1. Go to https://gitlab.com/fdroid/rfp/-/issues
2. Click "New Issue"
3. Use this template:

```markdown
### App Name
Termux Kotlin

### App Website/Source
https://github.com/reapercanuk39/termux-kotlin-app

### Description
Termux is a terminal emulator with a large set of command line utilities.
This is a complete Kotlin rewrite of the original Termux app.

### License
GPL-3.0

### Upstream has included metadata
Yes - fastlane/metadata/android/en-US/

### The app compiles
Yes - Builds with Gradle, no proprietary dependencies

### The app works
Yes - Fully functional terminal emulator
```

### Option 2: Direct Merge Request to fdroiddata

1. Fork https://gitlab.com/fdroid/fdroiddata
2. Create metadata file `metadata/com.termux.kotlin.yml`:

```yaml
Categories:
  - Development
  - System
License: GPL-3.0-only
AuthorName: reapercanuk39
AuthorWebSite: https://github.com/reapercanuk39
SourceCode: https://github.com/reapercanuk39/termux-kotlin-app
IssueTracker: https://github.com/reapercanuk39/termux-kotlin-app/issues
Changelog: https://github.com/reapercanuk39/termux-kotlin-app/releases

AutoName: Termux Kotlin

RepoType: git
Repo: https://github.com/reapercanuk39/termux-kotlin-app.git

Builds:
  - versionName: 0.118.0
    versionCode: 118
    commit: v1.0.1
    subdir: app
    gradle:
      - yes
    ndk: r27c
    prebuild:
      - sed -i -e '/google()/d' ../build.gradle

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.118.0
CurrentVersionCode: 118
```

3. Submit merge request to fdroiddata

### Build Requirements for F-Droid

The app must build reproducibly. Key requirements:
- No proprietary dependencies ✅
- No tracking/analytics ✅
- Open source license (GPL-3.0) ✅
- Buildable with free tools ✅

### Updating for New Releases

When releasing a new version:
1. Update `versionCode` and `versionName` in `app/build.gradle`
2. Add changelog: `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
3. Tag the release: `git tag v<version>`
4. Push: `git push origin v<version>`

F-Droid will automatically pick up new versions if AutoUpdateMode is enabled.
