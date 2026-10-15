"""Offline regressions: every cloud/container command is replaced with a stub."""
import copy
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location('release_manifest', ROOT / 'infra/deploy/release_manifest.py')
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)
COMMIT = 'a' * 40


def manifest():
    images = {name: f'library/{name}@sha256:{"1" * 64}' for name in release.SERVICES}
    for name in ('backend', 'frontend', 'simulator'):
        images[name] = f'123456789012.dkr.ecr.ap-south-1.amazonaws.com/one3d-{name}@sha256:{"1" * 64}'
    return dict(schema_version=1, release='v1.0.0', commit=COMMIT, operations_commit='b' * 40,
                target=dict(account_id='123456789012', region='ap-south-1', instance_id='i-12345678901234567', domain='demo.one3d.store'),
                bundle=dict(bucket='one3d-demo-ops-test', key=f'deploy/{COMMIT}.tar.gz', version_id='exact-version', sha256='2' * 64),
                images=images, database_versions=['1', '2', '3', '4', '5', '6', '7', '8'])


STUB = r'''#!/usr/bin/env python3
import hashlib,json,os,pathlib,subprocess,sys
name=pathlib.Path(sys.argv[0]).name
args=sys.argv[1:]
mode=os.environ.get('TEST_MODE','ok')
log=pathlib.Path(os.environ['TEST_LOG'])
with log.open('a') as out: out.write(json.dumps([name,args])+'\n')
if name=='terraform':
    print({'instance_id':'i-12345678901234567','ops_bucket':'one3d-demo-ops-test'}[args[-1]])
elif name=='gh':
    sys.exit(90)
elif name=='sleep':
    pass
elif name=='seq':
    print('1')
elif name=='aws':
    if args[:2]==['ec2','describe-instances']: print('running')
    elif args[:2]==['ssm','describe-instance-information']: print('Online')
    elif args[:2]==['ssm','send-command']:
        params=json.loads(args[args.index('--parameters')+1])
        pathlib.Path(os.environ['TEST_COMMANDS']).write_text(json.dumps(params))
        if mode=='backup_fail':
            pipeline=next(c for c in params['commands'] if c.startswith('bash -o pipefail'))
            result=subprocess.run(['bash','-c',pipeline],stdout=subprocess.DEVNULL).returncode
            pathlib.Path(os.environ['TEST_STATUS']).write_text('Failed' if result else 'Success')
        print('test-command-id')
    elif args[:2]==['ssm','get-command-invocation']:
        status=pathlib.Path(os.environ['TEST_STATUS'])
        print((status.read_text() if status.exists() else 'Success') if args[args.index('--query')+1]=='Status' else 'stub output')
    elif args[:2]==['ssm','get-parameters-by-path']:
        for k,v in {'one3d-registry':'123456789012.dkr.ecr.ap-south-1.amazonaws.com','one3d-domain':'demo.one3d.store',
                    'one3d-db-password':'synthetic','one3d-oidc-client-secret':'synthetic','razorpay-key-id':'rzp_test_synthetic',
                    'one3d-grafana-admin-password':'synthetic'}.items(): print('/one3d/demo/'+k+'\t'+v)
    elif args[:2]==['ecr','get-login-password']: print('synthetic')
    elif args[:2]==['s3','cp']: sys.stdin.read()
    else: sys.exit(0)
elif name=='curl':
    if mode=='health_fail': sys.exit(22)
    if '-o' in args:
        dest=args[args.index('-o')+1]
        if dest!='/dev/null': pathlib.Path(dest).write_bytes(b'wrong' if mode=='checksum_mismatch' else b'fixture')
    if '-w' in args: print('200')
elif name=='docker':
    if args[0]=='login': sys.stdin.read()
    elif args[0]=='inspect':
        fmt=args[args.index('--format')+1]
        if fmt=='{{.Image}}': print('sha256:existing-db-image')
        elif 'Health' in fmt: print('healthy')
        else: print('exited' if mode=='db_stopped' and args[-1]=='one3d-db-1' else 'running')
    elif args[:2]==['image','inspect']:
        print('sha256:different-db-image' if mode=='db_image_mismatch' else 'sha256:existing-db-image')
    elif args[0]=='compose':
        if 'ps' in args and '-q' in args: print(args[-1])
        elif 'pg_dump' in args:
            if mode=='backup_fail': sys.exit(7)
            print('fixture dump')
        elif 'psql' in args: print('1,2,3,4,5,6,7,8,9' if mode=='schema_mismatch' else '1,2,3,4,5,6,7,8')
    elif 'pg_dump' in args: print('fixture dump')
    elif 'pg_restore' in args:
        sys.stdin.read()
        if mode=='restore_fail': sys.exit(4)
    elif 'psql' in args:
        sql=args[args.index('-c')+1]
        db=args[args.index('-d')+1]
        digest=hashlib.sha256(b'fixture').hexdigest()
        if 'flyway_schema_history' in sql:
            if mode=='history_query_fail': sys.exit(4)
            print('different history' if mode=='history_mismatch' and db!='one3d' else
                  '1,2,3,4,5,6,7,8,9' if mode=='schema_mismatch' else '1,2,3,4,5,6,7,8')
        elif sql.startswith('select count(*)'): print('2' if mode=='count_mismatch' and db!='one3d' else '1')
        elif sql.startswith('select id from customer_order'): print('00000000-0000-0000-0000-000000000001')
        elif sql.startswith('select id, storage_key'): print('artifact-id uploads/model version-id '+digest)
        elif sql.startswith('select storage_key ||'): print('uploads/modelversion-id'+digest)
        elif 'from customer_order o join' in sql: print('different' if mode=='order_mismatch' and db!='one3d' else 'synthetic order')
        else: sys.exit(93)
'''


