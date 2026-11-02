# Android release requirements

- Every user-visible Android update must add a short entry to the in-app patch
  notes, in English and Romanian. Explain how to use new features when needed.
- Keep earlier entries in the small history under Settings > Patch notes.
- Update `PatchNotes.java`, its localized `patch_strings.xml` resources, and the
  app version together. The newest note must match the installed version.
- Keep notes written for Mara: what changed and what to tap, without developer
  jargon or claims of accuracy/performance improvements that have not been tested.
- Write personal app messages and instructions in the user's voice: "send me"
  and "our chat", with equivalent Romanian wording. Do not refer to the author
  by name in the third person or append an author-name signature.
- End only in-app update/patch-note messages with exactly `Love ya, Mara <3`, including
  old patch notes. Do not append it to assistant replies or Help. Keep it in English in
  both locales. The shared `personal_signoff` resource supplies it automatically
  in patch-note windows; do not duplicate it in individual release bodies.
- Explicit wording for a particular update overrides that default closing.
  Version 0.1.5-test uses `I love youuuu` then `Danny~` instead, in both locales.
  Do not append both closings. Earlier notes retain their existing closing.
- Version 0.2.0-test uses the supplied lecture-mode note and the closing
  `With love` then `Your Danny~`, in both locales. Keep its English wording exact.
- Build and verify the signed update APK with the existing signing key and a
  higher versionCode. Preserve previous distribution packages and app data.
