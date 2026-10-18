# Icon provenance

- Graphic: Lucide `package-search`.
- Upstream: https://github.com/lucide-icons/lucide
- Pinned revision: `1ef60af944de207e0d5176905a0759ba12e6ee8d`.
- Original SVG: https://github.com/lucide-icons/lucide/blob/1ef60af944de207e0d5176905a0759ba12e6ee8d/icons/package-search.svg
- License: ISC; full upstream notices in [LICENSES/Lucide.txt](../../LICENSES/Lucide.txt).
- `package-search.svg` is the unchanged downloaded source.
- `icon-256.png` and `icon-512.png` are black-on-white raster exports of that source.
- `src/main/resources/icon.png` is the same 256px export, used in Forge's mod list.

No image-generation prompt was used. These exports use an existing open-source graphic rather
than AI-generated artwork. The icon is shared stock artwork, not an exclusive logo or trademark.

Reproduce the exports with ImageMagick:

```powershell
magick -background white -density 1536 assets/branding/package-search.svg -resize 512x512 -alpha remove -alpha off assets/branding/icon-512.png
magick -background white -density 768 assets/branding/package-search.svg -resize 256x256 -alpha remove -alpha off assets/branding/icon-256.png
```
