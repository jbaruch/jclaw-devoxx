#!/usr/bin/env python3
"""Prepare by default. --inspect reads the chosen Port org; --apply seeds it after identity/config checks."""
import argparse
import json
import os
import pathlib
import ssl
import urllib.error
import urllib.parse
import urllib.request
from prepare import PORT, prepare

class Api:
    def __init__(self,base):
        self.base=base.rstrip('/');self.token=None
        parsed=urllib.parse.urlparse(self.base)
        assert parsed.scheme=='https' and parsed.hostname in ('api.port.io','api.us.port.io','api.getport.io') and parsed.path=='/v1'
        assert not parsed.username and not parsed.password
        body={'clientId':os.environ['PORT_CLIENT_ID'],'clientSecret':os.environ['PORT_CLIENT_SECRET']}
        self.token=self.request('POST','/auth/access_token',body)['accessToken']
    def request(self,method,path,body=None,allow_missing=False):
        headers={'Content-Type':'application/json'}
        if self.token:headers['Authorization']='Bearer '+self.token
        req=urllib.request.Request(self.base+path,data=json.dumps(body).encode() if body is not None else None,
            headers=headers,method=method)
        try:
            with urllib.request.urlopen(req,timeout=45) as response:
                data=json.load(response)
                if data.get('errors'):raise RuntimeError('Port rejected entities: '+json.dumps(data['errors']))
                return data
        except urllib.error.HTTPError as error:
            if allow_missing and error.code==404:return None
            # Never emit authorization headers or the submitted secret body.
            raise RuntimeError(f'{method} {path} failed: HTTP {error.code}') from None

def inspect(api):
    raw=api.request('GET','/organization')
    org=raw.get('organization',raw)
    identifier=org.get('id') or org.get('identifier')
    print('Port organization:',org.get('name'),'identifier:',identifier)
    providers=api.request('GET','/llm-providers')
    # Provider configurations can include secret or deployment fields; show identities only.
    print('Read configured LLM providers; review their model IDs in the target console.')
    return identifier,providers

def apply(api,config,folder):
    expected=config.get('expectedOrgId');assert expected,'Set expectedOrgId from --inspect before seeding'
    actual,_=inspect(api);assert actual==expected,'Credentials point to a different organization'
    assert not config['bridgeUrl'].endswith('.invalid') and '.invalid' not in urllib.parse.urlparse(config['bridgeUrl']).hostname
    assert urllib.parse.urlparse(config['bridgeUrl']).scheme=='https'
    assert config['approverEmail'] and not config['approverEmail'].endswith('.test')
    assert all(config['models'].values()),'Configure actual provider/model pairs for entry, identify, draft and judge'
    read=os.environ['JCLAW_PORT_READ_TOKEN'];action=os.environ['JCLAW_PORT_ACTION_TOKEN']
    assert min(len(read),len(action))>=32 and read!=action
    # Check the bridge before any Port writes. Secrets remain in request headers/body, never output.
    with urllib.request.urlopen(config['bridgeUrl'].rstrip('/')+'/health',timeout=20) as r:
        assert json.load(r)['mode']=='MOCK ONLY'
    for blueprint in json.loads((folder/'blueprints.json').read_text()):
        identifier=blueprint['identifier'];existing=api.request('GET','/blueprints/'+identifier,allow_missing=True)
        if not existing:api.request('POST','/blueprints',blueprint)
        else:
            found=existing.get('blueprint',existing)
            for key,wanted in blueprint['schema']['properties'].items():
                present=found['schema']['properties'].get(key)
                assert present and present['type']==wanted['type'],f'{identifier}.{key} schema differs; reconcile it before seeding'
        print('Blueprint ready:',identifier)
    for secret,value in [('JCLAW_PORT_READ_TOKEN',read),('JCLAW_PORT_ACTION_TOKEN',action)]:
        # Creation uses Port's documented secretName/secretValue body. Creation is explicit;
        # existing names are never rotated just because this script is re-run.
        found=api.request('GET','/organization/secrets/'+secret,allow_missing=True)
        if not found:api.request('POST','/organization/secrets',{'secretName':secret,'secretValue':value})
        else:print('Existing secret retained:',secret,'— verify it matches this bridge locally')
    # Register the connector before the Identify agent's relation references it.
    for bundle in [json.loads((folder/'connector.json').read_text())]+json.loads((folder/'entities.json').read_text()):
        identifier=bundle['blueprint']
        api.request('POST','/blueprints/'+identifier+'/entities/bulk?upsert=true&merge=true',{'entities':bundle['entities']})
        print('Entities seeded:',identifier,len(bundle['entities']))
    workflow=json.loads((folder/'workflow.json').read_text());identifier=workflow['identifier']
    found=api.request('GET','/workflows/'+identifier,allow_missing=True)
    api.request('PUT' if found else 'POST','/workflows/'+identifier if found else '/workflows',workflow)
    actual=api.request('GET','/workflows/'+identifier)
    assert actual,'Workflow did not read back'
    print('Workflow exists:',identifier,'— connect/publish the MCP connector and run preflight before claiming live success')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',type=pathlib.Path,default=PORT/'config.example.json')
    choice=parser.add_mutually_exclusive_group();choice.add_argument('--inspect',action='store_true');choice.add_argument('--apply',action='store_true')
    args=parser.parse_args();config=json.loads(args.config.read_text())
    folder=PORT/('generated' if args.inspect or args.apply else 'preview');prepare(config,folder)
    if args.inspect or args.apply:
        api=Api(config['apiBase'])
        if args.inspect:inspect(api)
        else:apply(api,config,folder)
    else:print('Local preview only. No Port connection or writes.')
