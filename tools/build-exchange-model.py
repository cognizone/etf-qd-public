#!/usr/bin/env python3
"""Use the reviewed ACQF commit's common model, CVs and publisher implementation."""
import argparse
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--common-repo', type=Path, required=True, help='Local ACQF public Git checkout')
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--test', action='store_true', help='Also run standalone SHACL tests with $JENA_HOME')
    args = parser.parse_args()
    revision = json.loads((ROOT / 'publication/validation.json').read_text())['common-revision']
    if not re.fullmatch('[0-9a-f]{40}', revision):
        raise SystemExit('common-revision must be a full reviewed commit SHA')
    # Read immutable Git objects, never the dependency checkout's possibly modified files.
    archive = subprocess.check_output(['git', '-C', str(args.common_repo.resolve()), 'archive', revision,
                                       'docs/model', 'publication', 'tools'])
    with tempfile.TemporaryDirectory(prefix='qcp-common-model-') as directory:
        common = Path(directory)
        with tarfile.open(fileobj=io.BytesIO(archive)) as source:
            source.extractall(common, filter='data')
        command = ['python3', str(common / 'tools/build-exchange-model.py'),
                   '--common-root', str(common), '--product-root', str(ROOT)]
        if args.check:
            command.append('--check')
        subprocess.run(command, check=True)
        if args.test:
            jena = Path(os.environ['JENA_HOME'])
            subprocess.run(['java', '-cp', str(jena / 'lib/*'),
                            str(common / 'tools/ValidateExchangeModel.java'),
                            str(ROOT / 'docs/model/exchange-model.ttl'), 'etf'], check=True)


if __name__ == '__main__':
    main()
