#!/usr/bin/env python3
"""Extract/verify an external manifest. Python 3.10+, standard library only."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import struct
import sys
import tempfile

SCHEMA_VERSION = 1
ROLES = {'대기': 'idle', '울음': 'cry', '물리공격': 'physical_attack',
         '특수공격': 'special_attack', '메가진화 포즈': 'pose',
         '불꽃 반복': 'effect', '가라르 구름': 'effect'}
GENDERS = {'shared', 'genderless', 'female', 'male'}
FORM_KINDS = {'base', 'other', 'mega', 'regional', 'gigantamax', 'primal', 'eternamax'}
MOTION_ROLES = set(ROLES.values()) | {'other'}


def require(condition, where, message):
    if not condition:
        raise ValueError(f'{where}: {message}')


def load_json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, str(path), f'duplicate JSON key {key}')
            result[key] = value
        return result
    return json.loads(Path(path).read_text(encoding='utf-8'), object_pairs_hook=unique,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError(f'{path}: invalid number {value}')))


def text(value, where):
    require(isinstance(value, str) and bool(value.strip()), where, 'must be a nonblank string')


def number(value, where):
    require(type(value) in (int, float) and math.isfinite(value), where, 'must be a finite number')


def integer(value, where, minimum=0):
    require(type(value) is int and value >= minimum, where, f'must be an integer >= {minimum}')


def vector(value, where):
    require(isinstance(value, list) and len(value) == 3, where, 'must contain exactly 3 numbers')
    for n in value:
        number(n, where)


def keys(value, expected, where):
    require(isinstance(value, dict), where, 'must be an object')
    require(set(value) == set(expected.split()), where,
            f'expected fields [{expected}], got {list(value)}')


def validate_manifest(doc):
    keys(doc, 'schemaVersion datasetVersion modelCount models', 'manifest')
    integer(doc['schemaVersion'], 'schemaVersion', 1)
    require(doc['schemaVersion'] == SCHEMA_VERSION, 'schemaVersion', 'unsupported version')
    text(doc['datasetVersion'], 'datasetVersion')
    integer(doc['modelCount'], 'modelCount', 1)
    require(isinstance(doc['models'], list) and len(doc['models']) == doc['modelCount'], 'modelCount', 'does not match models')
    ids, paths = set(), set()
    for i, m in enumerate(doc['models']):
        w = f'models[{i}]'
        keys(m, 'modelId pokedexNumber nameKo nameEn form shiny gender generation asset motions camera', w)
        for key in ('modelId', 'nameKo', 'nameEn'):
            text(m[key], f'{w}.{key}')
        require(re.fullmatch(r'[A-Za-z0-9_-]+', m['modelId']), w, 'invalid modelId')
        require(m['modelId'] not in ids, w, f'duplicate modelId {m["modelId"]}')
        ids.add(m['modelId'])
        w += f'({m["modelId"]})'
        integer(m['pokedexNumber'], w + '.pokedexNumber', 1)
        integer(m['generation'], w + '.generation', 1)
        require(type(m['shiny']) is bool, w + '.shiny', 'must be boolean')
        require(m['gender'] in GENDERS, w + '.gender', 'unsupported gender')
        keys(m['form'], 'code kind label', w + '.form')
        for key in ('code', 'label'):
            text(m['form'][key], w + '.form.' + key)
        require(m['form']['kind'] in FORM_KINDS, w + '.form.kind', 'unsupported form kind')
        a = m['asset']
        keys(a, 'path sizeBytes sha256 requiredExtensions', w + '.asset')
        text(a['path'], w + '.asset.path')
        parts = a['path'].split('/')
        require(not PurePosixPath(a['path']).is_absolute() and not any(p in ('', '.', '..') for p in parts)
                and not any(c in a['path'] for c in '\\:?%#') and a['path'].endswith('.glb'), w, 'invalid relative GLB path')
        require(a['path'] not in paths, w, 'duplicate asset path')
        paths.add(a['path'])
        integer(a['sizeBytes'], w + '.asset.sizeBytes', 1)
        require(isinstance(a['sha256'], str) and re.fullmatch('[0-9a-f]{64}', a['sha256']), w, 'invalid SHA-256')
        ext = a['requiredExtensions']
        require(isinstance(ext, list), w, 'requiredExtensions must be an array')
        for e in ext:
            text(e, w + '.asset.requiredExtensions')
        require(len(set(ext)) == len(ext), w, 'duplicate extension')
        require(isinstance(m['motions'], list), w, 'motions must be an array')
        for index, motion in enumerate(m['motions']):
            mw = f'{w}.motions[{index}]'
            keys(motion, 'index name role startTimeSeconds endTimeSeconds durationSeconds', mw)
            integer(motion['index'], mw + '.index')
            require(motion['index'] == index, mw, 'index must match GLB animation order')
            text(motion['name'], mw + '.name')
            require(motion['role'] in MOTION_ROLES, mw, 'unsupported role')
            for key in ('startTimeSeconds', 'endTimeSeconds', 'durationSeconds'):
                number(motion[key], mw + '.' + key)
            start, end, duration = (motion[k] for k in ('startTimeSeconds', 'endTimeSeconds', 'durationSeconds'))
            require(0 <= start <= end and duration >= 0 and math.isclose(duration, end-start, rel_tol=0, abs_tol=1e-6), mw, 'invalid time range/duration')
        c = m['camera']
        keys(c, 'cameraName direction fov padding target bounds', w + '.camera')
        text(c['cameraName'], w + '.camera.cameraName')
        for key in ('direction', 'target'):
            vector(c[key], w + '.camera.' + key)
        require(math.isclose(math.hypot(*c['direction']), 1, abs_tol=1e-5), w, 'camera direction must be normalized')
        require(math.hypot(c['direction'][0], c['direction'][2]) > 1e-8, w, 'camera direction cannot be parallel to Y-up')
        number(c['fov'], w + '.camera.fov')
        number(c['padding'], w + '.camera.padding')
        require(0 < c['fov'] < 180 and c['padding'] >= 1, w, 'invalid camera fov/padding')
        keys(c['bounds'], 'min max', w + '.camera.bounds')
        for key in ('min', 'max'):
            vector(c['bounds'][key], w + '.camera.bounds.' + key)
        lo, hi = c['bounds']['min'], c['bounds']['max']
        require(all(a <= b for a, b in zip(lo, hi)) and any(a < b for a, b in zip(lo, hi)), w, 'invalid bounds')
        require(all(a-1e-5 <= t <= b+1e-5 for a, t, b in zip(lo, c['target'], hi)), w, 'camera target outside bounds')
    return doc


def read_glb(path):
    # Read only JSON metadata; hash the entire file incrementally in extract_model.
    with path.open('rb') as f:
        header = f.read(12)
        require(len(header) == 12, str(path), 'truncated GLB header')
        magic, version, length = struct.unpack('<4sII', header)
        require(magic == b'glTF' and version == 2 and length == path.stat().st_size, str(path), 'invalid GLB header/length')
        document = None
        offset = 12
        while offset < length:
            chunk_header = f.read(8)
            require(len(chunk_header) == 8, str(path), 'truncated chunk header')
            size, kind = struct.unpack('<II', chunk_header)
            require(size % 4 == 0 and offset + 8 + size <= length, str(path), 'invalid chunk length')
            if offset == 12:
                require(kind == 0x4E4F534A, str(path), 'first chunk must be JSON')
                document = json.loads(f.read(size))
            else:
                require(kind != 0x4E4F534A, str(path), 'duplicate JSON chunk')
                f.seek(size, 1)
            offset += 8 + size
        require(document is not None and document.get('asset', {}).get('version') == '2.0', str(path), 'missing glTF 2.0 JSON')
        return document


def extract_model(entry, root):
    rel = entry['file']
    path = (root / rel).resolve()
    require(path.is_relative_to(root.resolve()), rel, 'path escapes data root')
    require(path.is_file(), rel, 'GLB file missing')
    require(entry.get('status') == 'ok', rel, 'catalog status must be ok')
    require(entry['category'] in ('normal', 'shiny'), rel, 'unknown category')
    d = read_glb(path)
    c = d['extras']['viewerCamera']
    require(c['version'] == 1, rel, 'unsupported viewerCamera version')
    camera_node = d['nodes'][c['cameraNode']]
    camera = d['cameras'][camera_node['camera']]
    require(camera['name'] == c['name'] and camera['type'] == 'perspective', rel, 'embedded camera mismatch')
    require(math.isclose(math.degrees(camera['perspective']['yfov']), c['fovDegrees'], abs_tol=1e-5), rel, 'camera fov mismatch')
    motions = []
    for index, animation in enumerate(d.get('animations', [])):
        times = []
        for sampler in animation['samplers']:
            accessor = d['accessors'][sampler['input']]
            require(accessor['type'] == 'SCALAR' and accessor['componentType'] == 5126 and accessor['count'] > 0, rel, 'invalid animation time accessor')
            require(len(accessor['min']) == len(accessor['max']) == 1, rel, 'time accessor requires min/max')
            start, end = accessor['min'][0], accessor['max'][0]
            number(start, rel + '.animation.min'); number(end, rel + '.animation.max')
            require(0 <= start <= end, rel, 'invalid animation time range')
            times.append((start, end))
        require(times, rel, 'animation has no samplers')
        start, end = min(t[0] for t in times), max(t[1] for t in times)
        name = animation.get('name') or f'animation_{index}'
        role = animation.get('extras', {}).get('role') or ROLES.get(name, 'other')
        motions.append(dict(index=index, name=name, role=role, startTimeSeconds=start,
                            endTimeSeconds=end, durationSeconds=end-start))
    digest = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            digest.update(chunk)
    require(re.fullmatch(r'gen[1-9][0-9]*', entry['generation']), rel, 'invalid generation')
    return dict(modelId=entry['id'], pokedexNumber=entry['number'], nameKo=entry['ko'], nameEn=entry['en'],
                form=dict(code=entry['formCode'] or entry['id'].removesuffix('_rare'), kind=entry['formKind'], label=entry['formLabel']),
                shiny=entry['category'] == 'shiny', gender=entry['gender'], generation=int(entry['generation'][3:]),
                asset=dict(path=rel, sizeBytes=path.stat().st_size, sha256=digest.hexdigest(),
                           requiredExtensions=sorted(d.get('extensionsRequired', []))), motions=motions,
                camera=dict(cameraName=c['name'], direction=c['direction'], fov=c['fovDegrees'],
                            padding=c['padding'], target=c['target'], bounds=c['bounds']))


def extract(root, catalog, dataset_version, expected_count=None):
    entries = load_json(catalog)
    require(isinstance(entries, list) and entries, str(catalog), 'catalog must be a nonempty array')
    if expected_count is not None:
        require(len(entries) == expected_count, str(catalog), f'expected {expected_count} models, found {len(entries)}')
    listed = [e['file'] for e in entries]
    require(len(set(listed)) == len(listed), str(catalog), 'duplicate file paths')
    actual = {p.relative_to(root).as_posix() for p in (root / 'models').rglob('*.glb')}
    require(set(listed) == actual, str(root), f'file set mismatch: missing={sorted(set(listed)-actual)[:5]}, unlisted={sorted(actual-set(listed))[:5]}')
    models = []
    for e in sorted(entries, key=lambda e: e['id']):
        try:
            models.append(extract_model(e, root))
        except (KeyError, IndexError, TypeError, ValueError, OSError) as exc:
            raise ValueError(f'{e.get("id", "unknown")} ({e.get("file")}): {exc}') from exc
    return validate_manifest(dict(schemaVersion=SCHEMA_VERSION, datasetVersion=dataset_version,
                                  modelCount=len(models), models=models))


def write_atomic(path, doc):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent, delete=False) as f:
            temporary = Path(f.name)
            json.dump(doc, f, ensure_ascii=False, indent=2, allow_nan=False)
            f.write('\n')
            f.flush()
            os.fsync(f.fileno())
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['extract', 'verify'])
    parser.add_argument('--data-root', required=True, type=Path)
    parser.add_argument('--catalog', required=True, type=Path)
    parser.add_argument('--manifest', required=True, type=Path)
    parser.add_argument('--dataset-version', help='required for extract')
    parser.add_argument('--expected-count', type=int)
    args = parser.parse_args()
    try:
        if args.command == 'extract':
            require(bool(args.dataset_version), 'arguments', '--dataset-version is required')
            require(args.manifest.resolve() != args.catalog.resolve() and args.manifest.suffix == '.json', 'manifest', 'output must be a separate JSON file')
            doc = extract(args.data_root, args.catalog, args.dataset_version, args.expected_count)
            write_atomic(args.manifest, doc)
        else:
            doc = validate_manifest(load_json(args.manifest))
            expected = extract(args.data_root, args.catalog, doc['datasetVersion'], args.expected_count)
            require(doc == expected, str(args.manifest), 'manifest differs from current catalog/GLBs; re-extract to a new file and compare')
        print(f'{args.command}: OK, {doc["modelCount"]} models, {sum(len(m["motions"]) for m in doc["models"])} motions, schema={SCHEMA_VERSION}')
    except (ValueError, KeyError, TypeError, OSError) as exc:
        parser.exit(1, f'manifest error: {exc}\n')


if __name__ == '__main__':
    main()
