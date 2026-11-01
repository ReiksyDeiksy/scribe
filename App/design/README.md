# Puffin identity and turquoise theme

The selected concept is 09: a puffin whose body forms an S and a writing quill.
Its transparent foreground is the source asset in
`../app/src/main/res/drawable-nodpi/puffin_foreground.png`. It was extracted and
refined from the user's supplied concept using the built-in image generation
tool. The generation prompt is saved in `puffin-foreground.prompt.txt`.

The Android app uses this foreground for its turquoise adaptive launcher icon,
splash screen, and header. The adaptive foreground has an additional inset to
keep the beak and quill inside launcher masks. A simple vector silhouette serves
notifications and Android's themed monochrome launcher icons.

The primary colour is **#00CED1**, with **#20B2AA**, **#7FFFD4**, **#4682B4**, and
**#5F9EA0** supporting it. All colours live in `res/values/colors.xml`. Pale mint
surfaces and white cards accompany dark ink text. Small accent text and native
controls use a darker turquoise (#006D70) for contrast; bright turquoise is used
for the main action and selected navigation background.

Settings includes an optional dark theme, off by default. It changes surface and
text roles through `res/values-night/` while the five brand colours, turquoise
icon background, and dark lettering on primary buttons stay the same. Android's
native per-application night mode persists the choice and keeps the splash and
system bars consistent; see [Android's theme guidance](https://developer.android.com/develop/ui/views/theming/darktheme#change-themes-in-app).

This is a visual update to the existing Library / Courses / Settings interface.
Recording, import, post-recording transcription, retention, and export behaviour
remain as before. The earlier generated UI picture was a concept, not a product
specification; live transcription and a new Notes tab are not introduced.
