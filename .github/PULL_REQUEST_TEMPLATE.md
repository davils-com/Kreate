# Summary

<!-- What changes and why. Link the issue this closes. -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change to the `kreate { }` DSL or a task name
- [ ] Build, CI, or documentation only

## Checklist

- [ ] `./gradlew build` passes locally, including `functionalTest`
- [ ] New or changed behaviour is covered by a test
- [ ] KDoc follows the rules in `.junie/AGENTS.md` (`@param`, `@return`, `@since` on every
      public declaration)
- [ ] `./gradlew apiDump` was run and `kreate-plugin/api/kreate-plugin.api` is committed,
      if the public API changed
- [ ] `CHANGELOG.md` has an entry under the unreleased heading
- [ ] The Writerside documentation under `docs/topics/` matches the new behaviour

## Breaking changes

<!-- Which DSL properties or task names changed, and what a consumer has to do about it.
     Add the migration note to MIGRATION-2.0.md. Delete this section if nothing broke. -->

## Verification

<!-- How you convinced yourself this works. For native or platform-specific changes, say
     which operating systems you actually ran it on — the CI matrix covers Linux, macOS and
     Windows, and native defects rarely show up on all three. -->
