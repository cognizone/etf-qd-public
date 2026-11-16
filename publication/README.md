# ETF dev publication

With Java 21 installed, initialize the pinned ACQF dependency and run:

```
git submodule update --init
./gradlew buildValidation
./gradlew check
```

Gradle resolves Jena and JUnit from Maven Central. No Python, separate Jena installation
or external RDF command is required. Both publishers use the Java sources and Gradle
configuration maintained in `common/`, an ACQF public Git submodule. Its gitlink pins
common rules, shared vocabularies, builder and tests together. Changes in ACQF dev or
production cannot change ETF until this pin is explicitly updated.

Maintain `docs/model/etf-profile.ttl` for ETF's NQF bindings. It follows this repo's
catalogue and retains the official EQF IRIs. `publication/validation.json` maps those
IRIs to the existing local documents. Commit the generated complete
`docs/model/exchange-model.ttl` alongside any source changes. Do not edit it directly.
It contains resolved membership constraints and needs no QCP preprocessing.

GitHub Actions run `./gradlew check` on dev pushes and PRs targeting dev. Checks compare
the rebuilt and committed RDF graphs, then exercise all eight EQF levels, invalid
values, ACQF-level rejection and duplicate notation with standard Jena SHACL. The
workflow does not commit files or deploy a site. Main and site publication settings
are unchanged.

`docs/model/eqf-nqf-levels` is the single reviewed, published EQF document, including
all source metadata. A second RDF/XML copy is unnecessary. `publication/eqf.properties`
records the original release URL, format and SHA-256 checksum. To refresh that reviewed
release explicitly:

```
./gradlew refreshEqf
./gradlew buildValidation check
```

Refresh checks the source checksum, converts the entire graph and verifies RDF graph
equivalence before replacing the public Turtle. An unreviewed upstream change fails
without changing the published file. Downloaded/conversion intermediates belong in
ignored `build/`, not `docs/`. Routine checks are offline after Gradle dependencies
have been cached. To adopt another release, review its source and update the recipe.

Repository artifacts have distinct roles:

| Artifact | Purpose |
| --- | --- |
| `docs/CNAME`, `docs/index.html` | Existing site hostname and landing page |
| `docs/model/internal/virtualspace-nqfs` | Framework catalogue used by QCP and the profile |
| `docs/model/eqf-nqf-levels` | Reviewed full EQF vocabulary and publication input |
| `docs/model/etf-profile.ttl` | Maintained ETF-specific validation policy |
| `docs/model/exchange-model.ttl` | Generated standalone validation entrypoint |
| `common`, `.gitmodules` | Standard Git pin for the shared implementation and rules |
| `publication/validation.json`, `publication/eqf.properties` | Bindings and upstream provenance |
| Gradle build, wrapper, workflow | Reproducible JVM build and automated checks |

To adopt common changes, fetch and check out a reviewed ACQF commit inside `common/`,
regenerate and run `check`, then commit the updated gitlink and export in ETF. Update
QCP's packaged lookups whenever accepted vocabulary membership changes.
