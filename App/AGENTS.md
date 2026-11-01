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
- Build and verify the signed update APK with the existing signing key and a
  higher versionCode. Preserve previous distribution packages and app data.
