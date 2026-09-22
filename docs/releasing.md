# Releasing

kojev is published to Maven Central **from CI only**. A pushed `v*` tag runs
`.github/workflows/release.yml`, which builds and tests every target, uploads the signed
artifacts to the Central Portal, and stops. Releasing the deployment is a manual click in the
Portal, so there is always a chance to look at what is about to become public and permanent.

## One-time setup (human)

These are done by a maintainer, once. None of them can be done by an agent.

1. **Central Portal account and namespace.** Sign in at <https://central.sonatype.com/> with
   GitHub. The namespace `io.github.itisnomatter` must be verified for the account; for
   `io.github.<username>` namespaces the Portal verifies GitHub ownership. Check it shows as
   verified under *Namespaces* before the first release.
2. **User token.** Portal → account → *Generate User Token*. This gives a username and a
   password that are *not* the account login. They go into the repository secrets
   `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`.
3. **GPG signing key.**
   ```sh
   gpg --full-generate-key                      # RSA 4096 or Ed25519; a real email; an expiry
   gpg --list-secret-keys --keyid-format long   # note the key id (the part after rsa4096/ or ed25519/)
   ```
4. **Publish the public key to key servers - Central checks signatures against these.**
   A signature whose public key can't be fetched fails validation in the Portal.
   ```sh
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
   ```
   `keys.openpgp.org` emails you to verify the address before it serves the identity; complete
   that. Confirm with `gpg --keyserver keyserver.ubuntu.com --recv-keys <KEY_ID>` from another
   machine or a fresh `GNUPGHOME`.
5. **Repository secrets.** Set from the maintainer's machine, never pasted into a chat or an
   agent session:
   ```sh
   gpg --armor --export-secret-keys <KEY_ID> | gh secret set SIGNING_KEY
   gh secret set SIGNING_KEY_PASSWORD          # the key's passphrase
   gh secret set MAVEN_CENTRAL_USERNAME        # from step 2
   gh secret set MAVEN_CENTRAL_PASSWORD        # from step 2
   ```
   The workflow maps these to the Gradle properties the publishing plugin reads
   (`ORG_GRADLE_PROJECT_signingInMemoryKey` and friends).

## Every release

1. Make sure `main` is what you want to ship and CI is green on it.
2. **Bump the version** in `build.gradle.kts` (`version = "X.Y.Z"`) and move `CHANGELOG.md`'s
   `Unreleased` entries under a new `## [X.Y.Z] - YYYY-MM-DD` heading. Open a PR; merge it.
   The release workflow refuses a tag that doesn't match `version`, so this step cannot be
   skipped.
3. With `TYPESAFE_API_KEY` set, run the live tests against the real API one more time:
   `./gradlew jvmLiveTest` (it always re-runs).
4. **Tag and push** - this is the trigger:
   ```sh
   git switch main && git pull
   git tag -a vX.Y.Z -m "kojev X.Y.Z"
   git push origin vX.Y.Z
   ```
5. Watch the *Release* workflow. It builds, tests, and uploads. If it fails, nothing is
   public; fix and re-tag (delete the tag first: `git push origin :vX.Y.Z`).
6. In the Central Portal → *Deployments*, the deployment appears as *Validated*. Inspect it
   (artifact list, POM), then **Publish**. Publishing is permanent: Sonatype does not remove
   released artifacts. Propagation to `repo1.maven.org` takes minutes; search indexing longer.
7. Create the GitHub Release for the tag, with the `CHANGELOG.md` section as the body.
8. Bump `version` to the next `-SNAPSHOT` (e.g. `X.Y+1.0-SNAPSHOT`) in a follow-up PR so `main`
   never claims to be a released version.

## If a release goes wrong

- Upload failed in CI: nothing is public. Fix, delete the tag, re-tag.
- Validation failed in the Portal (missing signature, unresolvable public key, POM problem):
  drop the deployment in the Portal, fix, delete the tag, re-tag.
- Already published and broken: you cannot unpublish. Release a fixed `X.Y.Z+1` and note the
  bad version in `CHANGELOG.md`.
