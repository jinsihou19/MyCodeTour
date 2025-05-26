<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# MyCodeTour Changelog

## Unreleased

## 0.2.2

### Added
- Added support for wiki-style step linking with step reference copy from context menu

### Changed
- Changed resource loading relative path calculation to start from the parent directory of .tours file, affecting images and excalidraw files, making path resolution more intuitive

## 0.2.1

### Added
- Added support for Excalidraw embedded preview via `![[demo.excalidraw]]` links, enabling visual idea expansion
- Added ability to generate Steps from selected code blocks
- Implemented Step search functionality with right-click or Ctrl/Command+F shortcut
- Added click-to-edit in rendering interface for easier documentation
- Add What's New

### Changed
- Localized frontend resources

## 0.1.8

### Added
- Added support for multiple folder hierarchies
- Implemented navigation for Java variables, methods, and classes with on-demand generation
- Added PSI element line marking in code body

## 0.1.5

### Added
- New JCEF rendering interface
- Added drag-and-drop support for step reordering
- Implemented wiki-style links `[[com.Main#main]]` for code navigation
- Added image loading support
