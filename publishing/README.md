# Publishing Find Me Clientside

Repository: https://github.com/Flurben/find-me-clientside

## Build the upload bundle

From the repository root, run:

```powershell
./scripts/Build-Release.ps1
```

This builds/tests the mod and produces `dist/modrinth-1.0.2/`, an upload-preparation ZIP, and
`dist/SHA256SUMS.txt`. Upload the **mod JAR** as the Minecraft mod; the publishing ZIP contains
listing text and assets and is not itself a mod or modpack.

The package contains the JAR, a 256px icon, genuine gameplay screenshots, a plain-text summary,
Markdown description, project/version field data, release notes, AI disclosure guidance, and licenses.
The packaging script populates `project.json`'s body and `version.json`'s changelog.
The version's `project_id` remains null until Modrinth assigns an ID.

## Modrinth: unlisted submission

The current project was primarily AI-generated through prompting and testing. Modrinth's
[AI policy](https://support.modrinth.com/en/articles/16551575-disclosure-and-usage-of-ai) limits public
listings for such projects; unlisted submission may be possible, subject to moderation. This package
does not claim guaranteed approval. No generative image model was used for the supplied icon/gallery.

1. Sign in to Modrinth and create a **mod** project named **Find Me Clientside**, slug `find-me-clientside`
   if available. Keep it as a **draft** while filling the listing.
2. Paste `modrinth/summary.txt` and `modrinth/description.md` into the summary and description fields.
3. Use `utility` and `storage` categories. Set the environment to **client-only**. In legacy fields,
   client is **required**, server is **unsupported**. Source and issues links are in `project.json`.
4. Use `icon-256.png` and upload the gallery images with the captions in `modrinth/gallery.json`.
   Preserve the icon's ISC attribution. You may replace it with your own original artwork; do not
   replace it with an AI-generated image for the Modrinth page.
5. Fill the content-disclosure controls using `modrinth/content-disclosures.md`: AI-generated code
   and AI-generated text are both disclosed. A description paragraph does not replace those controls.
6. Add version **1.0.2**, channel **Beta**, game version **1.20.1**, loader **Forge**, and the mod JAR.
   Add **EMI** as an **optional** dependency. Its project ID is `fRiHVvU7`.
7. Set the code license to **CC0-1.0**, retaining the separately licensed icon's ISC notices.
8. Request **unlisted** visibility and submit through Modrinth's review flow. A listed version
   within an unlisted project does not change the project's visibility. Respond to moderation feedback.

The JSON files are prepared field data/templates, not a promise that every field is accepted by
every API version. No API credential or account token is stored in this repository. Submission,
content-disclosure controls, and review remain account actions.

## GitHub

The source belongs in the public `Flurben/find-me-clientside` repository. The code license is CC0-1.0;
the original Lucide license is kept separately. Genuine screenshots can be used in issues/releases.

CI builds and runs tests on pushes and pull requests. The manually invoked **Prepare draft GitHub
release** workflow builds a package and creates a draft beta release for the version in
`gradle.properties`. It does not publish to Modrinth or change Modrinth visibility.

Before a later release, update `mod_version`, `CHANGELOG.md`, `publishing/release-notes.md`, and
the prepared version metadata, then rerun the packaging script. The script updates the version
number/title from Gradle; review descriptive text and compatibility claims before uploading.

References:
- https://docs.modrinth.com/api/operations/createproject/
- https://docs.modrinth.com/api/operations/createversion/
- https://support.modrinth.com/en/articles/16567675-content-disclosures
