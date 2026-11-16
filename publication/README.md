# ETF dev publication

Maintain `docs/model/etf-profile.ttl` for ETF's bindings. The NQF shape resolves this
repo's existing framework catalogue and retains the official EQF scheme and member IRIs.
The common structural rules and shared CV bindings are maintained in ACQF public.
`publication/validation.json` pins their reviewed commit; the same pin selects the
shared builder and all shared vocabulary sources. Nothing follows ACQF production.

Prerequisites: Python 3.12, Java 21, Apache Jena 5.6.0, and a local clone of
`https://github.com/cognizone/acqf-qcp-public.git` containing the pinned commit.

```
python3 -m pip install -r ../acqf-qcp-public/publication/requirements.txt
./gradlew buildValidation -PcommonRepo=../acqf-qcp-public
./gradlew check -PcommonRepo=../acqf-qcp-public -PjenaHome="$JENA_HOME"
```

The Python wrapper also works directly (no Gradle needed for validation publication):

```
python3 tools/build-exchange-model.py --common-repo ../acqf-qcp-public
python3 tools/build-exchange-model.py --common-repo ../acqf-qcp-public --check --test
```

Commit `docs/model/exchange-model.ttl` with its profile or vocabulary changes.
This generated, complete graph is the ETF validation entrypoint. It contains standard
`sh:in` constraints and the common SHACL-SPARQL constraints; no QCP preprocessing or
network resolution is needed to validate data. Do not edit the generated file directly.
The build rejects missing or empty membership. It reads the pinned dependency's Git
objects, so local modifications to that checkout do not change the result.

GitHub Actions run on dev pushes and PRs targeting dev. CI checks the committed EQF
export against its reviewed original distribution and rebuilds/checks the validation
artifact, then tests all eight EQF levels, invalid values, ACQF-level rejection and the
common duplicate-notation rule with Jena. CI does not commit generated files or deploy
the site. Site publication settings and the main branch are unchanged.

`eqf.properties` identifies the official pinned full SKOS-AP-EU release. `exportEqf`
converts the reviewed RDF/XML snapshot to Turtle losslessly; `refreshEqf` explicitly
downloads that pinned release first. `checkEqf` compares source and public RDF graphs.
The full source distribution remains intact, including its original IRIs and metadata.
The validation export only selects its `skos:inScheme` members for the bound shape.

To adopt a common-model update, fetch/review the ACQF dev commit, change
`common-revision`, regenerate and run `check`. A vocabulary update must also update
QCP's packaged lookups so that UI choices and accepted validation values agree.
