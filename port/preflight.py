#!/usr/bin/env python3
"""Read-only readiness check after Port setup; never trigger a workflow or send a mock decline."""
import argparse
import json
import pathlib
import urllib.request
from prepare import PORT, fixture, validate
from setup import Api, inspect

def preflight(config):
    api=Api(config['apiBase']);org,_=inspect(api)
    assert org==config['expectedOrgId'],'Unexpected target organization'
    events,target,_,seeds=fixture()
    for blueprint,ids in [('jclaw_event',[e['id'] for e in events]),('jclaw_sent_fact',[e['identifier'] for e in seeds]),
        ('skill',['jclaw-corporate-speak','jclaw-decline-review']),('jclaw_profile',['jclaw-baruch'])]:
        for identifier in ids:
            assert api.request('GET','/blueprints/'+blueprint+'/entities/'+identifier),'Missing seeded entity'
        print('Seed records present:',blueprint,len(ids))
    raw=api.request('GET','/workflows/jclaw_decline');workflow=raw.get('workflow',raw)
    validate(workflow);print('Stored native graph passes local action-boundary checks')
    servers=api.request('GET','/mcp/user/servers?includeTools=true')['servers']
    connector=next(s for s in servers if s['identifier']=='jclaw-read')
    assert connector['usable'] is True,'Connect and publish jclaw-read in the target Port instance'
    names={t['name'] if isinstance(t,dict) else t for t in connector['tools']}
    wanted={'jclaw-read_getCalendar','jclaw-read_getOrganizerSensitivity'}
    assert names==wanted,'Connector tool cache differs from the read-only allowlist: '+repr(names)
    print('Port connector connected with exactly two read tools')
    with urllib.request.urlopen(config['bridgeUrl'].rstrip('/')+'/health',timeout=20) as response:
        assert json.load(response)['mode']=='MOCK ONLY'
    assert all(config['models'].values()),'Provider/model configuration is incomplete'
    print('Read-only preflight passed. Rehearse approval, hold, replacement, critic exhaustion and failed receipt before the stage run.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--config',type=pathlib.Path,required=True)
    args=parser.parse_args();preflight(json.loads(args.config.read_text()))