class OfflineCase(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.work = Path(self.temp.name)
        self.bin = self.work / 'bin'
        self.bin.mkdir()
        for name in ('aws','docker','terraform','gh','curl','sleep','seq'):
            p = self.bin / name
            p.write_text(STUB)
            p.chmod(0o755)
        self.log = self.work / 'commands.jsonl'
        self.env = dict(os.environ, PATH=str(self.bin)+os.pathsep+os.environ['PATH'], TEST_LOG=str(self.log),
                        TEST_COMMANDS=str(self.work/'ssm.json'), TEST_STATUS=str(self.work/'status'), PYTHONDONTWRITEBYTECODE='1',
                        ONE3D_LOCAL_LOCK_DIR=str(self.work/'local-lock'))
        self.path = self.work / 'release.json'
        self.path.write_text(json.dumps(manifest()))

    def run_script(self, script, *args, mode='ok'):
        return subprocess.run(['bash', str(ROOT/script), *map(str,args)], env=dict(self.env, TEST_MODE=mode),
                              cwd=ROOT, text=True, capture_output=True, timeout=20)

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def test_manifest_accepts_digest_pins_and_renders_override(self):
        data = release.load_manifest(self.path)
        self.assertEqual(data['commit'], COMMIT)
        output=self.work/'compose.json'
        result=subprocess.run(['python3',str(ROOT/'infra/deploy/release_manifest.py'),str(self.path),
                               '--approve',COMMIT,'--compose',str(output)],capture_output=True)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(set(json.loads(output.read_text())['services']),release.SERVICES)

    def test_unpinned_or_missing_service_refused_before_cloud_access(self):
        for field in ('backend','db','nginx','prometheus','grafana','certbot','frontend','simulator'):
            data=manifest(); data['images'][field]='library/test:latest'; self.path.write_text(json.dumps(data))
            result=self.run_script('infra/deploy/remote-deploy.sh',self.path,'--approve',COMMIT)
            self.assertNotEqual(result.returncode,0)
        self.assertEqual(self.calls(),[])

    def test_wrong_approval_or_missing_version_refused_before_cloud(self):
        self.assertNotEqual(self.run_script('infra/deploy/remote-deploy.sh',self.path,'--approve','f'*40).returncode,0)
        data=manifest(); data['bundle']['version_id']='null'; self.path.write_text(json.dumps(data))
        self.assertNotEqual(self.run_script('infra/deploy/remote-deploy.sh',self.path,'--approve',COMMIT).returncode,0)
        self.assertEqual(self.calls(),[])

    def test_bundle_hash_and_traversal_rejected(self):
        bundle=self.work/'bundle.tar.gz'
        with tarfile.open(bundle,'w:gz') as archive:
            item=tarfile.TarInfo('../escape'); item.size=1; archive.addfile(item,io.BytesIO(b'x'))
        digest=hashlib.sha256(bundle.read_bytes()).hexdigest()
        with self.assertRaisesRegex(ValueError,'SHA-256'): release.verify_bundle(bundle,'0'*64)
        with self.assertRaisesRegex(ValueError,'Unsafe path'): release.verify_bundle(bundle,digest)

    def test_start_does_not_contact_github_or_select_main(self):
        result=self.run_script('infra/ops/demo.sh','start')
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertFalse(any(name=='gh' for name,_ in self.calls()))
        commands=json.loads((self.work/'ssm.json').read_text())['commands']
        self.assertTrue(any('release_manifest.py' in line for line in commands))
        self.assertTrue(any('deployed-tag' in line for line in commands))
        self.assertFalse(any('main' in line or 'latest' in line for line in commands))

    def test_failed_dump_prevents_instance_shutdown(self):
        result=self.run_script('infra/ops/demo.sh','stop',mode='backup_fail')
        self.assertNotEqual(result.returncode,0)
        self.assertEqual((self.work/'status').read_text(),'Failed')
        self.assertFalse(any(args[:2]==['ec2','stop-instances'] for name,args in self.calls() if name=='aws'))

    def test_replacement_refuses_without_cloud_calls(self):
        result=self.run_script('infra/ops/replace-instance.sh','prepare')
        self.assertEqual(result.returncode,2)
        self.assertEqual(self.calls(),[])

    def test_restore_success_cleans_only_temporary_database(self):
        result=self.run_script('infra/ops/restore-check-host.sh','local','http://mock','fixtures')
        self.assertEqual(result.returncode,0,result.stderr)
        drops=[args for name,args in self.calls() if name=='docker' and 'dropdb' in args]
        self.assertEqual(len(drops),1)
        self.assertTrue(drops[0][-1].startswith('one3d_restore_check_'))

    def test_restore_mismatches_fail_and_cleanup(self):
        for mode in ('count_mismatch','order_mismatch','checksum_mismatch','restore_fail','history_query_fail','history_mismatch'):
            with self.subTest(mode=mode):
                if self.log.exists(): self.log.unlink()
                result=self.run_script('infra/ops/restore-check-host.sh','local','http://mock','fixtures',mode=mode)
                self.assertNotEqual(result.returncode,0,result.stdout)
                drops=[args for name,args in self.calls() if name=='docker' and 'dropdb' in args]
                self.assertEqual(len(drops),1)
                self.assertTrue(drops[0][-1].startswith('one3d_restore_check_'))

    def prepare_host(self):
        host=self.work/'host'; app=host/'app'; app.mkdir(parents=True)
        (host/'region').write_text('ap-south-1')
        (host/'deployed-tag').write_text('c'*40+'\n')
        (app/'release.json').write_text(self.path.read_text())
        for folder in ('infra/deploy','infra/nginx/bootstrap','infra/nginx/https','infra/monitoring'):
            (app/folder).mkdir(parents=True,exist_ok=True)
        (app/'infra/deploy/release_manifest.py').write_text((ROOT/'infra/deploy/release_manifest.py').read_text())
        (app/'infra/deploy/host-lock.sh').write_text((ROOT/'infra/deploy/host-lock.sh').read_text())
        for folder in ('infra/nginx/bootstrap','infra/nginx/https','infra/monitoring'):
            (app/folder/'fixture').write_text('configuration')
        self.env['ONE3D_ROOT']=str(host)
        return host

    def test_failed_health_does_not_advance_release_marker(self):
        host=self.prepare_host()
        result=self.run_script('infra/deploy/deploy.sh',COMMIT,mode='health_fail')
        self.assertNotEqual(result.returncode,0,result.stdout)
        self.assertIn('Release health failed',result.stderr)
        self.assertEqual((host/'deployed-tag').read_text().strip(),'c'*40)
        self.assertFalse((host/'previous-tag').exists())

    def test_successful_health_advances_marker(self):
        host=self.prepare_host()
        result=self.run_script('infra/deploy/deploy.sh',COMMIT)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual((host/'deployed-tag').read_text().strip(),COMMIT)
        self.assertEqual((host/'previous-tag').read_text().strip(),'c'*40)

    def test_schema_mismatch_refuses_before_application_start(self):
        host=self.prepare_host()
        result=self.run_script('infra/deploy/deploy.sh',COMMIT,mode='schema_mismatch')
        self.assertNotEqual(result.returncode,0,result.stdout)
        self.assertEqual((host/'deployed-tag').read_text().strip(),'c'*40)
        self.assertFalse(any(name=='docker' and args[0]=='compose' and 'up' in args for name,args in self.calls()))

    def test_database_digest_change_refused_before_any_recreate(self):
        host=self.prepare_host()
        result=self.run_script('infra/deploy/deploy.sh',COMMIT,mode='db_image_mismatch')
        self.assertNotEqual(result.returncode,0,result.stdout)
        self.assertIn('Database image differs',result.stderr)
        self.assertEqual((host/'deployed-tag').read_text().strip(),'c'*40)
        self.assertFalse(any(name=='docker' and (args[0]=='start' or args[0]=='compose' and 'up' in args) for name,args in self.calls()))

    def test_stopped_database_started_without_recreate_before_history(self):
        self.prepare_host()
        result=self.run_script('infra/deploy/deploy.sh',COMMIT,mode='db_stopped')
        self.assertEqual(result.returncode,0,result.stderr)
        calls=self.calls()
        start=next(i for i,(name,args) in enumerate(calls) if name=='docker' and args[0]=='start')
        history=next(i for i,(name,args) in enumerate(calls) if name=='docker' and any('flyway_schema_history' in a for a in args))
        recreate=next(i for i,(name,args) in enumerate(calls) if name=='docker' and args[0]=='compose' and 'up' in args)
        self.assertLess(start,history)
        self.assertLess(history,recreate)

    def test_shared_lock_rejects_concurrent_operation_and_allows_inherited_lock(self):
        ready=self.work/'locked'
        helper=ROOT/'infra/deploy/host-lock.sh'
        holder_command = """. "$1"; one3d_lock "$2" || exit; bash -c '. "$1"; one3d_lock "$2"' child "$1" "$2" || exit; touch "$3"; python3 -c 'import time; time.sleep(2)'"""
        first=subprocess.Popen(['bash','-c',holder_command,
            'holder',str(helper),str(self.work),str(ready)],env=self.env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        try:
            for _ in range(100):
                if ready.exists() or first.poll() is not None: break
                time.sleep(0.02)
            self.assertTrue(ready.exists(), 'holder failed to acquire/re-enter its inherited lock')
            second=subprocess.run(['bash','-c','. "$1"; one3d_lock "$2"','contender',str(helper),str(self.work)],
                                  env=self.env,text=True,capture_output=True,timeout=5)
            self.assertNotEqual(second.returncode,0)
            self.assertIn('another operation owns the lock',second.stderr)
        finally:
            first.terminate()
            first.communicate(timeout=15)


if __name__=='__main__': unittest.main()
