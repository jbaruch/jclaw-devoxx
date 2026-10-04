#!/usr/bin/env python3
"""Read-only readiness check after Port setup; never trigger a workflow or send a mock decline."""
import argparse
import json
import pathlib
import urllib.request
from prepare import PORT, fixture, validate, build_agents
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
    for expected in build_agents(config):
        agent=api.request('GET','/blueprints/_ai_agent/entities/'+expected['identifier'])['entity']
        for key in ('status','prompt','tools','provider','model'):
            assert agent['properties'].get(key)==expected['properties'].get(key),expected['identifier']+'.'+key+' differs from prepared configuration'
        servers=[value['identifier'] if isinstance(value,dict) else value for value in agent['relations']['mcp_servers']]
        assert servers==expected['relations']['mcp_servers']
    print('Four declared agents match the chat entry point, workflow roles, prompts, tool access and configured models')
    wanted={'jclaw-read_getCalendar','jclaw-read_getOrganizerSensitivity'}
    connector=api.request('GET','/blueprints/_mcp_server/entities/jclaw-read')['entity']['properties']
    assert connector['exposed'] is True,'Publish jclaw-read in the target Port instance'
    assert set(connector['allowed_tools'])==wanted,'Published connector allowlist differs from the two read tools'
    # The user/servers endpoint describes the calling identity's connection, not
    # whether a workflow automation identity can use a shared-header connector.
    # Fetch upstream tools through Port, which also exercises its stored headers.
    tools=api.request('GET','/mcp/servers/jclaw-read/tools')['tools']
    names={t['name'] if isinstance(t,dict) else t for t in tools}
    assert names==wanted,'Upstream tool surface differs from the read-only allowlist: '+repr(names)
    print('Published connector reachable through Port with exactly two read tools; native execution requires rehearsal')
    with urllib.request.urlopen(config['bridgeUrl'].rstrip('/')+'/health',timeout=20) as response:
        health = json.load(response)
        assert health['mode']=='MOCK ONLY'
        from prepare import MAX_REFINEMENTS
        assert health['maxRefinements']==MAX_REFINEMENTS,'Bridge and native workflow limits differ; restart the updated bridge'
    assert all(config['models'].values()),'Provider/model configuration is incomplete'
    print('Read-only preflight passed. Rehearse approval, hold, both critics sharing six refinements, exhaustion and failed receipt before the stage run.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--config',type=pathlib.Path,required=True)
    args=parser.parse_args();preflight(json.loads(args.config.read_text()))
