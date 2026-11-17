import copy
import hashlib
import json
import os
from pathlib import Path
import stat
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from model_manifest import extract, load_json, read_glb, validate_manifest, write_atomic

FIXTURE = Path(__file__).resolve().parents[2] / 'src/test/resources/manifest/valid.json'


class ManifestToolTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.glb = self.root / 'models/fixture_001.glb'
        self.glb.parent.mkdir()
        self.doc = load_json(FIXTURE)
        c = self.doc['models'][0]['camera']
        self.gltf = {
            'asset': {'version': '2.0'},
            'extras': {'viewerCamera': dict(version=1, name=c['cameraName'], direction=c['direction'],
                fovDegrees=c['fov'], padding=c['padding'], target=c['target'], bounds=c['bounds'], cameraNode=0)},
            'nodes': [{'camera': 0}],
            'cameras': [{'name': c['cameraName'], 'type': 'perspective', 'perspective': {'yfov': 0.6108652381980153}}],
            'accessors': [{'type': 'SCALAR', 'componentType': 5126, 'count': 2, 'min': [1], 'max': [3]}],
            'animations': [{'name': '대기', 'samplers': [{'input': 0}]}],
            'extensionsRequired': ['EXT_meshopt_compression'],
        }
        self.write_glb()
        self.entry = dict(id='fixture_001', number=1, ko='테스트몬', en='Fixturemon', formCode='00',
            formKind='base', formLabel='일반 모습', category='normal', gender='shared', generation='gen1',
            file='models/fixture_001.glb', status='ok', bytes=999999)
        self.catalog = self.root / 'catalog.json'
        self.write_catalog([self.entry])

    def write_catalog(self, entries):
        self.catalog.write_text(json.dumps(entries))

    def write_glb(self):
        content = json.dumps(self.gltf).encode()
        content += b' ' * (-len(content) % 4)
        self.glb.write_bytes(struct.pack('<4sIII4s', b'glTF', 2, len(content)+20, len(content), b'JSON') + content)

    def extract(self):
        return extract(self.root, self.catalog, 'fixture-v1', 1)

    def test_extracts_actual_size_hash_times_roles_and_original_camera(self):
        result = self.extract()['models'][0]
        self.assertEqual(self.glb.stat().st_size, result['asset']['sizeBytes'])
        self.assertEqual(hashlib.sha256(self.glb.read_bytes()).hexdigest(), result['asset']['sha256'])
        self.assertEqual(2, result['motions'][0]['durationSeconds'])
        self.assertEqual(1, result['motions'][0]['startTimeSeconds'])
        self.assertEqual('idle', result['motions'][0]['role'])
        self.assertEqual(self.doc['models'][0]['camera'], result['camera'])

    def test_repeat_runs_produce_identical_bytes(self):
        out = self.root / 'manifest.json'
        write_atomic(out, self.extract())
        first = out.read_bytes()
        write_atomic(out, self.extract())
        self.assertEqual(first, out.read_bytes())
        self.assertEqual(validate_manifest(load_json(out)), self.extract())

    @unittest.skipUnless(os.name == 'posix', 'requires POSIX file permissions')
    def test_atomic_replacement_preserves_existing_permissions(self):
        out = self.root / 'manifest.json'
        for mode in (0o644, 0o640, 0o600):
            with self.subTest(mode=oct(mode)):
                out.write_text('{}')
                out.chmod(mode)
                write_atomic(out, self.doc)
                self.assertEqual(mode, stat.S_IMODE(out.stat().st_mode))
                self.assertEqual(self.doc, load_json(out))

    @unittest.skipUnless(os.name == 'posix', 'requires POSIX file permissions')
    def test_new_manifest_is_readable_even_with_restrictive_umask(self):
        out = self.root / 'manifest.json'
        previous_umask = os.umask(0o077)
        try:
            write_atomic(out, self.doc)
        finally:
            os.umask(previous_umask)
        self.assertEqual(0o644, stat.S_IMODE(out.stat().st_mode))

    @unittest.skipUnless(os.name == 'posix', 'requires POSIX file permissions')
    def test_permission_failure_preserves_original_and_removes_temporary_file(self):
        out = self.root / 'manifest.json'
        out.write_text('{}')
        out.chmod(0o640)
        before = set(self.root.iterdir())
        with patch('model_manifest.os.fchmod', side_effect=PermissionError('denied')):
            with self.assertRaises(PermissionError):
                write_atomic(out, self.doc)
        self.assertEqual('{}', out.read_text())
        self.assertEqual(0o640, stat.S_IMODE(out.stat().st_mode))
        self.assertEqual(before, set(self.root.iterdir()))

    def test_unknown_motion_is_preserved_and_za_form_has_stable_fallback(self):
        self.gltf['animations'][0]['name'] = 'new animation'
        self.write_glb()
        self.entry['formCode'] = None
        self.write_catalog([self.entry])
        result = self.extract()['models'][0]
        self.assertEqual('other', result['motions'][0]['role'])
        self.assertEqual('fixture_001', result['form']['code'])

    def test_invalid_header_missing_times_and_camera_fail_with_model_context(self):
        self.glb.write_bytes(b'bad')
        with self.assertRaisesRegex(ValueError, 'fixture_001.*header'):
            self.extract()
        self.gltf['accessors'][0].pop('max')
        self.write_glb()
        with self.assertRaisesRegex(ValueError, 'fixture_001'):
            self.extract()
        self.gltf.pop('extras')
        self.write_glb()
        with self.assertRaisesRegex(ValueError, 'fixture_001'):
            self.extract()

    def test_missing_extra_and_duplicate_files_fail(self):
        self.glb.unlink()
        with self.assertRaisesRegex(ValueError, 'file set mismatch'):
            self.extract()
        self.write_glb()
        (self.root / 'models/extra.glb').write_bytes(self.glb.read_bytes())
        with self.assertRaisesRegex(ValueError, 'unlisted'):
            self.extract()
        self.write_catalog([self.entry, self.entry])
        with self.assertRaisesRegex(ValueError, 'duplicate file'):
            extract(self.root, self.catalog, 'v1')

    def test_schema_rejects_duplicates_missing_fields_and_invalid_camera(self):
        doc = copy.deepcopy(self.doc)
        doc['models'].append(copy.deepcopy(doc['models'][0])); doc['modelCount'] = 2
        with self.assertRaisesRegex(ValueError, 'duplicate modelId'):
            validate_manifest(doc)
        doc = copy.deepcopy(self.doc); del doc['models'][0]['shiny']
        with self.assertRaisesRegex(ValueError, 'expected fields'):
            validate_manifest(doc)
        for value in ([0, 0, 0], [0, 1, 0], [float('nan'), 0, 1], [0, 1]):
            doc = copy.deepcopy(self.doc); doc['models'][0]['camera']['direction'] = value
            with self.assertRaises(ValueError):
                validate_manifest(doc)

    def test_changed_binary_is_detected_even_when_metadata_is_same(self):
        before = self.extract()
        data = bytearray(self.glb.read_bytes())
        # JSON whitespace changes keep parsed metadata identical, but change hash.
        index = data.index(b' ', 20)
        data[index] = 9
        self.glb.write_bytes(data)
        after = self.extract()
        self.assertNotEqual(before, after)
        self.assertNotEqual(before['models'][0]['asset']['sha256'], after['models'][0]['asset']['sha256'])


if __name__ == '__main__':
    unittest.main()
